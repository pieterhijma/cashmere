public interface RaytracerInterface extends ibis.cashmere.Spawnable {

    public float[] render(Scene scene, int nrSamples, 
	    int x, int y, int width, int height, boolean cpu);

}
