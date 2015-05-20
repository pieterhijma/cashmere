/* $Id: NBody.java 11903 2010-03-25 09:29:28Z ceriel $ */

import ibis.cashmere.CashmereObject;
import ibis.cashmere.many_core.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.io.BufferedWriter;
import java.io.FileWriter;

/* strictfp */final class NBody extends CashmereObject implements
        NBodyInterface {

    static boolean remoteViz = false;

    static String dumpViz = null;

    static boolean viz = false;

    static boolean debug = false; //use -(no)debug to modify

    static final boolean ASSERTS = false; //also used in other nbody classes

    private static int spawn_min = 16384; //use -min <threshold> to modify
    
    private static int gpu_factor = 16; //use -gpufactor <factor> to modify
    
    private static long totalTime = 0, updateTime = 0, forceCalcTime = 0,
            vizTime = 0;

    static float[] mass;
    static float[] positions;
    static float[] velocities;
    static float[] oldAcc;

    private transient RunParameters params;

    private static String dump_file = null;

    private static int dump_iters = 100;

    NBody(int n, RunParameters params) {
        this.params = params;
        mass = new float[n];
        positions = new float[n*3];
        velocities = new float[n*3];
        oldAcc = new float[n*3];
        new Plummer().generate(mass, positions, velocities);
    }

    NBody(Reader r, RunParameters params) throws IOException {
        BufferedReader br = new BufferedReader(r);
        StreamTokenizer tokenizer = new StreamTokenizer(br);

        tokenizer.resetSyntax();
        tokenizer.wordChars('0', '9');
        tokenizer.wordChars('.', '.');
        tokenizer.wordChars('+', '+');
        tokenizer.wordChars('E', 'E');
        tokenizer.wordChars('-', '-');
        tokenizer.eolIsSignificant(false);
        tokenizer.whitespaceChars('\t', ' ');

        int nBodies = (int) readFloat(tokenizer,
            "number expected for number of bodies");

        // Ignore number of dimensions.
        readFloat(tokenizer, "number expected for dimensions");

        double startTtime = readFloat(tokenizer,
            "number expected for start time");

        this.params = new RunParameters(params.DT, params.SOFT,
            params.THRESHOLD, params.GPUFACTOR,
            startTtime, params.END_TIME,
            params.ITERATIONS, params.GPU);

        // Allocate bodies and read mass.
        mass = new float[nBodies];
        positions = new float[nBodies*3];
        velocities = new float[nBodies*3];
        oldAcc = new float[nBodies*3];
        
        for (int i = 0; i < nBodies; i++) {
            mass[i] = readFloat(tokenizer,
                "number expected for mass of body");
        }

        // Read positions
        for (int i = 0; i < nBodies; i++) {
            positions[3*i] = readFloat(tokenizer, "x coordinate expected for body");
            positions[3*i+1] = readFloat(tokenizer, "y coordinate expected for body");
            positions[3*i+2] = readFloat(tokenizer, "z coordinate expected for body");
        }

        // Read velocities
        for (int i = 0; i < nBodies; i++) {
            velocities[3*i] = readFloat(tokenizer, "x velocity expected for body");
            velocities[3*i+1] = readFloat(tokenizer, "y velocity expected for body");
            velocities[3*i+2] = readFloat(tokenizer, "z velocity expected for body");
        }
    }

    private float readFloat(StreamTokenizer tk, String err)
        throws IOException {
        int tok = tk.nextToken();
        if (tok != StreamTokenizer.TT_WORD) {
            throw new IOException(err);
        }
        return Float.parseFloat(tk.sval);
    }

    private void dump(int iteration) {
        try {
            BufferedWriter w = new BufferedWriter(new FileWriter(dump_file));
            w.write("" + mass.length + "\n");
            w.write("3\n");
            w.write("" + (params.START_TIME + iteration * params.DT) + "\n");
            for (int i = 0; i < mass.length; i++) {
                w.write("" +mass[i] + "\n");
            }
            for (int i = 0; i < mass.length; i++) {
                w.write("" + positions[3*i] + " " + positions[3*i+1]
                    + " " + positions[3*i+2]+ "\n");
            }
            for (int i = 0; i < mass.length; i++) {
                w.write("" + velocities[3*i] + " " + velocities[3*i+1]
                    + " " + velocities[3*i+2]+ "\n");
            }
            w.close();
        } catch (Exception e) {
            throw new Error(e.toString(), e);
        }
    }

    /* guard method for the spawn below */
    public boolean guard_NBodySO(int lo, int high, int iteration, Bodies bodies) {
        // System.out.println("guard: iteration = " + iteration
        //         + ", bodies.iteration = " + bodies.getIteration());
        return bodies.getIteration() + 1 == iteration;
    }

    /* spawnable */
    public BodyUpdates NBodySO(int lo, int high, int iteration, Bodies bodies) {
        return doNBodySO(lo, high, iteration, bodies);
    }

    public BodyUpdates doNBodySO(int lo, int high, int iteration, Bodies bodies) {
        RunParameters params = bodies.getParams();

        int threshold = params.GPUFACTOR * params.THRESHOLD;
        if (high - lo <= params.THRESHOLD) {
            BodyUpdates res = new BodyUpdates(high - lo);
            bodies.nbodySequential(lo, high, res);
            return res;
        }
        
        if (high - lo <= threshold) {
            MCCashmere.enableManycore();
        }
        
        if (MCCashmere.spawningToManycore()) {
            threshold = params.THRESHOLD;
        }

        int spawn = 16;
        int s = 2;
        while (s < spawn) {
            if ((high-lo)/s <= threshold) {
        	spawn = s;
        	break;
            }
            s <<= 1;
        }
 
        BodyUpdates result = new BodyUpdates(0);
        BodyUpdates[] res = new BodyUpdates[spawn];

        int size = (high - lo) / spawn;
        int rem = (high - lo) % spawn;
        int start = lo;

        for (int i = 0; i < spawn; i++) {
            int end = start + size;
            if (i < rem) end++;
            res[i] = /* spawn */NBodySO(start, end, iteration, bodies);
            start = end;
        }

        sync();
        return result.combineResults(res);
    }

    void runSim() {
        long start = 0, end, phaseStart = 0;

        BodyUpdates result = null;

        BodyCanvas bc = null;

        RemoteVisualization rv = null;

        System.out.println("NBody: simulating " + mass.length
            + " bodies, spawn-min-threshold = "
            + spawn_min);

        if (viz) {
            bc = BodyCanvas.visualize(positions, velocities);
        }

        if (remoteViz) {
            rv = new RemoteVisualization();
        } else if (dumpViz != null) {
            try {
                rv = new RemoteVisualization(dumpViz);
            } catch (IOException e) {
                System.out.println("Warning: could not open " + dumpViz
                    + ", got " + e);
            }
        }

        // turn of Cashmere during sequential pars.
        ibis.cashmere.CashmereObject.pause();

        Bodies bodies;

        printMemStats("pre bodies");
        
        bodies = new Bodies(positions, mass, velocities, oldAcc, params);
        bodies.exportObject();

        try {
            Thread.sleep(10000);
        } catch(Throwable e) {
            // ignore
        }

        printMemStats("post bodies");

        MCTimer timer = MCCashmere.createOverallTimer();
        int eventNo = 0;

        System.out.println("First iteration is not included in the timings");

        for (int iteration = 0; iteration < params.ITERATIONS; iteration++) {
            printMemStats("begin iter " + iteration);
            
            long updateTimeTmp = 0, forceCalcTimeTmp = 0, vizTimeTmp = 0;

            if (iteration == 1) {
                start = System.currentTimeMillis();
                eventNo = timer.start();
            }
            // System.out.println("Starting iteration " + iteration);

            phaseStart = System.currentTimeMillis();

            //force calculation

            ibis.cashmere.CashmereObject.resume(); //turn ON divide-and-conquer stuff

            result = NBodySO(0, mass.length, iteration, bodies);
            sync();
            
            ibis.cashmere.CashmereObject.pause(); // pause divide-and-conquer stuff

            printMemStats("post force " + iteration);

            if (iteration > 0) {
                forceCalcTimeTmp = System.currentTimeMillis() - phaseStart;
                forceCalcTime += forceCalcTimeTmp;
            }

            phaseStart = System.currentTimeMillis();

            result.prepareForUpdate();

            printMemStats("post prepare for update " + iteration);

            //            System.err.println("update: " + result);

            // Ceriel: commented out so that we measure full iterations.
            // if (iteration < params.ITERATIONS - 1) {
                bodies.updateBodies(result, iteration);
            // } else {
            //     bodies.updateBodiesLocally(result, iteration);
            // }


            if (iteration > 0) {
                updateTimeTmp = System.currentTimeMillis() - phaseStart;
                updateTime += updateTimeTmp;
            }

            printMemStats("post update " + iteration);

            phaseStart = System.currentTimeMillis();

            if (dump_file != null && iteration != 0
                && (iteration % dump_iters) == 0) {
                dump(iteration);
            }

            if (viz) {
                bc.repaint();
            }

            if (rv != null) {
                rv.showBodies(positions, iteration, updateTimeTmp
                    + forceCalcTimeTmp);
            }

            if (iteration > 0) {
                vizTimeTmp = System.currentTimeMillis() - phaseStart;
                vizTime += vizTimeTmp;

                long total = updateTimeTmp + forceCalcTimeTmp + vizTimeTmp;

                System.out.println("Iteration " + iteration + " done"
                    + ", update = " + updateTimeTmp + ", force = "
                    + forceCalcTimeTmp + ", viz = " + vizTimeTmp + ", total = "
                    + total);
            }
        }

        timer.stop(eventNo);

        end = System.currentTimeMillis();
        totalTime = end - start;
        if (rv != null) {
            rv.stopRemoteViz();
        }
    }

    void run() {
        System.out.println("Iterations: " + params.ITERATIONS + " (timings DO "
            + "include the first iteration!)");

        MCCashmere.initialize();

        runSim();

        System.out.println("update took             " + updateTime / 1000.0
            + " s");
        System.out.println("Force calculation took  " + forceCalcTime / 1000.0
            + " s");
        System.out
            .println("visualization took      " + vizTime / 1000.0 + " s");
        System.out.println("application nbody took "
            + (double) (totalTime / 1000.0) + " s");

        double GFLOPS = (double)(params.ITERATIONS - 1) * 18 * mass.length * mass.length / 1e9;

        System.out.println("GFLOPS/s = " + GFLOPS/(totalTime / 1000.0));

        printMemStats("done");
    }

    public static void printMemStats(String prefix) {
	/*
        Runtime r = Runtime.getRuntime();

        System.gc();
        long free = r.freeMemory() / (1024*1024);
        long max = r.maxMemory() / (1024*1024);
        long total = r.totalMemory() / (1024*1024);
        System.err.println(prefix + ": free = " + free + " max = " + max
            + " total = " + total);
            */
    }

    public static void main(String argv[]) {
        int nBodies = 0;
        boolean nBodiesSeen = false;
        FileReader rdr = null;

        double dt = 0.025;
        double soft = 0.0000025; // this value is copied from Suel, splash-2 uses 0.05
        double startTime = 0.0;
        double endTime = 0.175;
        int iterations = -1;
        boolean gpu = true;


        printMemStats("start");
        
        //parse arguments
        for (int i = 0; i < argv.length; i++) {
            //options
            if (argv[i].equals("-gpu")) {
                gpu = true;
            } else if (argv[i].equals("-no-gpu")) {
                gpu = false;
            } else if (argv[i].equals("-debug")) {
                debug = true;
            } else if (argv[i].equals("-no-debug")) {
                debug = false;
            } else if (argv[i].equals("-viz")) {
                viz = true;
            } else if (argv[i].equals("-remote-viz")) {
                remoteViz = true;
            } else if (argv[i].equals("-dump-viz")) {
                dumpViz = argv[++i];
            } else if (argv[i].equals("-no-viz")) {
                viz = false;
            } else if (argv[i].equals("-it")) {
                iterations = Integer.parseInt(argv[++i]);
                if (iterations < 0) {
                    throw new IllegalArgumentException(
                        "Illegal argument to -it: number of iterations must be >= 0 !");
                }
            } else if (argv[i].equals("-dump-iter")) {
                dump_iters = Integer.parseInt(argv[++i]);
                if (dump_iters <= 0) {
                    throw new IllegalArgumentException(
                        "Illegal argument to -dump-iter: number of iterations must be > 0 !");
                }
            } else if (argv[i].equals("-starttime")) {
                startTime = Double.parseDouble(argv[++i]);
            } else if (argv[i].equals("-endtime")) {
                endTime = Double.parseDouble(argv[++i]);
            } else if (argv[i].equals("-dt")) {
                dt = Double.parseDouble(argv[++i]);
            } else if (argv[i].equals("-eps")) {
                soft = Double.parseDouble(argv[++i]);
            } else if (argv[i].equals("-input")) {
                try {
                    rdr = new FileReader(argv[++i]);
                } catch (IOException e) {
                    throw new IllegalArgumentException(
                        "Could not open input file " + argv[i]);
                }
            } else if (argv[i].equals("-dump")) {
                dump_file = argv[++i];
            } else if (argv[i].equals("-min")) {
                spawn_min = Integer.parseInt(argv[++i]);
                if (spawn_min < 0) {
                    throw new IllegalArgumentException(
                        "Illegal argument to -min: Spawn min threshold must be >= 0 !");
                }
            } else if (argv[i].equals("-gpufactor")) {
                gpu_factor = Integer.parseInt(argv[++i]);
                if (gpu_factor < 1) {
                    throw new IllegalArgumentException(
                        "Illegal argument to -gpufactor: Gpufactor threshold must be >= 1 !");
                }
            } else if (!nBodiesSeen) {
                try {
                    nBodies = Integer.parseInt(argv[i]); //nr of bodies to
                    // simulate
                    nBodiesSeen = true;
                } catch (NumberFormatException e) {
                    System.err.println("Illegal argument: " + argv[i]);
                    System.exit(1);
                }
            } else {
                System.err.println("Illegal argument: " + argv[i]);
                System.exit(1);
            }
        }

        if (nBodies < 1 && rdr == null) {
            System.err.println("Invalid body count, generating 300 bodies...");
            nBodies = 3000;
        }

        RunParameters params = new RunParameters(dt, soft,
            spawn_min, gpu_factor, startTime, endTime,
            iterations, gpu);

        if (rdr != null) {
            if (nBodiesSeen) {
                System.out
                    .println("Warning: nBodies as seen in argument list ignored!");
            }
            try {
                new NBody(rdr, params).run();
            } catch (IOException e) {
                throw new NumberFormatException(e.getMessage());
            }
        } else {
            new NBody(nBodies, params).run();
        }

        ibis.cashmere.CashmereObject.resume(); // allow cashmere to exit cleanly
    }
}
