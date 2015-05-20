package ibis.cashmere.many_core;


import static org.jocl.CL.clEnqueueNDRangeKernel;
import static org.jocl.CL.clSetKernelArg;
import static org.jocl.CL.clWaitForEvents;
import ibis.util.ThreadPool;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_event;
import org.jocl.cl_kernel;
import static org.jocl.CL.CL_PROFILING_COMMAND_END;
import static org.jocl.CL.CL_PROFILING_COMMAND_START;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KernelLaunch {

    static final Logger logger = LoggerFactory.getLogger("ibis.cashmere.many_core");
    
    private ArrayList<Argument> argsToClean;

    private Set<float[]> noCopyFloats;
    private Set<int[]> noCopyInts;
    private Set<ByteBuffer> noCopyBuffers;

    private cl_context context;
    private cl_command_queue writeQueue;
    private cl_command_queue executeQueue;
    private cl_command_queue readQueue;
    private cl_kernel kernel;
    private int nrArgs;
    private boolean launched = false;

    private Device device;
    // private String kernelName;
    private String threadName;
    private String kernelName;

    ArrayList<cl_event> writeBufferEvents;
    ArrayList<cl_event> executeEvents;
    ArrayList<cl_event> readBufferEvents;
    
    private boolean finished = false;

    private cl_event[] prevWrites;

    public KernelLaunch(String kernelName, String threadName, Device device, cl_event[] prevWrites) {

        this.kernelName = kernelName;
	this.argsToClean = new ArrayList<Argument>();
	this.noCopyFloats = new HashSet<float[]>();
	this.noCopyInts = new HashSet<int[]>();
	this.noCopyBuffers = new HashSet<ByteBuffer>();

	this.context = device.context;
	this.writeQueue = device.writeQueue;
	this.executeQueue = device.executeQueue;
	this.readQueue = device.readQueue;

	this.kernel = device.getKernel(kernelName);
	this.nrArgs = 0;

	this.threadName = threadName;
	this.writeBufferEvents = new ArrayList<cl_event>();
	this.executeEvents = new ArrayList<cl_event>();
	this.readBufferEvents = new ArrayList<cl_event>();

	this.device = device;
	// this.kernelName = kernelName;
	this.prevWrites = prevWrites;
    }

    
    public void setArgument(int i, Argument.Direction d) {
	IntArgument arg = new IntArgument(i, d);
	setArgument(Sizeof.cl_int, arg);
	argsToClean.add(arg);
    }
    
    public void setArgument(float f, Argument.Direction d) {
	FloatArgument arg = new FloatArgument(f, d);
	setArgument(Sizeof.cl_float, arg);
	argsToClean.add(arg);
    }


    public void setArgument(float[][] a, Argument.Direction d) { 
	//MCCashmere.stats.hToDTimer.start();
	FloatArray2DArgument arg = new FloatArray2DArgument(context,
		writeQueue, readQueue, writeBufferEvents, a, d, prevWrites);
	setArgument(Sizeof.cl_mem, arg);
	argsToClean.add(arg);
    }


    public void setArgumentNoCopy(float[] a) {
	FloatArrayArgument arg = device.getArgument(a);
	setArgument(Sizeof.cl_mem, arg);

	// make the execute dependent on this copy.
	addWriteEvent(device.getWriteEvent(a));
	noCopyFloats.add(a);
    }


    public void setArgument(float[] a, Argument.Direction d) { 
	//MCCashmere.stats.hToDTimer.start();
	FloatArrayArgument arg = new FloatArrayArgument(context, 
		writeQueue, readQueue, writeBufferEvents, a, d, prevWrites);
	setArgument(Sizeof.cl_mem, arg);
	argsToClean.add(arg);
    }
    

    public void setArgumentNoCopy(ByteBuffer a) {
	ByteBufferArgument arg = device.getArgument(a);
	setArgument(Sizeof.cl_mem, arg);

	// make the execute dependent on this copy.
	addWriteEvent(device.getWriteEvent(a));
	noCopyBuffers.add(a);
    }


    public void setArgument(ByteBuffer a, Argument.Direction d) { 
	//MCCashmere.stats.hToDTimer.start();
	ByteBufferArgument arg = new ByteBufferArgument(context, 
		writeQueue, readQueue, writeBufferEvents, a, d, prevWrites);
	setArgument(Sizeof.cl_mem, arg);
	argsToClean.add(arg);
    }


    public void setArgumentNoCopy(int[] a) {
	IntArrayArgument arg = device.getArgument(a);
	setArgument(Sizeof.cl_mem, arg);
	// make the execute dependent on this copy.
	addWriteEvent(device.getWriteEvent(a));
	noCopyInts.add(a);
    }


    public void setArgument(int[] a, Argument.Direction d) { 
	//MCCashmere.stats.hToDTimer.start();
	IntArrayArgument arg = new IntArrayArgument(context, 
		writeQueue, readQueue, writeBufferEvents, a, d, prevWrites);
	setArgument(Sizeof.cl_mem, arg);
	argsToClean.add(arg);
    }


    public void setArgument(byte[] a, Argument.Direction d) { 
	//MCCashmere.stats.hToDTimer.start();
	ByteArrayArgument arg = new ByteArrayArgument(context, 
		writeQueue, readQueue, writeBufferEvents, a, d, prevWrites);
	setArgument(Sizeof.cl_mem, arg);
	argsToClean.add(arg);
    }


    public void launch(int gridX, int gridY, int gridZ,
	    int blockX, int blockY, int blockZ) {
        launch(gridX, gridY, gridZ, blockX, blockY, blockZ, true);
    }

    public void launch(int gridX, int gridY, int gridZ,
	    int blockX, int blockY, int blockZ, boolean synchronous) {
	
	long global_work_size[] = new long[] {gridX, gridY, gridZ};
	long local_work_size[] = new long[] {blockX, blockY, blockZ};
	
	device.launched();
	//MCCashmere.stats.kernelTimer.start();
	cl_event event = new cl_event();
	final cl_event[] wbeArray = writeBufferEvents.toArray(new cl_event[writeBufferEvents.size()]);
	device.releaseForWrites(wbeArray);
        if (logger.isTraceEnabled()) {
            logger.debug("Launch: events to wait for: " + Arrays.toString(wbeArray));
            ThreadPool.createNew(new Thread() {
        	public void run() {
        	    clWaitForEvents(wbeArray.length, wbeArray);
        	    System.out.println("Test wait successful: " + Arrays.toString(wbeArray));
        	}
            }, "test event waiter");
        }
        clEnqueueNDRangeKernel(executeQueue, kernel, 3, null, global_work_size,
        	local_work_size, wbeArray.length,
        	wbeArray, event);
        // System.out.println("Kernel launch event: " + event);

	//MCCashmere.stats.hToDTimer.stop();
	executeEvents.add(event);
	registerExecuteEventToDevice(event);
	//cl_event[] rbeArray = new cl_event[readBufferEvents.size()];
	//clWaitForEvents(readBufferEvents.size(),
	//	readBufferEvents.toArray(rbeArray));
	//MCCashmere.stats.dToHTimer.stop();
	
	//MCCashmere.writeBufferEvents.addAll(writeBufferEvents);
	//MCCashmere.executeEvents.addAll(executeEvents);
	//MCCashmere.readBufferEvents.addAll(readBufferEvents);
	launched = true;
        if (synchronous) {
            finish();
        }
    }
    
    public boolean isLaunched() {
	return launched;
    }


    public String getDeviceName() {
	return device.getName();
    }


    String getThread() {
	return threadName;
    }


    private void registerExecuteEventToDevice(cl_event event) {
	for (float[] fs : noCopyFloats) {
	    device.addEvent(fs, event);
	}
	for (int[] is : noCopyInts) {
	    device.addEvent(is, event);
	}
	for (ByteBuffer bs : noCopyBuffers) {
	    device.addEvent(bs, event);
	}
	device.registerEvent(event);
    }


    private void setArgument(int size, Argument arg) {
	if (logger.isDebugEnabled()) {
	    logger.debug("setArgument: size = " + size + ", getPointer(): " + arg.getPointer());
	}
	clSetKernelArg(kernel, nrArgs, size, arg.getPointer());
	nrArgs++;
    }


    public void finish() {
        if (! finished) {
            if (executeEvents.size() != 0) {
                cl_event[] exevnts = executeEvents.toArray(new cl_event[executeEvents.size()]);
                clWaitForEvents(exevnts.length, exevnts);
                long start = MCCashmere.getValue(exevnts[0], CL_PROFILING_COMMAND_START);
                long end = MCCashmere.getValue(exevnts[0], CL_PROFILING_COMMAND_END);
                if (start != 0 && end > start) {
                    // Sometimes, end == 0 or start == 0. Don't know why.
                    double time = (end - start)/1e9;
                    MCCashmere.addTime(kernelName, device, time);
                }
                if (prevWrites != null) {
                    for (cl_event e : prevWrites) {
                	device.deleteEvent(e);
                    }
                }

                for (cl_event e : executeEvents) {
                    device.deleteEvent(e);
        	}
                
                for (cl_event e : writeBufferEvents) {
                    device.deleteEvent(e);
        	}

                for (Argument a : argsToClean) {
                    a.scheduleReads(null, readBufferEvents);
                    a.clean();
                }
                finished = true;
                device.setNotBusy();
            } else {
                throw new Error("launch not called yet");
            }
            if (readBufferEvents.size() != 0) {
        	for (cl_event e : readBufferEvents) {
        	    MCCashmere.releaseEvent(e);
        	}
            }
        }
    }

    public boolean isFinished() {
	return finished;
    }

    private void addWriteEvent(cl_event event) {
	if (event != null) {
	    writeBufferEvents.add(event);
	    device.registerEvent(event);
	}
    }
}
