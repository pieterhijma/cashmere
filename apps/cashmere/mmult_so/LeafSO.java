/* $Id: Leaf.java 3447 2006-01-24 16:09:16Z rob $ */

//
// Class Leaf
//
// Quad tree stuff
//


import ibis.cashmere.many_core.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;

class LeafSO implements java.io.Serializable, ZeroCopy {
    private transient ByteBuffer bvalue;
    private float[] value;
    private int size;

    LeafSO() {
    }

    public void getByteBuffers(List<ByteBuffer> byteBuffers) {
        if (bvalue != null) {
            byteBuffers.add(bvalue);
        }
    }

    public void setByteBuffers(List<ByteBuffer> byteBuffers) {
        if (value == null) {
            bvalue = byteBuffers.remove(0);
        }
    }

    public boolean clearByteBuffer(ByteBuffer b) {
        if (bvalue == b) {
            bvalue = null;
            return true;
        }
        return false;
    }

    void allocateLeaf(int size, float v, boolean flipped) {
        if (value == null) {
            this.size = size;
            value = new float[size*size];
            if (v != 0.0) {
                for (int i = 0; i < size; i++) {
                    for (int j = 0; j < size; j++) {
                        value[size*i+j] = v;
                    }
                    if (flipped) {
                        v = -v;
                    }
                }
            }
        }
    }

    void allocateLeafBB(int size, float v, boolean flipped) {
        if (bvalue == null) {
            this.size = size;
            if (v == 0.0) {
                bvalue = MCCashmere.getByteBuffer(size*size*4, true);
                bvalue.position(0);
            } else {
                bvalue = MCCashmere.getByteBuffer(size*size*4, false);
                bvalue.position(0);
                FloatBuffer fb = bvalue.asFloatBuffer();
                for (int i = 0; i < size; i++) {
                    for (int j = 0; j < size; j++) {
                        fb.put(size*i+j, v);
                    }
                    if (flipped) {
                        v = -v;
                    }
                }
            }
        }
    }

    public void print() {
        if (bvalue != null) {
            bvalue.position(0);
            FloatBuffer bf = bvalue.asFloatBuffer();
            value = new float[size*size];
            bf.get(value);
        }
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                System.out.print(value[i*size+j] + " ");
            }
            System.out.println();
        }
        if (bvalue != null) {
            value = null;
        }
    }

    public boolean check(float result) {
        boolean ok = true;

        if (bvalue != null) {
            bvalue.position(0);
            FloatBuffer bf = bvalue.asFloatBuffer();
            value = new float[size*size];
            bf.get(value);
        }

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (value[i*size+j] != result) {
                    System.out.println("ERROR in matrix!, i = " + i + ", j = "
                            + j + " val = " + value[i*size+j]);
                    ok = false;
                }
            }
        }
        if (bvalue != null) {
            value = null;
        }

        return ok;
    }

    public float sum() {
        float s = 0.0f;
        if (bvalue != null) {
            bvalue.position(0);
            FloatBuffer bf = bvalue.asFloatBuffer();
            value = new float[size*size];
            bf.get(value);
        }
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                s += value[i*size+j];
            }
        }
        if (bvalue != null) {
            value = null;
        }
        return s;
    }

    public void loopMatMul(LeafSO a, LeafSO b) {
        if (bvalue != null) {
            bvalue.position(0);
            FloatBuffer bf = bvalue.asFloatBuffer();
            for (int i = 0; i < size; i++) {
        	for (int j = 0; j < size; j++) {
        	    float value = bf.get(i*size+j);
        	    for (int k = 0; k < size; k++) {
        		value += a.value[i*size+k] * b.value[k*size+j];
        	    }
        	    bf.put(i*size+j, value);
        	}
            }
        } else {
            for (int i = 0; i < size; i++) {
        	for (int j = 0; j < size; j++) {
        	    for (int k = 0; k < size; k++) {
        		value[i*size+j] += a.value[i*size+k] * b.value[k*size+j];
        	    }
        	}
            }
        }
    }


    void multiplyMCL(LeafSO a, LeafSO b) {
	try {
	    Kernel kernel = MCCashmere.getKernel();
	    KernelLaunch kernelLaunch = kernel.createLaunch();
	    MCL.launchMatmulKernel(kernelLaunch, size, size, size, bvalue,
		    a.value, b.value);
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
    void multiplyStride2(LeafSO a, LeafSO b) {
        if (bvalue != null) {
            bvalue.position(0);
            FloatBuffer bf = bvalue.asFloatBuffer();
            for (int j = 0; j < size; j += 2) {
        	for (int i = 0; i < size; i += 2) {

        	    float s00 = 0.0F;
        	    float s01 = 0.0F;
        	    float s10 = 0.0F;
        	    float s11 = 0.0F;

        	    for (int k = 0; k < size; k += 2) {

        		s00 += a.value[i*size+k] * b.value[k*size+j];
        		s10 += a.value[(i+1)*size+k] * b.value[k*size+j];
        		s01 += a.value[i*size+k] * b.value[k*size+j + 1];
        		s11 += a.value[(i+1)*size+k] * b.value[k*size+j + 1];

        		s00 += a.value[i*size+k + 1] * b.value[(k+1)*size+j];
        		s10 += a.value[(i+1)*size+k + 1] * b.value[(k+1)*size+j];
        		s01 += a.value[i*size+k + 1] * b.value[(k+1)*size+j + 1];
        		s11 += a.value[(i+1)*size+k + 1] * b.value[(k+1)*size+j + 1];
        	    }

        	    
        	    bf.put(i*size+j, bf.get(i*size+j) + s00);
        	    bf.put(i*size+j+1, bf.get(i*size+j+1) + s01);
        	    bf.put((i+1)*size+j, bf.get((i+1)*size+j) + s10);
        	    bf.put((i+1)*size+j+1, bf.get((i+1)*size+j+1) + s01);
        	}
            }
        } else {
            for (int j = 0; j < size; j += 2) {
        	for (int i = 0; i < size; i += 2) {

        	    float s00 = 0.0F;
        	    float s01 = 0.0F;
        	    float s10 = 0.0F;
        	    float s11 = 0.0F;

        	    for (int k = 0; k < size; k += 2) {

        		s00 += a.value[i*size+k] * b.value[k*size+j];
        		s10 += a.value[(i+1)*size+k] * b.value[k*size+j];
        		s01 += a.value[i*size+k] * b.value[k*size+j + 1];
        		s11 += a.value[(i+1)*size+k] * b.value[k*size+j + 1];

        		s00 += a.value[i*size+k + 1] * b.value[(k+1)*size+j];
        		s10 += a.value[(i+1)*size+k + 1] * b.value[(k+1)*size+j];
        		s01 += a.value[i*size+k + 1] * b.value[(k+1)*size+j + 1];
        		s11 += a.value[(i+1)*size+k + 1] * b.value[(k+1)*size+j + 1];
        	    }

        	    value[i*size+j] += s00;
        	    value[i*size+j + 1] += s01;
        	    value[(i + 1)*size+j] += s10;
        	    value[(i + 1)*size+j + 1] += s11;
        	}
            }
        }
    }
}
