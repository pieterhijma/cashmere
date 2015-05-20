class Camera implements java.io.Serializable {
    

    // user defined values
    float[] orig;
    float[] target;
    // calculated values
    float[] direction;
    float[] x;
    float[] y;


    Camera(float[] orig, float[] target) {
	this.orig = orig;
	this.target = target;
	this.direction = new float[3];
	this.x = new float[3];
	this.y = new float[3];
    }


    void update(int width, int height) {
	V.sub(direction, target, orig);
	V.norm(direction);

	float[] up = new float[] {0.0f, 1.0f, 0.0f};
	float fov = (float) (Math.PI / 180) * 45;
	V.xcross(x, direction, up);
	V.norm(x);
	V.smul(x, width * fov / height, x);

	V.xcross(y, x, direction);
	V.norm(y);
	V.smul(y, fov, y);
    }
}
