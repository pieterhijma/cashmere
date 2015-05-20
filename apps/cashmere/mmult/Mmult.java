/* $Id: Mmult.java 2844 2004-11-24 10:52:27Z ceriel $ */

import java.io.PrintStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import ibis.cashmere.many_core.MCCashmere;
import ibis.cashmere.many_core.MCTimer;

//
// Class Mmult
//
// Matrix multiply functionality
// This is the ony really interesting part
// The rest is dead weight
//
final class Mmult extends ibis.cashmere.CashmereObject implements MmultInterface,
        java.io.Serializable {


    public Matrix mult(int task, int rec, Matrix a, Matrix b, Matrix c) {
	return real_mult(task, rec, a, b, c);
    }

    // real  functionality: tasked-mat-mul
    public Matrix real_mult(int task, int rec, Matrix a, Matrix b, Matrix c) {

        Matrix f_00 = null;
        Matrix f_01;
        Matrix f_10;
        Matrix f_11;

	if (task == 0) {
	    if (rec == 0) {
		c.matMul(a, b);
		return c;
	    }
	    else if (MCCashmere.getSpeed() > 10) { 
		MCCashmere.enableManycore();
		rec--;
	    }
	    else {
		rec--;
	    }
	}
	else if (task > 0) {
	    task--;
	}



        f_00 = /* spawn */mult(task, rec, a._00, b._00, c._00);
        f_01 = /* spawn */mult(task, rec, a._00, b._01, c._01);
        f_10 = /* spawn */mult(task, rec, a._10, b._00, c._10);
        f_11 = /* spawn */mult(task, rec, a._10, b._01, c._11);
        sync();
        // Here we can release the bytebuffers in c that are not used
        // in f.
        List<ByteBuffer> lf = new ArrayList<ByteBuffer>();
        f_00.getByteBuffers(lf);
        f_01.getByteBuffers(lf);
        f_10.getByteBuffers(lf);
        f_11.getByteBuffers(lf);
        List<ByteBuffer> lc = new ArrayList<ByteBuffer>();
        c.getByteBuffers(lc);
        for (ByteBuffer bc : lc) {
            boolean found = false;
            // Don't use lf.contains(bc), because ByteBuffer redefines equals().
            for (ByteBuffer bf: lf) {
                if (bf == bc) {
                    found = true;
                    break;
                }
            }
            if (! found) {
                MCCashmere.releaseByteBuffer(bc);
            }
        }


        // spawn child threads; k = 1; after having synched on
        // get_result() sync on the child threads
        // it's slightly inefficient that 01 will be started after 00, a
        // completely unrelated sub matrix, is ready
        // what we really want is systolicism, where f_01 is started as
        // soon as its data dependency is resolved.  Now, the sematics
        // of join are that it blocks the entire parent thread, instead
        // of just the 00 call. Mentat has the data dependency
        // analysis that we want.

        c._00 = /* spawn */mult(task, rec, a._01, b._10, f_00);
        c._01 = /* spawn */mult(task, rec, a._01, b._11, f_01);
        c._10 = /* spawn */mult(task, rec, a._11, b._10, f_10);
        c._11 = /* spawn */mult(task, rec, a._11, b._11, f_11);
        sync();
        // Here we can release the bytebuffers in f that are not used
        // in c.
        lc.clear();
        c.getByteBuffers(lc);
        for (ByteBuffer bf : lf) {
            boolean found = false;
            for (ByteBuffer bc: lc) {
                if (bf == bc) {
                    found = true;
                    break;
                }
            }
            if (! found) {
                MCCashmere.releaseByteBuffer(bf);
            }
        }

        return c;
    }

    public static int power(int base, int exponent) {
        return (int) Math.pow(base, exponent);
    }

    public static void main(String args[]) {
        int task = 2, rec = 2, loop = power(2, 2);
        long start, end;
        double time;
        Mmult m = new Mmult();
	MCCashmere.initialize();

        if (args.length == 3) {
            task = Integer.parseInt(args[0]);
            rec = Integer.parseInt(args[1]);
            loop = Integer.parseInt(args[2]);
        } else if (args.length != 0) {
            System.out.println("usage: mmult [task rec loop]");
            System.exit(66);
        }

        int cells = power(2, task + rec) * loop;
        System.out.println("Running Matrix multiply, on a matrix of size "
                + cells + " x " + cells + ", threads = " + power(8, task));

        Matrix a = new Matrix(task, rec, loop, 1.0f, false);
        Matrix b = new Matrix(task, rec, loop, 1.0f, false);
        Matrix c = new Matrix(task, rec, loop, 0.0f, false);

        // System.out.println("A:");
        // a.print(task, rec);
        // System.out.println("\nB:");
        // b.print(task, rec);
	
	MCTimer timer = MCCashmere.createOverallTimer();
        start = System.currentTimeMillis();
	int eventNo = timer.start();
        c = /* spawn */m.real_mult(task, rec, a, b, c);
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
	    System.out.printf("application performance: %f GFLOPS\n", 2l * cells
		    * cells * cells / 1.0e9 / time);
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
            //System.exit(1);
        }
    }
}
