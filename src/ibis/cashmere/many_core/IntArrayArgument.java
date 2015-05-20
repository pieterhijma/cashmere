package ibis.cashmere.many_core;

import java.util.ArrayList;

import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_event;



public class IntArrayArgument extends Argument {

    protected int[] is;

    // private cl_context context;
    // private cl_command_queue writeQueue;
    private cl_command_queue readQueue;

    // private ArrayList<cl_event> writeBufferEvents;

    public IntArrayArgument(cl_context context, cl_command_queue
	    writeQueue, cl_command_queue readQueue, ArrayList<cl_event>
	    writeBufferEvents, int[] is, Direction d, cl_event[] prevWrites) {
	super(d);

	this.is = is;
	transform();
	Pointer isPointer = Pointer.to(is);

	// this.context = context;
	// this.writeQueue = writeQueue;
	this.readQueue = readQueue;

	// this.writeBufferEvents = writeBufferEvents;

	if (d == Direction.IN || d == Direction.INOUT) {
	    //MCCashmere.stats.special.start();
	    //System.out.printf("creating buffer of %d MB\n", 
	//	    is.length * Sizeof.cl_int / (1024 * 1024));
	    cl_event event = writeBuffer(context, writeQueue, prevWrites, is.length *
		    Sizeof.cl_int, isPointer);
	    writeBufferEvents.add(event);
	    //MCCashmere.stats.special.stop();
	}
	else {
	    createBuffer(context, is.length *
		    Sizeof.cl_int, isPointer);
	}
    }


    public void scheduleReads(ArrayList<cl_event> waitListEvents, ArrayList<cl_event>
	    readBufferEvents) {
	if (direction == Direction.OUT || direction == Direction.INOUT) {
	    cl_event event = readBuffer(readQueue, waitListEvents, is.length *
		    Sizeof.cl_int, Pointer.to(is));
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
        is = null;
    }

    protected void transform() {
    }

    protected void transformBack() {
    }
}
