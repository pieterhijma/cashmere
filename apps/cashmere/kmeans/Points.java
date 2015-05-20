public class Points extends ibis.cashmere.SharedObject implements PointsInterface {

    private static final long serialVersionUID = 1L;
    
    private float[][] points;
    private int nFeatures;
    private boolean initialized = false;

    public float[] getPoints(int i) {
	return points[i];
    }

    public void initializePoints(float[][] points, int nFeatures) {
	this.points = points;
        this.nFeatures = nFeatures;
        initialized = true;
        synchronized(this) {
            notifyAll();
        }
    }

    public int nPoints() {
        int count = 0;
        for (int i = 0; i < points.length; i++) {
            count += points[i].length;
        }
        return count / nFeatures;
    }

    public int nFeatures() {
        return nFeatures;
    }

    @Override
    public void generatePoints(int npoints, int nfeatures, int nTasks) {
        KMeans.logger.debug("Generating points");
	this.nFeatures = nfeatures;
        points = new float[nTasks][];
        int sz = (npoints + nTasks - 1) / (nTasks);
        for (int i = 0; i < points.length; i++) {
            points[i] = KMeans.generateRandom(sz * nFeatures, 903L + i);
        }
        KMeans.logger.debug("Generated points");
        initialized = true;
        synchronized(this) {
            notifyAll();
        }
    }
    
    public boolean isInitialized() {
        KMeans.logger.debug("isInitialized: " + initialized);
	return initialized;
    }
}
