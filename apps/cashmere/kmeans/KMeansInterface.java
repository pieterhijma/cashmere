public interface KMeansInterface extends ibis.cashmere.Spawnable {
    public KMeansResult kMeansCluster(float[] centers, Points points,
	    int numTasks, int gpuJobsPerTask, int jobNo, boolean gpu);
    public Integer makeSOInvocationStart(Points points);
 }
