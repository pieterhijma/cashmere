package ibis.cashmere.many_core;


import java.util.ArrayList;

import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_event;



public class FloatArray2DArgument extends FloatArrayArgument {
    //broken

    private float[][] fs2D;

    public FloatArray2DArgument(cl_context context, cl_command_queue
	    writeQueue, cl_command_queue readQueue, ArrayList<cl_event>
	    writeBufferEvents, float[][] fs, Direction d, cl_event[] prevWrites) {
	super(context, writeQueue, readQueue, writeBufferEvents, new
		float[fs.length * fs[0].length], d, prevWrites);
	this.fs2D = fs;
	transform();
    }


    protected void transform() {
	flatten();
    }


    protected void transformBack() {
	unFlatten();
    }

    private void flatten() {
	for (int i = 0; i < fs2D.length; i++) {
	    for (int j = 0; j < fs2D[0].length; j++) {
		fs[i * fs2D[0].length + j] = fs2D[i][j];
	    }
	}
    }


    private void unFlatten() {
	for (int i = 0; i < fs2D.length; i++) {
	    for (int j = 0; j < fs2D[0].length; j++) {
		fs2D[i][j] = fs[i * fs2D[0].length + j] ;
	    }
	}
    }

    public void scheduleReads(ArrayList<cl_event> waitListEvents, ArrayList<cl_event>
                        readBufferEvents) {
        super.scheduleReads(waitListEvents, readBufferEvents);
        fs2D = null;
    }
}
