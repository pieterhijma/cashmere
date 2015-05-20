class GeomFunc {



    static final float EPSILON = 0.01f;
    static final float FLOAT_PI = (float) Math.PI;


    static float sphereIntersect(Sphere s, Ray r) { 
	/* returns distance, 0 if nohit */

	float[] op = new float[3];
	/* Solve t^2*d.d + 2*t*(o-p).d + (o-p).(o-p)-R^2 = 0 */
	V.sub(op, s.position, r.origin);

	float b = V.dot(op, r.direction);
	float det = b * b - V.dot(op, op) + s.radius * s.radius;
	if (det < 0.f)
	    return 0.f;
	else
	    det = (float) Math.sqrt(det);

	float t = b - det;
	if (t > EPSILON)
	    return t;
	else {
	    t = b + det;

	    if (t > EPSILON)
		return t;
	    else
		return 0.f;
	}
    }


    static void uniformSampleSphere(float u1,  float u2, float[] v) {
	float zz = 1.f - 2.f * u1;
	float r = (float) Math.sqrt(Math.max(0.f, 1.f - zz * zz));
	float phi = 2.f * FLOAT_PI * u2;
	float xx = r * (float) Math.cos(phi);
	float yy = r * (float) Math.sin(phi);

	V.init(v, xx, yy, zz);
    }


    static boolean intersect(Sphere[] spheres, Ray r, float[] t, int[] id) {
	float inf = t[0] = 1e20f;

	for (int i = spheres.length - 1; i >= 0; i--) {
	    float d = sphereIntersect(spheres[i], r);
	    if ((d != 0.0f) && (d < t[0])) {
		t[0] = d;
		id[0] = i;
	    }
	}

	return t[0] < inf;
    }


    static boolean intersectP(Sphere[] spheres, Ray r, float maxt) {
	for (int i = spheres.length - 1; i >= 0; i--) {
	    float d = sphereIntersect(spheres[i], r);
	    if ((d != 0.f) && (d < maxt))
		return true;
	}

	return false;
    }


    static void sampleLights(Sphere[] spheres, int[] seeds, int i1, int i2, 
	    float[] hitPoint, float[] normal, float[] result) {

	V.clr(result);

	/* For each light */
	for (int i = 0; i < spheres.length; i++) {
	    Sphere light = spheres[i];
	    if (!V.iszero(light.emission)) {
		/* It is a light source */
		Ray shadowRay = new Ray();

		// should be a V.assign
		V.assign(shadowRay.origin, hitPoint);

		/* Choose a point over the light source */
		float[] unitSpherePoint = new float[3];

		// fix the evaluation order
		float u1 = R.getRandom(seeds, i1, i2);
		float u2 = R.getRandom(seeds, i1, i2);
		uniformSampleSphere(u1, u2, unitSpherePoint);
		float[] spherePoint = new float[3];
		V.smul(spherePoint, light.radius, unitSpherePoint);
		V.add(spherePoint, spherePoint, light.position);

		/* Build the shadow ray direction */
		V.sub(shadowRay.direction, spherePoint, hitPoint);
		float len = (float) Math.sqrt(V.dot(shadowRay.direction, 
			    shadowRay.direction));
		V.smul(shadowRay.direction, 1.f / len, shadowRay.direction);

		float wo = V.dot(shadowRay.direction, unitSpherePoint);
		if (wo > 0.f) {
		    /* It is on the other half of the sphere */
		    continue;
		} 
		else {
		    wo = -wo;
		}

		/* Check if the light is visible */
		float wi = V.dot(shadowRay.direction, normal);
		if ((wi > 0.f) && (!intersectP(spheres, shadowRay, 
				len - EPSILON))) {
		    float[] c = new float[3]; 
		    V.assign(c, light.emission);

		    float s = (4.f * FLOAT_PI * light.radius * light.radius) * 
			wi * wo / (len *len);

		    V.smul(c, s, c);
		    V.add(result, result, c);
		}
	    }
	}
    }


    static void radiancePathTracing(Sphere[] spheres, Ray startRay, 
	    int[] seeds, int i1, int i2, float[] result) {

	Ray currentRay = new Ray(); 
	currentRay.assign(startRay);

	float[] rad = new float[3]; 
	V.init(rad, 0.f, 0.f, 0.f);

	float[] throughput = new float[3]; 
	V.init(throughput, 1.f, 1.f, 1.f);

	boolean specularBounce = true;
	for (int depth = 0; true; ++depth) {
	    // Removed Russian Roulette in order to improve execution on SIMT
	    if (depth > 6) {
		V.assign(result, rad);
		return;
	    }

	    float[] t = new float[1]; /* distance to intersection */
	    int[] id = new int[1]; /* id of intersected object */
	    if (!intersect(spheres, currentRay, t, id)) {
		/* if miss, return */
		V.assign(result, rad);
		return;
	    }

	    Sphere obj = spheres[id[0]]; /* the hit object */

	    float[] hitPoint = new float[3];
	    V.smul(hitPoint, t[0], currentRay.direction);
	    V.add(hitPoint, currentRay.origin, hitPoint);

	    float[] normal = new float[3];
	    V.sub(normal, hitPoint, obj.position);
	    V.norm(normal);

	    float dp = V.dot(normal, currentRay.direction);

	    float[] nl = new float[3];
	    // SIMT optimization
	    float invSignDP = -1.f * V.sign(dp);
	    V.smul(nl, invSignDP, normal);

	    /* Add emitted light */
	    float[] eCol = new float[3]; 
	    V.assign(eCol, obj.emission);

	    if (!V.iszero(eCol)) {
		if (specularBounce) {
		    V.smul(eCol, Math.abs(dp), eCol);
		    V.mul(eCol, throughput, eCol);
		    V.add(rad, rad, eCol);
		}

		V.assign(result, rad);
		return;
	    }

	    if (obj.reflectionType == Sphere.DIFF) { 
		/* Ideal Sphere.DIFFUSE reflection */
		specularBounce = false;
		V.mul(throughput, throughput, obj.color);

		/* Direct lighting component */

		float[] ld = new float[3]; 
		sampleLights(spheres, seeds, i1, i2, hitPoint, nl, ld);
		V.mul(ld, throughput, ld);
		V.add(rad, rad, ld);

		/* Diffuse component */

		float r1 = 2.f * FLOAT_PI * R.getRandom(seeds, i1, i2);
		float r2 = R.getRandom(seeds, i1, i2);
		float r2s = (float) Math.sqrt(r2);

		float[] w = new float[3]; 
		V.assign(w, nl);

		float[] u = new float[3]; 
		float[] a = new float[3];
		if (Math.abs(w[0]) > .1f) {
		    V.init(a, 0.f, 1.f, 0.f);
		} else {
		    V.init(a, 1.f, 0.f, 0.f);
		}
		V.xcross(u, a, w);
		V.norm(u);

		float[] v = new float[3];
		V.xcross(v, w, u);

		float[] newDir = new float[3];
		V.smul(u, (float) Math.cos(r1) * r2s, u);
		V.smul(v, (float) Math.sin(r1) * r2s, v);
		V.add(newDir, u, v);
		V.smul(w, (float) Math.sqrt(1 - r2), w);
		V.add(newDir, newDir, w);

		V.assign(currentRay.origin, hitPoint);
		V.assign(currentRay.direction, newDir);
		continue;
	    } else if (obj.reflectionType == Sphere.SPEC) { 
		/* Ideal SPECULAR reflection */
		specularBounce = true;

		float[] newDir = new float[3];
		V.smul(newDir, 2.f * V.dot(normal, currentRay.direction), 
			normal);
		V.sub(newDir, currentRay.direction, newDir);

		V.mul(throughput, throughput, obj.color);

		currentRay.init(hitPoint, newDir);
		continue;
	    } else {
		specularBounce = true;

		float[] newDir = new float[3];
		V.smul(newDir, 2.f * V.dot(normal, currentRay.direction), 
			normal);
		V.sub(newDir, currentRay.direction, newDir);

		Ray reflRay = new Ray(); 
		reflRay.init(hitPoint, newDir); 
		    /* Ideal dielectric REFRACTION */

		boolean into = (V.dot(normal, nl) > 0); 
		    /* Ray from outside going in? */

		float nc = 1.f;
		float nt = 1.5f;
		float nnt = into ? nc / nt : nt / nc;
		float ddn = V.dot(currentRay.direction, nl);
		float cos2t = 1.f - nnt * nnt * (1.f - ddn * ddn);

		if (cos2t < 0.f)  { /* Total internal reflection */
		    V.mul(throughput, throughput, obj.color);

		    currentRay.assign(reflRay);
		    continue;
		}

		float kk = (into ? 1 : -1) * 
		    (ddn * nnt + (float) Math.sqrt(cos2t));
		float[] nkk = new float[3];
		V.smul(nkk, kk, normal);
		float[] transDir = new float[3];
		V.smul(transDir, nnt, currentRay.direction);
		V.sub(transDir, transDir, nkk);
		V.norm(transDir);

		float a = nt - nc;
		float b = nt + nc;
		float R0 = a * a / (b * b);
		float c = 1 - (into ? -ddn : V.dot(transDir, normal));

		float Re = R0 + (1 - R0) * c * c * c * c*c;
		float Tr = 1.f - Re;
		float P = .25f + .5f * Re;
		float RP = Re / P;
		float TP = Tr / (1.f - P);

		if (R.getRandom(seeds, i1, i2) < P) { /* R. */
		    V.smul(throughput, RP, throughput);
		    V.mul(throughput, throughput, obj.color);

		    currentRay.assign(reflRay);
		    continue;
		} else {
		    V.smul(throughput, TP, throughput);
		    V.mul(throughput, throughput, obj.color);

		    currentRay.init(hitPoint, transDir);
		    continue;
		}
	    }
	}
    }
}
