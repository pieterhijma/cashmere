package ibis.cashmere.many_core;


import static org.jocl.CL.CL_FALSE;
import static org.jocl.CL.CL_MEM_READ_ONLY;
import static org.jocl.CL.CL_MEM_READ_WRITE;
import static org.jocl.CL.CL_MEM_WRITE_ONLY;
import static org.jocl.CL.CL_TRUE;
import static org.jocl.CL.clCreateBuffer;
import static org.jocl.CL.clEnqueueReadBuffer;
import static org.jocl.CL.clEnqueueWriteBuffer;
import static org.jocl.CL.clReleaseMemObject;
import static org.jocl.CL.clWaitForEvents;
import ibis.util.ThreadPool;

import java.util.ArrayList;
import java.util.Arrays;

import org.jocl.Pointer;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_event;
import org.jocl.cl_mem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Argument {

    static final Logger logger = LoggerFactory.getLogger("ibis.cashmere.many_core");

    public static enum Direction { IN, OUT, INOUT };

    protected Pointer pointer;
    protected Direction direction;
    protected cl_mem memObject;
    
    private static cl_event null_event = new cl_event();	// to test events against.

    public Argument(Direction d) {
	this.pointer = null;
	this.direction = d;
    }

    public Argument(Pointer p, Direction d, boolean allocated) {
	this.pointer = p;
	this.direction = d;
    }


    public void scheduleReads(ArrayList<cl_event> a, ArrayList<cl_event> b) {
    }
    
    public void clean() {
	if (memObject != null) {
	    if (logger.isInfoEnabled()) {
		logger.info("Releasing " + memObject);
	    }
	    clReleaseMemObject(memObject);
            memObject = null;
	}
    }


    public Pointer getPointer() {
	return pointer;
    }
    
    public void createBuffer(cl_context context, long size, Pointer hostPtr) {
	// int[] errorCode = new int[1];

	long flags = direction == Direction.IN ? CL_MEM_READ_ONLY :
	    direction == Direction.INOUT ? CL_MEM_READ_WRITE : CL_MEM_WRITE_ONLY;
	try {
	    memObject = clCreateBuffer(context, flags, size, null, null);
	} catch(Throwable e) {
	    memObject = null;
	}
	boolean tryAgain = true;
	if (memObject == null) {
	    do {
		// logger.warn("Oops, waiting for mem");
		tryAgain = Device.getDevice(context).waitForSomeJobToFinish();
		try {
		    memObject = clCreateBuffer(context, flags, size, null, null);
		} catch(Throwable e) {
		    // ignore;
		}
	    } while (memObject == null && tryAgain);
	}

	if (memObject == null) {
	    // logger.error("OOPS!");
	    Device.getDevice(context).releaseForWrites(null);
	    throw new Error("Could not allocate device memory");
	}
	if (logger.isInfoEnabled()) {
	    logger.info("Done allocating memory of size " + size + ", result = " + memObject);
	}
	pointer = Pointer.to(memObject);
    }
    
    public cl_event writeBuffer(cl_context context, cl_command_queue q, final cl_event[] waitEvents, long size, Pointer hostPtr) {
	createBuffer(context, size, hostPtr);
	cl_event event = new cl_event();
	int nEvents = 0;
	if (waitEvents != null) {
	    nEvents = waitEvents.length;
	}
        if (nEvents > 0) {
            if (logger.isTraceEnabled()) {
        	logger.debug("WriteEvent: events to wait for: " + Arrays.toString(waitEvents));
        	ThreadPool.createNew(new Thread() {
        	    public void run() {
        		clWaitForEvents(waitEvents.length, waitEvents);
        		System.out.println("Test wait successful: " + Arrays.toString(waitEvents));
        	    }
        	}, "test event waiter");
            }
        }
        try {
            clEnqueueWriteBuffer(q, memObject, CL_FALSE, 0, size, hostPtr, nEvents, waitEvents, event);
        } catch(Throwable e) {
            boolean tryAgain = true;
            while (tryAgain) {
        	tryAgain = Device.getDevice(context).waitForSomeJobToFinish();
        	try {
        	    clEnqueueWriteBuffer(q, memObject, CL_FALSE, 0, size, hostPtr, nEvents, waitEvents, event);
        	    break;
        	} catch(Throwable ex) {
        	    if (! tryAgain) {
        		logger.info("Releasing for writes");
        		Device.getDevice(context).releaseForWrites(null);
        		throw new Error("got exception", ex);
        	    }
        	}
            }
        }
        logger.info("Done enqueue write event " + event);
        Device.getDevice(context).registerEvent(event);
	return event;
    }
    
    
    public cl_event readBuffer(cl_command_queue q, ArrayList<cl_event> waitEvents, long size, Pointer hostPtr) {
        // Synchronous for now.
	cl_event event = new cl_event();
	cl_event[] events = null;
	int nEvents = 0;
	if (waitEvents != null) {
	    nEvents = waitEvents.size();
	    events = new cl_event[nEvents];
	    events = waitEvents.toArray(events);
	}
	clEnqueueReadBuffer(q, memObject, CL_TRUE, 0, size, hostPtr, nEvents, events, event);
	if (event.equals(null_event)) {
	    // No initialized event returned.
	    return null;
	}
	return event;
    }
}
