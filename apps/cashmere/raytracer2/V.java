class V {


    static void init(float[] v, float x, float y, float z) {
	v[0] = x;
	v[1] = y;
	v[2] = z;
    }


    static void assign(float[] a, float[] b) {
	init(a, b[0], b[1], b[2]);
    }


    static void clr(float[] v) {
	init(v, 0f, 0f, 0f);
    }


    static void add(float[] v, float[] a, float[] b) {
	init(v, a[0] + b[0], a[1] + b[1], a[2] + b[2]);
    }


    static void sub(float[] v, float[] a, float[] b) {
	init(v, a[0] - b[0], a[1] - b[1], a[2] - b[2]);
    }


    static void mul(float[] v, float[] a, float[] b) {
	init(v, a[0] * b[0], a[1] * b[1], a[2] * b[2]);
    }


    static void smul(float[] v, float a, float[] b) {
	init(v, a * b[0], a * b[1], a * b[2]);
    }


    static float dot(float[] a, float[] b) {
	return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }


    static void norm(float[] v) {
	float l = 1.0f / (float) Math.sqrt(dot(v, v));
	smul(v, l, v);
    }


    static void xcross(float[] v, float[] a, float[] b) {
	init(v, a[1] * b[2] - a[2] * b[1], 
		a[2] * b[0] - a[0] * b[2],
		a[0] * b[1] - a[1] * b[0]);
    }


    static boolean iszero(float[] v) {
	return v[0] == 0.0f && v[1] == 0.0f && v[2] == 0.0f;
    }


    static float clamp(float x, float a, float b) {
	return x < a ? a : (x > b ? b : x);
    }


    static float sign(float x) {
	return x > 0.0f ? 1.0f : -1.0f;
    }


    static int toInt(float x) {
	return (int) (Math.pow(
		clamp(x, 0.f, 1.f), 
		1.0f / 2.2f)
	    * 255.0 + 0.5);
    }
}
