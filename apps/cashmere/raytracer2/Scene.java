class Scene extends ibis.cashmere.SharedObject {

    int width;
    int height;

    Camera camera;
    Sphere[] spheres;

    int[] seeds;

    Scene(Camera camera, Sphere[] spheres) {
	this.camera = camera;
	this.spheres = spheres;
    }

    void update(int width, int height) {
	this.width = width;
	this.height = height;
	camera.update(width, height);
    }

    void addSeeds(int[] seeds) {
	this.seeds = seeds;
    }
}
