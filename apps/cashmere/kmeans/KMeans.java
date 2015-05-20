import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Random;

import ibis.cashmere.many_core.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KMeans extends ibis.cashmere.CashmereObject implements KMeansInterface {

    public static Logger logger = LoggerFactory.getLogger("KMeans");
 
    private static final long serialVersionUID = 1L;

    public static final float THRESHOLD = 0.1f;
    public static final int MAX_ITERATIONS = 1000;

    private static float centers[];
    private static int nFeatures = 4;	// Default

    // Read initial "centers" file.
    private static final float[] readCenters(String dir) throws Exception {
	File d = new File(dir, "centers");
        DataInputStream is = new DataInputStream(new BufferedInputStream(
                    new FileInputStream(d)));
        nFeatures = is.readInt();
        int len = is.readInt();
        float[] result = new float[len];
        for (int i = 0; i < len; i++) {
            result[i] = is.readFloat();
        }
	is.close();
	return result;
    }

    // Read "points" directory.
    private static final float[][] readPoints(String dir, int numTasks) throws Exception {
	File d = new File(dir, "points");
	DataInputStream is = null;
	int len = 0;
	float[] result = null;
	try {
	    is = new DataInputStream(new BufferedInputStream(
		    new FileInputStream(d)));
	    int n = is.readInt();
	    if (n != nFeatures) {
		throw new Error("Wrong number of features");
	    }
	    len = is.readInt();
	    result = new float[len];
	    for (int i = 0; i < len; i++) {
		result[i] = is.readFloat();
	    }
	} finally {
	    if (is != null) {
		is.close();
	    }
	}

        int nPoints = len / nFeatures;
        int pointsPerTask = nPoints / numTasks;
        int p1 = nPoints - numTasks * pointsPerTask;

        float[][] pts = new float[numTasks][];
        int pIndex = 0;
        for (int i = 0; i < p1; i++) {
            pts[i] = new float[(pointsPerTask + 1) * nFeatures];
            for (int j = 0; j <= pointsPerTask; j++) {
                for (int k = 0; k < nFeatures; k++) {
                    pts[i][k*pointsPerTask + j] = result[pIndex*nFeatures+k];
                }
                pIndex++;
            }
        }
        for (int i = p1; i < numTasks; i++) {
            pts[i] = new float[pointsPerTask * nFeatures];
            for (int j = 0; j < pointsPerTask; j++) {
                for (int k = 0; k < nFeatures; k++) {
                    pts[i][k * pointsPerTask + j] = result[pIndex*nFeatures+k];
                }
                pIndex++;
            }
        }
        return pts;
    }

    @Override
    public Integer makeSOInvocationStart(Points points) {
        try {
            logger.debug("makeSOInvocationStart");
            Thread.sleep(1000);
        } catch(Throwable e) {
            // ignore
        }
        return null;
    }

    public boolean guard_makeSOInvocationStart(Points points) {
        return points.isInitialized();
    }
    
    /*
    public boolean guard_kMeansCluster(float[] centers, Points points,
	    int numTasks, int gpuJobsPerTask, int jobNo, boolean gpu) {
	return points.isInitialized();
    }
    */

    // A single iteration of the clustering algorithm.
    @Override
    public KMeansResult kMeansCluster(float[] centers, Points points,
	    int numTasks, int gpuJobsPerTask, int jobNo, boolean gpu) {
        return realKMeansCluster(centers, points, numTasks, gpuJobsPerTask, jobNo, gpu);
    }

    public KMeansResult realKMeansCluster(float[] centers, Points points,
	    int numTasks, int gpuJobsPerTask, int jobNo, boolean gpu) {
        if (logger.isDebugEnabled()) {
            logger.debug("Job: numTasks = " + numTasks);
        }
	if (numTasks == 0) {
	    return kMeansCluster(centers, points, jobNo, gpu);
	}
	int nSpawns;
	int newNumTasks;
	if (numTasks > 1) {
            nSpawns = numTasks;
            newNumTasks = 1;
	} else {
	    nSpawns = gpuJobsPerTask;
	    MCCashmere.enableManycore();
	    newNumTasks = 0;
	}
	KMeansResult[] results = new KMeansResult[nSpawns];
	for (int i = 0; i < nSpawns; i++) {
	    results[i] = kMeansCluster(centers, points, newNumTasks, gpuJobsPerTask,
		    jobNo, gpu);
	    if (newNumTasks == 0) {
		jobNo++;
	    } else {
		jobNo += newNumTasks * gpuJobsPerTask;
	    }
	}
	sync();

	for (int i = 1; i < nSpawns; i++) {
	    results[0].add(results[i], nFeatures);
	}
	return results[0];
    }
    

    private int[] kMeansClusterCPU(float[] centers, float[] pts, int nFeatures, int jobSize) {
	int nCenters = centers.length / nFeatures;
	int[] pointsCluster = new int[jobSize];

	for (int pIndex = 0; pIndex < jobSize; pIndex++) {
	    float minimum = Float.MAX_VALUE;
	    int cluster = -1;
	    for (int i = 0; i < nCenters; i++) {
		float distanceSq = eucledianDistanceSq(pts, pIndex, centers, i,
			nFeatures, jobSize);
		if (distanceSq < minimum) {
		    minimum = distanceSq;
		    cluster = i;
		}
	    }
	    pointsCluster[pIndex] = cluster;
	}
	return pointsCluster;
    }


    private int[] kMeansClusterGPU(int jobNo, float[] centers, float[] pts, int nFeatures, int jobSize) {
	int[] pointsCluster = new int[jobSize];
        try {
            Kernel kernel = MCCashmere.getKernel();
            KernelLaunch kernelLaunch = kernel.createLaunch();

            if (logger.isDebugEnabled()) {
                logger.debug("Executing job " + jobNo + " of size " + jobSize);
            }

            MCL.launchKmeans_kernelKernel(kernelLaunch, jobSize, centers.length/nFeatures,
                    nFeatures, pts, centers, pointsCluster);

            // System.out.println("Executed job " + jobNo);
        
            return pointsCluster;
        } catch (MCCashmereNotAvailable e) {
            System.err.println("fallback to CPU");
            System.err.println(e.getMessage());

            return kMeansClusterCPU(centers, pts, nFeatures, jobSize);
        } catch(RuntimeException e) {
            e.printStackTrace(System.out);
            throw e;
        } catch(Error e) {
            e.printStackTrace(System.out);
            throw e;
        }
    }

    public KMeansResult kMeansCluster(float[] centers, Points points,
	    int jobNo, boolean gpu) {

        int nFeatures = points.nFeatures();
        float[] newCenters = new float[centers.length];
        int nCenters = centers.length / nFeatures;
        int[] counts = new int[nCenters];
        KMeansResult result = new KMeansResult(newCenters, counts);
        float[] pts = points.getPoints(jobNo);
        int jobSize = pts.length / nFeatures;

        int[] pointsCluster = gpu ? kMeansClusterGPU(jobNo, centers, pts, nFeatures, jobSize)
                    : kMeansClusterCPU(centers, pts, nFeatures, jobSize);

        MCTimer timer = MCCashmere.getTimer("java", "main", "CPU part",
                10);
        int event = timer.start();
        for (int pIndex = 0; pIndex < jobSize; pIndex++) {
            int cluster = pointsCluster[pIndex];
            for (int i = 0; i < nFeatures; i++) {
                newCenters[cluster * nFeatures + i] += pts[i * jobSize + pIndex];
            }
            counts[cluster]++;
        }

        timer.stop(event);

	return result;
    }

    /***** SUPPORT METHODS *****/
    private static final float eucledianDistanceSq(float[] pts, int i1, float[] set2,
	    int i2, int nFeatures, int jobSize) {
	float sum = 0;
	for (int i = 0; i < nFeatures; i++) {
	    float diff = set2[i2 * nFeatures + i] - pts[i * jobSize + i1];
	    sum += (diff * diff);
	}
	return sum;
    }

    private static Map<String, List<String>> createDefines(int nFeatures) {
	Map<String, List<String>> mapDefines = 
	    new HashMap<String, List<String>>();

	List<String> defines = new ArrayList<String>();
	defines.add(String.format("#define MCL_nfeatures %d\n", nFeatures));
	mapDefines.put("kmeans_cc_2_0.cl", defines);
	mapDefines.put("kmeans_xeon_phi.cl", defines);
	mapDefines.put("kmeans_hd7970.cl", defines);
	return mapDefines;
    }

    public static void main(String[] args) throws Exception {

	int numTasks = 1;
        int gpuJobsPerTask = 1;
        int maxIter = MAX_ITERATIONS;
        boolean runOnGpu = true;
        String dataDir = null;
        int nPoints = 1024 * 1024 * 128;
        int nCenters = 1024;
        int nNodes = 0;

        String nt = System.getProperty("ibis.pool.size");
        if (nt != null) {
            numTasks = Integer.parseInt(nt);
            nNodes = numTasks;
        }

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-iters")) {
                i++;
                maxIter = Integer.parseInt(args[i]);
            } else if (args[i].equals("-gpuJobsPerTask")) {
                i++;
                gpuJobsPerTask = Integer.parseInt(args[i]);
            } else if (args[i].equals("-nTasks")) {
                i++;
                numTasks = Integer.parseInt(args[i]);
            } else if (args[i].equals("-nCenters")) {
                i++;
                nCenters = Integer.parseInt(args[i]);
            } else if (args[i].equals("-nPoints")) {
        	i++;
        	nPoints = Integer.parseInt(args[i]);
            } else if (args[i].equals("-nFeatures")) {
        	i++;
        	nFeatures = Integer.parseInt(args[i]);
            } else if (args[i].equals("-gpu")) {
                runOnGpu = true;
            } else if (args[i].equals("-cpu")) {
                runOnGpu = false;
            } else {
                if (dataDir == null) {
                    dataDir = args[i];
                } else {
                    throw new Error("Usage: java KMeans [ -cpu | -gpu ] [ -iters <num> ] [ -nTasks <num> ] <dataDir>");
                }
            }
        }
        
	Points points = new Points();

	KMeans m = new KMeans();

        if (dataDir == null) {
            centers = generateRandom(nFeatures * nCenters, 307L);
         } else {
            centers = readCenters(dataDir);
            nCenters = centers.length / nFeatures;

            points.initializePoints(readPoints(dataDir, numTasks * gpuJobsPerTask), nFeatures);

            nPoints = points.nPoints();
        }
        
        /*
	System.out.println("Original centers:");
	for (int i = 0; i < nCenters; i++) {
	    System.out.print("(");
	    for (int j = 0; j < nFeatures; j++) {
		System.out.print(centers[i*nFeatures+j]);
		if (j < nFeatures - 1) {
		    System.out.print(", ");
		}
	    }
	    System.out.println(")");
	}
        */
	

        System.out.println("KMeans, running on " + (runOnGpu ? "GPU" : "CPU")
                + ", number of tasks = " + numTasks);
        System.out.println("Number of features: " + nFeatures + ", number of centers: " + centers.length/nFeatures
                + ", number of points: " + nPoints);

	points.exportObject();
	MCCashmere.initialize(createDefines(nFeatures));
        Integer x;

        for (int i = 0; i < nNodes; i++) {
            x = m.makeSOInvocationStart(points);
        }

        // Give other nodes time to steal job, so don't sync, otherwise 
        // master would steal these jobs.
        // This makes sure that every node updates the shared object.
        try {
            Thread.sleep(10000);
        } catch(Throwable e) {
            // ignore
        }

	if (dataDir == null) {
	    points.generatePoints(nPoints, nFeatures, numTasks * gpuJobsPerTask);
	}

	int iteration = 0;
	float max;
	KMeansResult results = new KMeansResult(centers, null);

        synchronized(points) {
            while (! points.isInitialized()) {
                try {
                    points.wait();
                } catch(Throwable e) {
                    // ignore
                }
            }
        }

        m.sync();

        try {
            Thread.sleep(10000);
        } catch(Throwable e) {
            // ignore
        }

	MCTimer timer = MCCashmere.createOverallTimer();

	int eventNo = timer.start();
	do {
	    results = m.realKMeansCluster(centers, points, numTasks, gpuJobsPerTask, 0, runOnGpu);
	    iteration++;

	    results.avg(nFeatures);

	    max = 0.0f;

	    // Compute new centers
	    for (int i = 0; i < nCenters; i++) {
		if (results.counts[i] > 0) {
                    float sum = 0;
                    for (int j = 0; j < nFeatures; j++) {
                        float diff = centers[i * nFeatures + j] - results.centers[i * nFeatures + j];
                        sum += (diff * diff);
                    }
                    float tmp = (float) Math.sqrt(sum);
                    if (tmp > max) max = tmp;
                    for (int j = 0; j < nFeatures; j++) {
                        centers[i*nFeatures+j] = results.centers[i*nFeatures+j];
                    }
		}
	    }

            System.out.println("Iteration " + iteration + ", max diff = "
                    + max);
	} while (max >= THRESHOLD && iteration < maxIter);
	timer.stop(eventNo);

        double time = timer.totalTimeVal() / 1000000.0;

        System.out.println("KMeans time: " + time + " seconds");
        System.out.printf("Kmeans performance: %f GFLOPS\n", (iteration * 3.0 * nFeatures * (centers.length/nFeatures) * nPoints)/1.0e9/time);

	// Print out centroid results.
        /*
	System.out.println("Centers:");
	for (int i = 0; i < nCenters; i++) {
	    System.out.print("(");
	    for (int j = 0; j < nFeatures; j++) {
		System.out.print(centers[i*nFeatures+j]);
		if (j < nFeatures - 1) {
		    System.out.print(", ");
		}
	    }
	    System.out.println(")");
	}
        */
    }  

    public static float[] generateRandom(int sz, long seed) {
	float[] a = new float[sz];
	Random r = new Random(seed);
	for (int i = 0; i < a.length; i++) {
	    a[i] = r.nextFloat() * 100.0f;
	}
	return a;
    }
}
