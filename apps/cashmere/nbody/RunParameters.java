/* $Id: RunParameters.java 6217 2007-09-03 12:49:44Z rob $ */
/**
 * Container class for some parameters that influence the run.
 */
public final class RunParameters implements java.io.Serializable {
    /** Integration time-step. */
    final double DT;

    /** Half of the integration time-step. */
    final double DT_HALF;

    /** Potential softening value. */
    final double SOFT;

    /** Potential softening value squared. */
    final double SOFT_SQ;

    /** Spawn threshold. */
    final int THRESHOLD;
    
    /** GPU factor. */
    final int GPUFACTOR;

    /** the start time of the simulation */
    final double START_TIME;

    /** the end time of the simulation */
    final double END_TIME;

    /** the number of iterations of the simulation */
    final int ITERATIONS;

    final boolean GPU;

    // don't allow uninitialized construction, not used
    private RunParameters() {
        throw new Error("internal error");
    }

    public RunParameters(double dt, double soft,
        int threshold, int gpufactor, double start_time, double end_time, int iterations, boolean gpu) {
        DT = dt;
        DT_HALF = DT / 2.0;
        SOFT = soft;
        SOFT_SQ = SOFT * SOFT;
        THRESHOLD = threshold;
        GPUFACTOR = gpufactor;
        START_TIME = start_time;
        END_TIME = end_time;
        GPU = gpu;
        
        if (iterations == -1) {
            ITERATIONS = (int) ((END_TIME + 0.1 * DT - START_TIME) / DT);
        } else {
            ITERATIONS = iterations;
        }
    }
}
