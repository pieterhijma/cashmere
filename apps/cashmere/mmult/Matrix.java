import java.nio.ByteBuffer;
import java.util.List;

/* $Id: Matrix.java 2844 2004-11-24 10:52:27Z ceriel $ */
//
// Class Matrix
//
// recursive part of quad tree
//
final class Matrix extends Leaf implements java.io.Serializable {
    Matrix _00, _01, _10, _11;

    public void getByteBuffers(List<ByteBuffer> byteBuffers) {
	if (_00 == null) {
	    super.getByteBuffers(byteBuffers);
	    return;
	}
	_00.getByteBuffers(byteBuffers);
	_01.getByteBuffers(byteBuffers);
	_10.getByteBuffers(byteBuffers);
	_11.getByteBuffers(byteBuffers);
    }

    public void setByteBuffers(List<ByteBuffer> byteBuffers) {
	if (_00 == null) {
	    super.setByteBuffers(byteBuffers);
	    return;
	}
	_00.setByteBuffers(byteBuffers);
	_01.setByteBuffers(byteBuffers);
	_10.setByteBuffers(byteBuffers);
	_11.setByteBuffers(byteBuffers);
    }

    public void releaseByteBuffers() {
	if (_00 == null) {
	    super.releaseByteBuffers();
	    return;
	}
	_00.releaseByteBuffers();
	_01.releaseByteBuffers();
	_10.releaseByteBuffers();
	_11.releaseByteBuffers();
    }

    public Matrix(int task, int rec, int loop, float dbl, boolean flipped) {
	super(task, rec, loop, dbl, flipped); // construct Leaf

	if (task + rec <= 0)
	    return;

	// Quad tree is recursive for both the task and the serial
	// recursion levels 

	if (task > 0) {
	    task--;
	} else {
	    rec--;
	}

	_00 = new Matrix(task, rec, loop, dbl, flipped);
	_01 = new Matrix(task, rec, loop, (flipped ? -dbl : dbl), flipped);
	_10 = new Matrix(task, rec, loop, dbl, flipped);
	_11 = new Matrix(task, rec, loop, (flipped ? -dbl : dbl), flipped);
    }

    public float sum(int task, int rec) {
	float s = 0.0f;
	if (task + rec > 0) {
	    if (task > 0) {
		task--;
	    } else {
		rec--;
	    }

	    s += _00.sum(task, rec);
	    s += _01.sum(task, rec);
	    s += _10.sum(task, rec);
	    s += _11.sum(task, rec);
	} else {
	    // task + rec == 0 here
	    s = super.sum();
	}
	return s;
    }

    public void print(int task, int rec) {
	if (task + rec > 0) {
	    if (task > 0) {
		task--;
	    } else {
		rec--;
	    }

	    _00.print(task, rec);
	    _01.print(task, rec);
	    _10.print(task, rec);
	    _11.print(task, rec);
	} else {
	    super.print();
	}
    }

    public boolean check(int task, int rec, float result) {
	boolean ok = true;

	if (task + rec > 0) {
	    if (task > 0) {
		task--;
	    } else {
		rec--;
	    }

	    ok &= _00.check(task, rec, result);
	    ok &= _01.check(task, rec, result);
	    ok &= _10.check(task, rec, result);
	    ok &= _11.check(task, rec, result);
	} else {
	    ok &= super.check(result);
	}

	return ok;
    }

    public void matMul(Matrix a, Matrix b) {
	    // pass Matrices as local variables 
	    // loopMatMul(a, b);
	    //multiplyStride2(a, b);
        multiplyMCL(a, b);
    }


}
