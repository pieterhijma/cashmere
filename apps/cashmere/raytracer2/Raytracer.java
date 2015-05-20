import java.util.Random;
import java.util.Scanner;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

import ibis.cashmere.many_core.*;



class Raytracer extends ibis.cashmere.CashmereObject implements RaytracerInterface {


    private float[] getSpheres(Sphere[] spheres) {
	int sizeSphere = (1 + 3 * 3 + 1);
	float[] fSpheres = new float[spheres.length * sizeSphere];
	for (int i = 0; i < spheres.length; i++) {
	    int j = 0;
	    int p = sizeSphere * i;
	    fSpheres[p + j] = spheres[i].radius;
	    fSpheres[p + ++j] = spheres[i].position[0];
	    fSpheres[p + ++j] = spheres[i].position[1];
	    fSpheres[p + ++j] = spheres[i].position[2];
	    fSpheres[p + ++j] = spheres[i].emission[0];
	    fSpheres[p + ++j] = spheres[i].emission[1];
	    fSpheres[p + ++j] = spheres[i].emission[2];
	    fSpheres[p + ++j] = spheres[i].color[0];
	    fSpheres[p + ++j] = spheres[i].color[1];
	    fSpheres[p + ++j] = spheres[i].color[2];
	    fSpheres[p + ++j] = spheres[i].reflectionType;
	}

	return fSpheres;
    }


    private float[] getCamera(Camera camera) {
	float[] fCamera = new float[5 * 3];
	int i = 0;
	fCamera[i] = camera.orig[0];
	fCamera[++i] = camera.orig[1];
	fCamera[++i] = camera.orig[2];
	fCamera[++i] = camera.target[0];
	fCamera[++i] = camera.target[1];
	fCamera[++i] = camera.target[2];
	fCamera[++i] = camera.direction[0];
	fCamera[++i] = camera.direction[1];
	fCamera[++i] = camera.direction[2];
	fCamera[++i] = camera.x[0];
	fCamera[++i] = camera.x[1];
	fCamera[++i] = camera.x[2];
	fCamera[++i] = camera.y[0];
	fCamera[++i] = camera.y[1];
	fCamera[++i] = camera.y[2];

	return fCamera;
    }


    private void renderGPU(Kernel kernel, Scene scene,
	    float[] colors, int[] seeds, 
	    float[] fSpheres, float[] fCamera, int currentSample, int x, int y,
	    int w, int h) throws MCCashmereNotAvailable {

	int width = scene.width;
	int height = scene.height;
	Sphere[] spheres = scene.spheres;


	KernelLaunch kl = kernel.createLaunch();
	// long start = System.nanoTime();
	MCL.launchRadianceKernel(kl, h, w, colors, false, height, width, seeds,
		false, spheres.length, fSpheres, false, fCamera, false, x, y,
		currentSample);
	// long end = System.nanoTime();

	// double t = (end - start) / 1e9;
	// System.out.printf("time: %f s\n", t);

    }


    private void writeFile(int[] vs, int width, int w, int h, int i, int j) {
	String fileName = String.format("out_%03d_%03d", i, j);
	try {
	    PrintStream out = new PrintStream(fileName);
	    for (int k = 0; k < width; k++) {
		out.printf("v[%03d] = %d\n", k, vs[(i * w + j) * width + k]);
	    }
	}
	catch (FileNotFoundException e) {
	    System.out.println(e.getMessage());
	    System.exit(1);
	}
    }


    private void writeFiles(int[] vs, int width, int w, int h) {
	for (int i = 0; i < h; i++) {
	    for (int j = 0; j < w; j++) {
		writeFile(vs, width, w, h, i, j);
	    }
	}
    }


    private void renderGPU(Scene scene, float[] colors, int nrSamples,
	    int x, int y, int w, int h) {
	try {
	    Kernel kernel = MCCashmere.getKernel();
	    Device d = kernel.getDevice();

	    int[] seeds = scene.seeds;
	    float[] fSpheres = getSpheres(scene.spheres);
	    float[] fCamera = getCamera(scene.camera);

	    
	    d.copy(colors, Argument.Direction.INOUT);
	    d.copy(seeds, Argument.Direction.IN);
	    d.copy(fSpheres, Argument.Direction.IN);
	    d.copy(fCamera, Argument.Direction.IN);
	    

            long start = System.nanoTime();

	    for (int i = 0; i < nrSamples; i++) {
		renderGPU(kernel, scene, colors, seeds, fSpheres, fCamera, 
			i, x, y, w, h);

	    }
            long end = System.nanoTime();
            double t = (end - start) / 1e9;
            System.out.printf("time for %d samples: %f s\n", nrSamples, t);

	    d.get(colors);
	}
	catch (MCCashmereNotAvailable e) {
	    System.err.println(e.getMessage());
	    System.err.println("Falling back to CPU");
	    renderCPU(scene, colors, nrSamples, x, y, w, h);
	}
    }


    private void renderCPU(Scene scene, int currentSample, 
	    float[] colors, int xOffset, int yOffset, int w, int h) {
	int width = scene.width;
	int height = scene.height;
	int[] seeds = scene.seeds;
	Camera camera = scene.camera;
	Sphere[] spheres = scene.spheres;

	float invWidth = 1.0f / width;
	float invHeight = 1.0f / height;
	
	for (int y = 0; y < h; y++) {
	    for (int x = 0; x < w; x++) {
		int realY = y + yOffset;
		int realX = x + xOffset;

		int id = realY  * width + realX;
		int id2 = 2 * id;

		float r1 = R.getRandom(seeds, id2, id2 + 1) - 0.5f;
		float r2 = R.getRandom(seeds, id2, id2 + 1) - 0.5f;

		float kcx = (realX + r1) * invWidth - 0.5f;
		float kcy = (realY + r2) * invHeight - 0.5f;

		float[] rdir = new float[3];


		V.init(rdir, 
			camera.x[0] * kcx + camera.y[0] * kcy + 
			    camera.direction[0],
			camera.x[1] * kcx + camera.y[1] * kcy + 
			    camera.direction[1],
			camera.x[2] * kcx + camera.y[2] * kcy + 
			    camera.direction[2]);

		float[] rorig = new float[3];
		V.smul(rorig, 0.1f, rdir);
		V.add(rorig, rorig, camera.orig);

		V.norm(rdir);

		Ray ray = new Ray(rorig, rdir);

		float[] r = new float[3];

		GeomFunc.radiancePathTracing(spheres, ray, seeds, 
			id2, id2 + 1, r);


		//int i = (height - y - 1) * width + x;
		int i = y * w + x;
		if (currentSample == 0) {
		    colors[3 * i + 0] = r[0];
		    colors[3 * i + 1] = r[1];
		    colors[3 * i + 2] = r[2];
		}
		else {
		    float k1 = currentSample;
		    float k2 = 1.0f / (k1 + 1.0f);
		    colors[3 * i + 0] = (colors[3 * i + 0] * k1 + r[0]) * k2;
		    colors[3 * i + 1] = (colors[3 * i + 1] * k1 + r[1]) * k2;
		    colors[3 * i + 2] = (colors[3 * i + 2] * k1 + r[2]) * k2;
		}
	    }
	}
    }


    private void renderCPU(Scene scene, float[] colors, int nrSamples,
	    int x, int y, int w, int h) {
	for (int i = 0; i < nrSamples; i++) {
	    renderCPU(scene, i, colors, x, y, w, h);
	}
    }


    private float[] renderLeaf(Scene scene, int nrSamples, 
	    int x, int y, int w, int h, boolean cpu){
	float[] colors = new float[w * h * 3];

        System.out.println("renderLeaf: x = " + x + ", y = " + y + ", w = " + w + ", h = " + h);

	if (cpu) {
	    renderCPU(scene, colors, nrSamples, x, y, w, h);
	}
	else {
	    renderGPU(scene, colors, nrSamples, x, y, w, h);
	}

	return colors;
    }


    private void set(float[] d, float[] s, int x, int y, int w, int h) {
	for (int j = 0; j < h; j++) {
	    for (int i = 0; i < w; i++) {
		int indexD = (y + j) * 2 * w + x + i;
		int indexS = j * w + i;

		d[3 * indexD + 0] = s[3 * indexS + 0];
		d[3 * indexD + 1] = s[3 * indexS + 1];
		d[3 * indexD + 2] = s[3 * indexS + 2];
	    }
	}
    }


    public float[] render(Scene scene, int nrSamples, 
	    int x, int y, int width, int height, boolean cpu) {
	if (width <= 1024 && height <= 512) {
	    return renderLeaf(scene, nrSamples, x, y, width, height, cpu);
	} else if (width <= 1024) {
	    int nh = height / 2;

	    float[] c1 = render(scene, nrSamples, x, y, width, nh, cpu);
	    float[] c2 = render(scene, nrSamples, x, y + nh, width, nh, cpu);
	    sync();

	    float[] colors = new float[3 * width * height];
	    set(colors, c1, 0, 0, width, nh);
	    set(colors, c2, 0, nh, width, nh);

	    return colors;
        } else if (height <= 512) {
	    int nw = width / 2;

	    float[] c1 = render(scene, nrSamples, x, y, nw, height, cpu);
	    float[] c2 = render(scene, nrSamples, x + nw, y, nw, height, cpu);
	    sync();

	    float[] colors = new float[3 * width * height];
	    set(colors, c1, 0, 0, nw, height);
	    set(colors, c2, nw, 0, nw, height);

	    return colors;
        } else {
	    int nw = width / 2;
	    int nh = height / 2;

	    float[] c1 = render(scene, nrSamples, x, y, nw, nh, cpu);
	    float[] c2 = render(scene, nrSamples, x + nw, y, nw, nh, cpu);
	    float[] c3 = render(scene, nrSamples, x, y + nh, nw, nh, cpu);
	    float[] c4 = render(scene, nrSamples, x + nw, y + nh, nw, nh, cpu);
	    sync();

	    float[] colors = new float[3 * width * height];
	    set(colors, c1, 0, 0, nw, nh);
	    set(colors, c2, nw, 0, nw, nh);
	    set(colors, c3, 0, nh, nw, nh);
	    set(colors, c4, nw, nh, nw, nh);

	    return colors;
	}
    }


    private static void write(float[] colors, int width, int height, boolean cpu) {
	write(colors, width, height, (cpu ? "cpu_" : "") + "image.ppm");
    }
 

    private static void write(float[] colors, int width, int height, 
	    String fileName) {
	try {
	    PrintStream out = new PrintStream(
                    new BufferedOutputStream(new FileOutputStream(fileName)));
	    out.printf("P3\n%d %d\n%d\n", width, height, 255);

	    for (int y = height - 1; y >= 0; y--) {
	    //for (int y = 0; y < height; y++) {
		for (int x = 0; x < width; x++) {
		    float r = colors[3 * (y * width + x) + 0];
		    float g = colors[3 * (y * width + x) + 1];
		    float b = colors[3 * (y * width + x) + 2];
		    out.printf("%d %d %d ",
			    V.toInt(r),
			    V.toInt(g),
			    V.toInt(b));
		    /*
		    out.printf("%.2f %.2f %.2f ", r, g, b);
			    */
		}
		out.println();
	    }
	}
	catch (FileNotFoundException e) {
	    System.out.println(e.getMessage());
	    System.exit(1);
	}
    }
 
    
    private static void start(Scene scene, int nrSamples, boolean cpu) {
	scene.exportObject();
	
	int width = scene.width;
	int height = scene.height;

	Raytracer raytracer = new Raytracer();

	long startTime = System.currentTimeMillis();
	MCTimer timer = MCCashmere.createOverallTimer();
	int event = timer.start();
	float[] colors = raytracer.render(scene, nrSamples, 
		0, 0, width, height, cpu);
	raytracer.sync();
	timer.stop(event);

	float elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0f;
	System.out.printf("Time %.3f s\n", elapsedTime); 

	System.out.printf("#samples per pixel: %d\n", nrSamples);

	float nrSamplesPerSec = ((long)nrSamples * width * height) / elapsedTime;
	System.out.printf("#samples/sec %.1fK\n", nrSamplesPerSec / 1000.0f);

	write(colors, width, height, cpu);
    }


    private static int[] initSeeds(int width, int height) {
	int nrPixels = width * height;
	int[] seeds = new int[nrPixels * 2];
	Random r = new Random(0);
	for (int i = 0; i < nrPixels * 2; i++) {
	    seeds[i] = r.nextInt(Integer.MAX_VALUE);
	    if (seeds[i] < 2) seeds[i] = 2;
	}
	return seeds;
    }
  
 
    private static void usage() {
	System.out.println("Usage: java Raytracer [<width> <height> <scene> <nrSamples> cpu|gpu]");
    }


    private static void check(String read, String expected) {
	if (!read.equals(expected)) {
	    System.out.println("expected " + expected + " while reading input");
	    System.exit(1);
	}
    }


    private static Scene readScene(String fileName) {
	Scene scene = null;
	try { 
	    Scanner s = new Scanner(new File(fileName));
	    check(s.next(), "camera");
	    Camera camera = new Camera(
		    new float[] {s.nextFloat(), s.nextFloat(), s.nextFloat()},
			// orig
		    new float[] {s.nextFloat(), s.nextFloat(), s.nextFloat()});
			// target
	    check(s.next(), "size");
	    int nrSpheres = s.nextInt();
	    Sphere[] spheres = new Sphere[nrSpheres];

	    for (int i = 0; i < nrSpheres; i++) {
		check(s.next(), "sphere");
		spheres[i] = new Sphere(
			s.nextFloat(), // radius
			new float[] 
			{s.nextFloat(), s.nextFloat(), s.nextFloat()}, 
			// position
			new float[] 
			{s.nextFloat(), s.nextFloat(), s.nextFloat()}, 
			// emission
			new float[] 
			{s.nextFloat(), s.nextFloat(), s.nextFloat()}, 
			// color
			s.nextInt()); // reflection type
	    }
	    scene = new Scene(camera, spheres);
	}
	catch (FileNotFoundException e) {
	    System.out.println(e.getMessage());
	    System.exit(1);
	}
	return scene;
    }


    public static void main(String[] argv) {
	Scene scene = null;
	int width = 640;
	int height = 480;
	boolean cpu = false;
	int nrSamples = 10;
	if (argv.length == 5) {
	    width = new Integer(argv[0]);
	    height = new Integer(argv[1]);
	    scene = readScene(argv[2]);
	    nrSamples = new Integer(argv[3]);
	    cpu = argv[4].equals("cpu") ? true : false;
	}
	else if (argv.length == 0) {
	    scene = readScene("scenes/cornell.scn");
	}
	else {
	    for (int i = 0; i < argv.length; i++) {
		System.out.printf("%d: %s\n", i, argv[i]);
	    }
	    usage();
	    System.exit(1);
	}
	scene.update(width, height);
	scene.addSeeds(initSeeds(width, height));

	MCCashmere.initialize();

	start(scene, nrSamples, cpu);
    }
}
