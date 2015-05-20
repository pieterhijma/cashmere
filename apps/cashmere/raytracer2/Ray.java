class Ray {

    float[] origin;
    float[] direction;


    Ray() {
	origin = new float[3];
	direction = new float[3];
    }


    Ray(float[] origin, float[] direction) {
	this.origin = origin;
	this.direction = direction;
    }


    void init(float[] a, float[] b) {
	V.assign(origin, a);
	V.assign(direction, b);
    }


    void assign(Ray r) {
	V.assign(origin, r.origin);
	V.assign(direction, r.direction);
    }
}
