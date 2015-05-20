package ibis.cashmere.many_core;

import org.jocl.Pointer;

public class IntArgument extends Argument {

    public IntArgument(int i, Direction d) {
	super(Pointer.to(new int[] {i}), d, false);
    }
}
