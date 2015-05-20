package ibis.cashmere.many_core;

import java.util.ArrayList;

import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_event;



public class FloatArrayArgument extends Argument {

    protected float[] fs;

    // private cl_context context;
    // private cl_command_queue writeQueue;
    private cl_command_queue readQueue;

    // private ArrayList<cl_event> writeBufferEvents;

    public FloatArrayArgument(cl_context context, cl_command_queue
	    writeQueue, cl_command_queue readQueue, ArrayList<cl_event>
	    writeBufferEvents, float[] fs, Direction d, cl_event[] prevWrites) {
	super(d);

	this.fs = fs;
	Pointer fsPointer = Pointer.to(fs);

	// this.context = context;
	// this.writeQueue = writeQueue;
	this.readQueue = readQueue;

	// this.writeBufferEvents = writeBufferEvents;

	if (d == Direction.IN || d == Direction.INOUT) {
	    //MCCashmere.stats.special.start();
	    //System.out.printf("creating buffer of %d MB\n",
	    //	    fs.length * Sizeof.cl_float / (1024 * 1024));
	    cl_event event = writeBuffer(context, writeQueue, prevWrites, fs.length *
		    Sizeof.cl_float, fsPointer);
	    writeBufferEvents.add(event);
	    //MCCashmere.stats.special.stop();
	}
	else {
	    createBuffer(context, fs.length *
		    Sizeof.cl_float, fsPointer);
	}
    }


    public void scheduleReads(ArrayList<cl_event> waitListEvents, ArrayList<cl_event>
	    readBufferEvents) {
	if (direction == Direction.OUT || direction == Direction.INOUT) {
	    cl_event event = readBuffer(readQueue, waitListEvents, fs.length *
		    Sizeof.cl_float, Pointer.to(fs));
	    //MCCashmere.stats.kernelTimer.stop();
	    //MCCashmere.stats.dToHTimer.start();
	    /*
	    clSetEventCallback(event, CL_COMPLETE, new EventCallbackFunction() {
		public void function(cl_event event, int commandCallbackType, 
		    Object userdata) {
		    cleanCallback();
		}
	    }, null);
	    */
	    if (event != null) {
		readBufferEvents.add(event);
	    }
	}
	cleanCallback();
    }


    private void cleanCallback() {
	if (direction == Direction.OUT || direction == Direction.INOUT) {
	    transformBack();
	}
	super.clean();
        fs = null;
    }

    protected void transform() {
    }

    protected void transformBack() {
    }
}
