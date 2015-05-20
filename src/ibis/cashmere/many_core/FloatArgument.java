package ibis.cashmere.many_core;

import org.jocl.Pointer;

public class FloatArgument extends Argument {

    public FloatArgument(float f, Direction d) {
	super(Pointer.to(new float[] {f}), d, false);
    }
}
