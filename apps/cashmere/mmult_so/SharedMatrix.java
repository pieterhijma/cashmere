import ibis.cashmere.SharedObject;
import ibis.cashmere.many_core.MCCashmere;

/* $Id: SharedMatrix.java 3447 2006-01-24 16:09:16Z rob $ */

final class SharedMatrix extends SharedObject implements SharedMatrixInterface  {
    MatrixSO m;
    boolean initialized = false;

    // write method
    public void init(int task, int rec, int loop, float dbl, boolean flipped) {
        System.out.println("Initializing shared matrix ...");
        m = new MatrixSO(task, rec, loop, dbl, flipped, true);
        System.out.println("Initialized shared matrix.");
        initialized = true;
        synchronized(this) {
            notifyAll();
        }
    }

    public boolean isInitialized() {
        return initialized;
    }
}
