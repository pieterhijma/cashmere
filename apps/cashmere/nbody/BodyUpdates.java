/* $Id: BodyUpdates.java 6128 2007-08-22 09:19:51Z rob $ */

/**
 * Container for collecting accellerations. This container is used for
 * job results, as well as for sending updates in the SO version.
 */
public class BodyUpdates implements java.io.Serializable {
    /** Body number corresponding to the index. */
    protected int[] bodyNumbers;

    /** Current fill index. */
    protected int index;

    /** For combining BodyUpdate containers. */
    protected BodyUpdates[] more;

    /** Acceleration in X direction. */
    private float[] acc_x;

    /** Acceleration in Y direction. */
    private float[] acc_y;

    /** Acceleration in Z direction. */
    private float[] acc_z;

    /**
     * Constructor.
     * @param sz the initial size of the accelleration arrays.
     */
    public BodyUpdates(int sz) {
        bodyNumbers = new int[sz];
        acc_x = new float[sz];
        acc_y = new float[sz];
        acc_z = new float[sz];
        index = 0;
    }

    /**
     * Grow (or shrink) to the specified size.
     * @param newsz the size to grow or shrink to.
     */
    public final void grow(int newsz) {
        if (newsz != bodyNumbers.length) {
            int[] newnums = new int[newsz];
            System.arraycopy(bodyNumbers, 0, newnums, 0, index);
            bodyNumbers = newnums;
            float[] newacc = new float[newsz];
            System.arraycopy(acc_x, 0, newacc, 0, index);
            acc_x = newacc;
            newacc = new float[newsz];
            System.arraycopy(acc_y, 0, newacc, 0, index);
            acc_y = newacc;
            newacc = new float[newsz];
            System.arraycopy(acc_z, 0, newacc, 0, index);
            acc_z = newacc;
        }
    }

    /**
     * Adds the specified accelerations for the specified body number.
     * @param bodyno the body number.
     * @param x the acceleration in the X direction.
     * @param y the acceleration in the Y direction.
     * @param z the acceleration in the Z direction.
     */
    public final void addAccels(int bodyno, float x, float y, float z) {
        if (index >= bodyNumbers.length) {
            System.out.println("Should not happen 1");
            grow(2 * index + 1);
        }
        bodyNumbers[index] = bodyno;
        acc_x[index] = x;
        acc_y[index] = y;
        acc_z[index] = z;
        index++;
    }
    
    public final void addAccels(int start, int len, float[] accels) {
	int k = 0;
	for (int i = 0; i < len; i++) {
	    bodyNumbers[index] = start+i;
	    acc_x[index] = accels[k];
	    acc_y[index] = accels[k+1];
	    acc_z[index] = accels[k+2];
	    index++;
	    k += 3;
	}
    }

    /**
     * Computes the number of bodies for which this BodyUpdates structure
     * (and its nested structures) has updates.
     * @return the number of updates.
     */
    protected final int computeSize() {
        int size = index;
        if (more != null) {
            for (int i = 0; i < more.length; i++) {
                size += more[i].computeSize();
            }
        }
        return size;
    }

    /**
     * Optimizes this BodyUpdates structure for sending, by collecting
     * its nested structures into the current one.
     */
    protected final void optimizeAndTrim() {
        if (bodyNumbers != null) {
            if (more != null || index < bodyNumbers.length) {
                int newsz = computeSize();
                grow(newsz);
                if (more != null) {
                    for (int i = 0; i < more.length; i++) {
                        optimize(more[i]);
                    }
                    more = null;
                }
            }
        }
    }

    /**
     * Collects the specified updates into the current one.
     * @param b the updates to be added.
     */
    protected final void optimize(BodyUpdates b) {
        System.arraycopy(b.bodyNumbers, 0, bodyNumbers, index, b.index);
        System.arraycopy(b.acc_x, 0, acc_x, index, b.index);
        System.arraycopy(b.acc_y, 0, acc_y, index, b.index);
        System.arraycopy(b.acc_z, 0, acc_z, index, b.index);
        index += b.index;
        if (b.more != null) {
            for (int i = 0; i < b.more.length; i++) {
                optimize(b.more[i]);
            }
        }
    }

    /**
     * Combines the specified updates into this one and returns the result.
     * Assumes that the current BodyUpdates has no <code>more</code> array
     * yet.
     * @param v the updates to combine into this one.
     * @return the result.
     */
    public BodyUpdates combineResults(BodyUpdates[] v) {
        if (more != null) {
            throw new Error("Oops: something wrong here.");
        }
        more = v;
        return this;
    }

    /**
     * Adds the specified updates, preparing for an update round.
     * @param r the specified updates.
     */
    private void addUpdates(BodyUpdates r) {
        for (int i = 0; i < r.index; i++) {
            int ix = r.bodyNumbers[i];
            acc_x[ix] = r.acc_x[i];
            acc_y[ix] = r.acc_y[i];
            acc_z[ix] = r.acc_z[i];
        }
        if (r.more != null) {
            for (int i = 0; i < r.more.length; i++) {
                addUpdates(r.more[i]);
            }
            r.more = null;
        }
    }

    /**
     * Prepares for an update round. It changes the order in the acceleration
     * arrays to the body order, and removes the bodyNumbers array, as it is
     * no longer needed, and this saves on serialization and sending time
     * when the BodyUpdate gets broadcasted.
     */
    public final void prepareForUpdate() {
        int sz = computeSize();
        float[] acc_x_tmp = new float[sz];
        float[] acc_y_tmp = new float[sz];
        float[] acc_z_tmp = new float[sz];

        System.out.println("Preparing for update: size = " + sz);

        for (int i = 0; i < index; i++) {
            int ix = bodyNumbers[i];
            acc_x_tmp[ix] = acc_x[i];
            acc_y_tmp[ix] = acc_y[i];
            acc_z_tmp[ix] = acc_z[i];
        }
        bodyNumbers = null;
        acc_x = acc_x_tmp;
        acc_y = acc_y_tmp;
        acc_z = acc_z_tmp;
        if (more != null) {
            for (int i = 0; i < more.length; i++) {
                addUpdates(more[i]);
            }
            more = null;
        }
    }

    /**
     * Applies the updates to the bodies in the specified array.
     * @param bodies the bodies
     * @param iteration the current iteration number
     * @param params the run parameters.
     */
    public final void updateBodies(Bodies bodies, int iteration,
            RunParameters params) {
        for (int i = 0; i < acc_x.length; i++) {
            if (iteration != 0) { // always true, except for first iteration
                bodies.velocity[3*i] += (acc_x[i] - bodies.oldAcc[3*i]) * params.DT_HALF;
                bodies.velocity[3*i+1] += (acc_y[i] - bodies.oldAcc[3*i+1]) * params.DT_HALF;
                bodies.velocity[3*i+2] += (acc_z[i] - bodies.oldAcc[3*i+2]) * params.DT_HALF;
            }

            bodies.position[3*i] += (acc_x[i] * params.DT_HALF + bodies.velocity[3*i]) * params.DT;
            bodies.position[3*i+1] += (acc_y[i] * params.DT_HALF + bodies.velocity[3*i+1]) * params.DT;
            bodies.position[3*i+2] += (acc_z[i] * params.DT_HALF + bodies.velocity[3*i+2]) * params.DT;
            if (i == 0) {
                System.out.println("0: (" + bodies.position[3*i] + ", " + bodies.position[3*i+1] + ", " + bodies.position[3*i+2] + ")");
            }

            bodies.velocity[3*i] += acc_x[i] * params.DT;
            bodies.velocity[3*i+1] += acc_y[i] * params.DT;
            bodies.velocity[3*i+2] += acc_z[i] * params.DT;

            bodies.oldAcc[3*i] = acc_x[i];
            bodies.oldAcc[3*i+1] = acc_y[i];
            bodies.oldAcc[3*i+2] = acc_z[i];
        }
    }
}
