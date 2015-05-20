public class KMeansResult implements java.io.Serializable {

    private static final long serialVersionUID = 1L;
    
    float[] centers;
    int[] counts;
    int offset;
    int end;

    public KMeansResult(float[] newCenters, int[] counts) {
	this.centers = newCenters;
	this.counts = counts;
    }

    public void add(KMeansResult other, int nFeatures) {
	for (int i = 0; i < centers.length; i++) {
            centers[i] += other.centers[i];
	}

        for (int i = 0; i < counts.length; i++) {
            counts[i] += other.counts[i];
        }
    }

    public void avg(int nFeatures) {
	for (int i = 0; i < centers.length/nFeatures; i++) {
	    if (counts[i] != 0) {
		for (int j = 0; j < nFeatures; j++) {
		    centers[i*nFeatures+ j] /= counts[i];
		}
	    }
	}
    }
}
