/* $Id: */

import ibis.cashmere.SharedObject;
import ibis.cashmere.many_core.*;

final public class Bodies extends SharedObject implements BodiesInterface, java.io.Serializable {

    private static final long serialVersionUID = 1L;
    
    float[] position;
    float[] mass;
    float[] velocity;
    transient float[] oldAcc;

    private RunParameters params;

    private int iteration = -1;

    public Bodies(float[] position, float[] mass, float[] velocity, float[] oldAcc, RunParameters params) {
        this.position = position;
        this.mass = mass;
        this.velocity = velocity;
        this.oldAcc = oldAcc;
        this.params = params;
    }

    // write method 
    public void updateBodies(BodyUpdates b, int iteration) {
        // when a node joins while node 0 broadcasts the update,
        // and the node obtains an already updated object while it has
        // not received the update yet, when it receives the update, it will
        // update again, which is wrong.
        System.out.println("updateBodies: iteration = " + iteration + ", this.iteration = " + this.iteration);
        if (iteration == this.iteration+1) {
            updateBodiesLocally(b, iteration);
        }
    }

    public void updateBodiesLocally(BodyUpdates b, int iteration) {
        System.out.println("updateBodiesLocally");
        try {
            b.updateBodies(this, iteration, params);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        this.iteration = iteration;
        System.out.println("this.iteration = " + this.iteration);
    }    

    private void readObject(java.io.ObjectInputStream in)
            throws java.io.IOException, ClassNotFoundException {
        
        in.defaultReadObject();
        oldAcc = new float[velocity.length];

        if (iteration >= 0) {
            for (int i = 0; i < oldAcc.length; i++) {
                oldAcc[i] = in.readFloat();
            }
        }
    }

    private void writeObject(java.io.ObjectOutputStream out)
            throws java.io.IOException {
        
        out.defaultWriteObject();
        if (iteration >= 0) {
            for (int i = 0; i < oldAcc.length; i++) {
                out.writeFloat(oldAcc[i]);
            }
        }
    }

    /**
     * @return the iteration
     */
    public int getIteration() {
        return iteration;
    }

    /**
     * @return the params
     */
    public RunParameters getParams() {
        return params;
    }

    public BodyUpdates nbodySequential(int lo, int high, BodyUpdates res) {
        if (params.GPU) {
            return nbodySequentialMCL(lo, high, res);
        }
        return nbodySequentialCPU(lo, high, res);
    }

    private BodyUpdates nbodySequentialCPU(int lo, int high, BodyUpdates res) {
        float acc0, acc1, acc2;
        for (int i = lo; i < high; i++) {
            acc0 = 0.0f; acc1 = 0.0f; acc2 = 0.0f;
            for (int j = 0; j < mass.length; j++) {
                float diff_x = position[3*j] - position[3*i];
                float diff_y = position[3*j+1] - position[3*i+1];
                float diff_z = position[3*j+2] - position[3*i+2];

                float distsq = diff_x * diff_x + diff_y * diff_y + diff_z * diff_z
                            + (float) params.SOFT_SQ;

                float factor = mass[j] / (distsq * (float) Math.sqrt(distsq));

                acc0 += diff_x * factor;
                acc1 += diff_y * factor;
                acc2 += diff_z * factor;
            }
            res.addAccels(i, acc0, acc1, acc2);
        }
        return res;
    }

    private BodyUpdates nbodySequentialMCL(int lo, int high, BodyUpdates res) {
        try {
            Kernel kernel = MCCashmere.getKernel();
            KernelLaunch kernelLaunch = kernel.createLaunch();
            float[] accel = new float[(high - lo) * 3];
            MCL.launchComputeAccelerationKernel(kernelLaunch, lo, high-lo, mass.length, position, true, mass, true, accel, true, (float) params.SOFT_SQ);
            res.addAccels(lo, high-lo, accel);
        } catch (MCCashmereNotAvailable e) {
            System.err.println("GPU failed, falling back to CPU");
            System.err.println("lo = " + lo + ", high = " + high + 
		    ", nBodies = " + mass.length);
            e.printStackTrace(System.err);
            return nbodySequentialCPU(lo, high, res);
        }

        return res;
    }
}
