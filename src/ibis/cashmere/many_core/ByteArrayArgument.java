package ibis.cashmere.many_core;


import java.util.ArrayList;

import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_event;


public class ByteArrayArgument extends Argument {

    protected byte[] bs;

    // private cl_context context;
    // private cl_command_queue writeQueue;
    private cl_command_queue readQueue;

    // private ArrayList<cl_event> writeBufferEvents;

    public ByteArrayArgument(cl_context context, cl_command_queue
	    writeQueue, cl_command_queue readQueue, ArrayList<cl_event>
	    writeBufferEvents, byte[] bs, Direction d, cl_event[] prevWrites) {
	super(d);

	this.bs = bs;
	transform();
	Pointer bsPointer = Pointer.to(bs);

	// this.context = context;
	// this.writeQueue = writeQueue;
	this.readQueue = readQueue;

	// this.writeBufferEvents = writeBufferEvents;

	if (d == Direction.IN || d == Direction.INOUT) {
	    //MCCashmere.stats.special.start();
	    //System.out.printf("creating buffer of %d MB\n", 
	    //bs.length * Sizeof.cl_char / (1024 * 1024));
	    cl_event event = writeBuffer(context, writeQueue, prevWrites, bs.length *
		    Sizeof.cl_char, bsPointer);
	    writeBufferEvents.add(event);
	    //MCCashmere.stats.special.stop();
	}
	else {
	    createBuffer(context, bs.length *
		    Sizeof.cl_char, bsPointer);
	}
    }


    public void scheduleReads(ArrayList<cl_event> waitListEvents, ArrayList<cl_event>
	    readBufferEvents) {
	if (direction == Direction.OUT || direction == Direction.INOUT) {
	    cl_event event = readBuffer(readQueue, waitListEvents, bs.length *
		    Sizeof.cl_char, Pointer.to(bs));
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
        bs = null;
    }

    protected void transform() {
    }

    protected void transformBack() {
    }
}
