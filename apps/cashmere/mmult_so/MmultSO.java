// Class Mmult
//
// Matrix multiply functionality
// This is the ony really interesting part
// The rest is dead weight

import ibis.cashmere.many_core.*;

import java.io.PrintStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;

final class MmultSO extends ibis.cashmere.CashmereObject implements MmultSOInterface,
        java.io.Serializable {

    byte[] newPos(byte[] srcPos, int direction) {
        byte[] result;
        if(srcPos == null) {
            result = new byte[1];
        } else {
            result = new byte[srcPos.length + 1];
            System.arraycopy(srcPos, 0, result, 0, srcPos.length);
        }
        
        result[result.length-1] = (byte) direction;

        return result;
    }
    
    @Override
    public Integer makeSOInvocationStart(SharedMatrix a) {
        try {
            Thread.sleep(5000);
        } catch(Throwable e) {
            // ignore
        }
        return null;
    }

    public boolean guard_makeSOInvocationStart(SharedMatrix a) {
        return a.isInitialized();
    }
    
    // real  functionality: tasked-mat-mul
    public MatrixSO mult(int task, int rec, SharedMatrix a, byte[] aPos,
	    SharedMatrix b, byte[] bPos, MatrixSO c, boolean gpu) {

        MatrixSO f_00 = null;
        MatrixSO f_01;
        MatrixSO f_10;
        MatrixSO f_11;

        if (task == 0) { 
	    if (rec == 0) {
		// switch to serial recursive part
		// pass instance variables
		// System.out.println("C = " + c);
		// System.out.println("A = " + a);
		// System.out.println("a.m = " + a.m);
		// System.out.println("B = " + b);
		// System.out.println("b.m = " + b.m);
		
		// added this to support input 0 0 2048
		MatrixSO am = aPos == null ? a.m : a.m.getSubMatrix(aPos);
		MatrixSO bm = aPos == null ? b.m : b.m.getSubMatrix(bPos);
		c.matMul(am, bm, gpu);
		return c;
	    }
	    else {
		c.allocateSubs();
		MCCashmere.enableManycore();
		rec--;
	    }
        }
	else if (task > 0) {
	    c.allocateSubs();
	    task--;
	}

        f_00 = /* spawn */mult(task, rec, a, newPos(aPos,00), b, newPos(bPos, 00), c._00, gpu);
        f_01 = /* spawn */mult(task, rec, a, newPos(aPos,00), b, newPos(bPos, 01), c._01, gpu);
        f_10 = /* spawn */mult(task, rec, a, newPos(aPos,10), b, newPos(bPos, 00), c._10, gpu);
        f_11 = /* spawn */mult(task, rec, a, newPos(aPos,10), b, newPos(bPos, 01), c._11, gpu);
        sync();

        // spawn child threads; k = 1; after having synched on
        // get_result() sync on the child threads
        // it's slightly inefficient that 01 will be started after 00, a
        // completely unrelated sub matrix, is ready
        // what we really want is systolicism, where f_01 is started as
        // soon as its data dependency is resolved.  Now, the sematics
        // of join are that it blocks the entire parent thread, instead
        // of just the 00 call. Mentat has the data dependency
        // analysis that we want.

        c._00 = null; c._01 = null; c._10 = null; c._11 = null;
        c._00 = /* spawn */mult(task, rec, a, newPos(aPos,01), b, newPos(bPos, 10), f_00, gpu);
        c._01 = /* spawn */mult(task, rec, a, newPos(aPos,01), b, newPos(bPos, 11), f_01, gpu);
        c._10 = /* spawn */mult(task, rec, a, newPos(aPos,11), b, newPos(bPos, 10), f_10, gpu);
        c._11 = /* spawn */mult(task, rec, a, newPos(aPos,11), b, newPos(bPos, 11), f_11, gpu);
        sync();

        return c;
    }

    public static int power(int base, int exponent) {
        return (int) Math.pow(base, exponent);
    }

    public static void main(String args[]) {
        int task = 2, rec = 2, loop = power(2, 2);
        long start, end;
        double time;
        boolean gpu = true;
        MmultSO m = new MmultSO();
        int nNodes = 0;

        String nt = System.getProperty("ibis.pool.size");
        if (nt != null) {
            nNodes = Integer.parseInt(nt);
        }

        if (args.length >= 3) {
            task = Integer.parseInt(args[0]);
            rec = Integer.parseInt(args[1]);
            loop = Integer.parseInt(args[2]);
            if (args.length == 4) {
        	if (args[3].equals("-cpu")) {
        	    gpu = false;
        	} else if (args[3].equals("-gpu")) {
        	    gpu = true;
        	}
            } else if (args.length != 3) {
                System.out.println("usage: mmult [task rec loop [ -gpu | -cpu]]");
                System.exit(66);
            }
            if(loop%2==1) {
                System.err.println("The loop size must be even");
            }
        } else if (args.length != 0) {
            System.out.println("usage: mmult [task rec loop [ -gpu | -cpu]]");
            System.exit(66);
        }

        int cells = power(2, task + rec) * loop;
        System.out.println("Running Matrix multiply, on a matrix of size "
                + cells + " x " + cells + ", threads = " + power(8, task));

        MCCashmere.initialize(power(4, task + rec), 4 * loop * loop);
        
        MatrixSO c = new MatrixSO(task, rec, loop, 0.0f, false);

        SharedMatrix a = new SharedMatrix();
        a.exportObject();
        SharedMatrix b = new SharedMatrix();
        b.exportObject();

        for (int i = 0; i < nNodes-1; i++) {
            Integer x = m.makeSOInvocationStart(a);
        }

        // Give other nodes time to steal job, so don't sync, otherwise 
        // master would steal these jobs.
        // This makes sure that every node updates the shared object.
        try {
            Thread.sleep(10000);
        } catch(Throwable e) {
            // ignore
        }

        a.init(task, rec, loop, 1.0f, false);

        synchronized(a) {
            while (! a.isInitialized()) {
                try {
                    a.wait();
                } catch(Throwable e) {
                    // ignore
                }
            }
        }

        m.sync();

        for (int i = 0; i < nNodes-1; i++) {
            Integer x = m.makeSOInvocationStart(b);
        }

        try {
            Thread.sleep(10000);
        } catch(Throwable e) {
            // ignore
        }

        b.init(task, rec, loop, 1.0f, false);

        synchronized(b) {
            while (! b.isInitialized()) {
                try {
                    b.wait();
                } catch(Throwable e) {
                    // ignore
                }
            }
        }

        m.sync();
	
        start = System.currentTimeMillis();
        MCTimer timer = MCCashmere.createOverallTimer();
        start = System.currentTimeMillis();
        int eventNo = timer.start();
        c = /* spawn */m.mult(task, rec, a, null, b, null, c, gpu);
        m.sync();
        timer.stop(eventNo);
        end = System.currentTimeMillis();
        time = (double) end - start;
        time /= 1000.0; // seconds.

        System.out.println("checking result, should be " + ((float) cells));
        if (c.check(task, rec, (float) cells)) {
            //    System.out.println("\nC:");
            //    c.print(task, rec, loop);
            System.out.println("application time Mmult (" + task + "," + rec
                    + "," + loop + "," + cells + ") took " + time + " s");
            System.out.printf("application performance: %f GFLOPS\n",
                    2l * cells * cells * cells / 1.0e9 / time);
            System.out.println("application result Mmult (" + task + "," + rec
                    + "," + loop + "," + cells + ") = OK");
            System.out.println("Test succeeded!");

	    String fileName = String.format("results_%dx%d.data", cells, cells);
	    try {
		PrintStream outputFile = new PrintStream(new
			FileOutputStream(fileName, true));
		outputFile.printf("%d,%d,%d,%d %f\n", task, rec, loop, loop,
			time);
		outputFile.close();
	    }
	    catch (FileNotFoundException e) {
		e.printStackTrace();
		System.out.println(e);
	    }

        } else {
            System.out.println("application time Mmult (" + task + "," + rec
                    + "," + loop + "," + cells + ") GAVE WRONG RESULT!");
            System.out.println("application result Mmult (" + task + "," + rec
                    + "," + loop + "," + cells + ") GAVE WRONG RESULT!");
            System.out.println("Test failed!");
        }
    }
}
