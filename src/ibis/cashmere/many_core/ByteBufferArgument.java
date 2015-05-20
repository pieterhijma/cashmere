package ibis.cashmere.many_core;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.jocl.Pointer;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_event;



public class ByteBufferArgument extends Argument {

    protected ByteBuffer buffer;

    // private cl_context context;
    // private cl_command_queue writeQueue;
    private cl_command_queue readQueue;

    // private ArrayList<cl_event> writeBufferEvents;

    public ByteBufferArgument(cl_context context, cl_command_queue
	    writeQueue, cl_command_queue readQueue, ArrayList<cl_event>
	    writeBufferEvents, ByteBuffer b, Direction d, cl_event[] prevWrites) {
	super(d);

	this.buffer = b;
	Pointer bufferPointer = Pointer.to(buffer);

	// this.context = context;
	// this.writeQueue = writeQueue;
	this.readQueue = readQueue;

	// this.writeBufferEvents = writeBufferEvents;

	if (d == Direction.IN || d == Direction.INOUT) {
	    //MCCashmere.stats.special.start();
	    //System.out.printf("creating buffer of %d MB\n",
	    //	    fs.length * Sizeof.cl_float / (1024 * 1024));
	    cl_event event = writeBuffer(context, writeQueue, prevWrites, buffer.capacity(), bufferPointer);
	    writeBufferEvents.add(event);
	    //MCCashmere.stats.special.stop();
	}
	else {
	    createBuffer(context, buffer.capacity(), bufferPointer);
	}
    }


    public void scheduleReads(ArrayList<cl_event> waitListEvents, ArrayList<cl_event>
	    readBufferEvents) {
	if (direction == Direction.OUT || direction == Direction.INOUT) {
	    cl_event event = readBuffer(readQueue, waitListEvents, buffer.capacity(), Pointer.to(buffer));
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
        buffer = null;
    }

    protected void transform() {
    }

    protected void transformBack() {
    }
}
