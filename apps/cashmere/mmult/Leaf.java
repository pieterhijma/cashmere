/* $Id: Leaf.java 2844 2004-11-24 10:52:27Z ceriel $ */

//
// Class Leaf
//
// Quad tree stuff
//

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import ibis.cashmere.many_core.*;

import java.util.List;

class Leaf implements java.io.Serializable, ZeroCopy {
    private transient ByteBuffer value;
    int size;

    private Leaf() {
    }

    public void getByteBuffers(List<ByteBuffer> byteBuffers) {
	byteBuffers.add(value);
    }

    public void setByteBuffers(List<ByteBuffer> byteBuffers) {
	value = byteBuffers.remove(0);
    }

    public void releaseByteBuffers() {
        if (value != null) {
            MCCashmere.releaseByteBuffer(value);
            value = null;
        }
    }

    Leaf(int task, int rec, int loop, float v, boolean flipped) {
        if (task + rec == 0) {
            this.size = loop;
            
            value = MCCashmere.getByteBuffer(4 * size * size, v != 0.0).order(ByteOrder.nativeOrder());
	    // value = ByteBuffer.wrap(new byte[4 * size * size]).order(ByteOrder.nativeOrder());
            if (v != 0.0) {
        	FloatBuffer fvalue = value.asFloatBuffer();
        	for (int i = 0; i < loop; i++) {
        	    for (int j = 0; j < loop; j++) {
        		fvalue.put(size*i+j, v);
        	    }
        	    if (flipped) {
        		v = -v;
        	    }
        	}
            }
        }
    }

    public void print() {
	value.position(0);
	FloatBuffer fvalue = value.asFloatBuffer();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                System.out.print(fvalue.get(i*size+j) + " ");
            }
            System.out.println();
        }
    }

    public boolean check(float result) {
        boolean ok = true;

        value.position(0);
	FloatBuffer fvalue = value.asFloatBuffer();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (fvalue.get(i*size+j) != result) {
                    System.out.println("ERROR in matrix!, i = " + i + ", j = "
                            + j + " val = " + fvalue.get(i*size+j));
                    ok = false;
                    break;
                }
            }
            if (! ok) {
                break;
            }
        }

        return ok;
    }

    public float sum() {
	value.position(0);
	FloatBuffer fvalue = value.asFloatBuffer();
        float s = 0.0f;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                s += fvalue.get(i*size+j);
            }
        }
        return s;
    }

    public void loopMatMul(Leaf a, Leaf b) {
	value.position(0);
	a.value.position(0);
	b.value.position(0);
	FloatBuffer fvalue = value.asFloatBuffer();
	FloatBuffer af = a.value.asFloatBuffer();
	FloatBuffer bf = b.value.asFloatBuffer();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                for (int k = 0; k < size; k++) {
                    fvalue.put(i*size+j, fvalue.get(i*size+j) + af.get(i*size+k) * bf.get(k*size+j));
                }
            }
        }
    }

    
    void multiplyMCL(Leaf a, Leaf b) {
	try {
	    Kernel kernel = MCCashmere.getKernel();
	    KernelLaunch kernelLaunch = kernel.createLaunch();

	    // value.position(0);
	    // a.value.position(0);
	    // b.value.position(0);
	    // FloatBuffer valuef = value.asFloatBuffer();
	    // FloatBuffer avaluef = a.value.asFloatBuffer();
	    // FloatBuffer bvaluef = b.value.asFloatBuffer();

	    // float[] fc = new float[size*size];
	    // float[] fa = new float[size*size];
	    // float[] fb = new float[size*size];
	    
	    // valuef.get(fc);
	    // avaluef.get(fa);
	    // bvaluef.get(fb);

	    MCL.launchMatmulKernel(kernelLaunch, size, size, size, value,
		    a.value, b.value);
	    // valuef.position(0);
	    // valuef.put(fc);
	}
	catch (MCCashmereNotAvailable e) {
	    System.err.println("falling back to CPU");
	    System.err.println(e.getMessage());
	    multiplyStride2(a, b);
	}
    }


    /** 
     * Version of matrix multiplication that steps 2 rows and columns
     * at a time. Adapted from Cilk demos.
     * Note that the results are added into C, not just set into C.
     * This works well here because Java array elements
     * are created with all zero values.
     **/
    void multiplyStride2(Leaf a, Leaf b) {
	FloatBuffer fvalue = value.asFloatBuffer();
	FloatBuffer af = a.value.asFloatBuffer();
	FloatBuffer bf = b.value.asFloatBuffer();
        for (int j = 0; j < size; j += 2) {
            for (int i = 0; i < size; i += 2) {

                //float[] a0 = a.value[i];
                //float[] a1 = a.value[i + 1];

                float s00 = 0.0F;
                float s01 = 0.0F;
                float s10 = 0.0F;
                float s11 = 0.0F;

                for (int k = 0; k < size; k += 2) {

                    s00 += af.get(i*size+k) * bf.get(k*size+j);
                    s10 += af.get((i+1)*size+k) * bf.get(k*size+j);
                    s01 += af.get(i*size+k) * bf.get(k*size+j + 1);
                    s11 += af.get((i+1)*size+k) * bf.get(k*size+j + 1);

                    s00 += af.get(i*size+k + 1) * bf.get((k+1)*size+j);
                    s10 += af.get((i+1)*size+k + 1) * bf.get((k+1)*size+j);
                    s01 += af.get(i*size+k + 1) * bf.get((k+1)*size+j + 1);
                    s11 += af.get((i+1)*size+k + 1) * bf.get((k+1)*size+j + 1);
                }

                fvalue.put(i*size+j, fvalue.get(i*size+j) + s00);
                fvalue.put(i*size+j + 1, fvalue.get(i*size+j + 1) + s01);
                fvalue.put((i + 1)*size+j, fvalue.get((i + 1)*size+j) + s10);
                fvalue.put((i + 1)*size+j + 1, fvalue.get((i + 1)*size+j + 1) + s11);
            }
        }
    }
}
