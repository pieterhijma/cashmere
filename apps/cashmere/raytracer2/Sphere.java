class Sphere implements java.io.Serializable {
    
    final static int DIFF = 0;
    final static int SPEC = 1;
    final static int REFR = 2;


    final float radius;
    final float[] position;
    final float[] emission;
    final float[] color;
    final int reflectionType;


    Sphere(float radius, float[] position, float[] emission, float[] color,
	    int reflectionType) {
	this.radius = radius;
	this.position = position;
	this.emission = emission;
	this.color = color;
	this.reflectionType = reflectionType;
    }
}
