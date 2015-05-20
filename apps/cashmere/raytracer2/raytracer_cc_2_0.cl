// cc_2_0
typedef struct __attribute__ ((packed)) {
    float x;
    float y;
    float z;
} Vec;


typedef struct __attribute__ ((packed)) {
    Vec origin;
    Vec direction;
} Ray;


typedef struct __attribute__ ((packed)) {
    float radius;
    Vec position;
    Vec emission;
    Vec color;
    float reflectionType;
} Sphere;


typedef struct __attribute__ ((packed)) {
    Vec orig;
    Vec target;
    Vec direction;
    Vec x;
    Vec y;
} Camera;


#define EPSILON 0.01


#define FLOAT_PI 3.14159265358979323846


#define DIFF 0.0


#define SPEC 1.0






 void vinit2m (__global Vec* v, float a, float b, float c) {


    


    v->x = a;
    v->y = b;
    v->z = c;
}


 void vassign2m (__global Vec* v, const Vec* a) {


    


    vinit2m(v, a->x, a->y, a->z);
}


 void vinit ( Vec* v, float a, float b, float c) {


    


    v->x = a;
    v->y = b;
    v->z = c;
}


 void vassignfm ( Vec* v, __global const Vec* a) {


    


    vinit(v, a->x, a->y, a->z);
}


 void vassign ( Vec* v, const Vec* a) {


    


    vinit(v, a->x, a->y, a->z);
}


 void vclr ( Vec* a) {


    


    vinit(a, 0.0, 0.0, 0.0);
}


 void vadd ( Vec* v, const Vec* a, const Vec* b) {


    


    vinit(v, a->x + b->x, a->y + b->y, a->z + b->z);
}


 void vsub ( Vec* v, const Vec* a, const Vec* b) {


    


    vinit(v, a->x - b->x, a->y - b->y, a->z - b->z);
}


 void vmul ( Vec* v, const Vec* a, const Vec* b) {


    


    vinit(v, a->x * b->x, a->y * b->y, a->z * b->z);
}


 void vsmul ( Vec* v, float a, const Vec* b) {


    


    vinit(v, b->x * a, b->y * a, b->z * a);
}


 float vdot (const Vec* a, const Vec* b) {


    


    return a->x * b->x + a->y * b->y + a->z * b->z;
}


 void vnorm ( Vec* v) {


    


    const float l = 1.0 / sqrt(vdot(v, v));
    vsmul(v, l, v);
}


 void vxcross ( Vec* v, const Vec* a, const Vec* b) {


    


    vinit(v, a->y * b->z - a->z * b->y, a->z * b->x - a->x * b->z, a->x * b->y 
            - a->y * b->x);
}


 bool viszero (const Vec* v) {


    


    return v->x == 0.0 && v->y == 0.0 && v->z == 0.0;
}


 void rinit ( Ray* r, const Vec* origin, const Vec* direction) {


    


    vassign(&r->origin, origin);
    vassign(&r->direction, direction);
}


 void rassign ( Ray* a, const Ray* b) {


    


    vassign(&a->origin, &b->origin);
    vassign(&a->direction, &b->direction);
}


 float getRandom ( uint* seed0,  uint* seed1) {


    *seed0 = 36969 * (*seed0 & 65535) + (*seed0 >> 16);
    *seed1 = 18000 * (*seed1 & 65535) + (*seed1 >> 16);
    const uint ires = (*seed0 << 16) + *seed1;
    union {
	float f;
	uint ui;
    } res;
    res.ui = (ires & 0x007fffff) | 0x40000000;
    return (res.f - 2.0f) / 2.0f;
}


 float sphereIntersect (__global const Sphere* s, const Ray* r) {


    


    Vec op;
    Vec sPos;
    vassignfm(&sPos, &s->position);
    vsub(&op, &sPos, &r->origin);
    const float b = vdot(&op, &r->direction);
    float det = b * b - vdot(&op, &op) + s->radius * s->radius;
    if (det < 0.0) {
    
        return 0.0;
    } else {
    
        det = sqrt(det);
    }
    float t = b - det;
    if (t > 0.01) {
    
        return t;
    } else {
    
        t = b + det;
        if (t > 0.01) {
        
            return t;
        } else {
        
            return 0.0;
        }
    }
}


 bool intersect (int nrSpheres, __global const Sphere* spheres, const Ray* r,  
         float* t,  int* id) {


    


    *t = 1E+20;
    const float inf = *t;
    for (int i = nrSpheres - 1; i >= 0; i--) {
    
        const float d = sphereIntersect(&spheres[i], r);
        if (d != 0.0 && d < *t) {
        
            *t = d;
            *id = i;
        }
    }
    return *t < inf;
}


 bool intersectP (int nrSpheres, __global const Sphere* spheres, const Ray* r, 
         float maxt) {


    


    for (int i = nrSpheres - 1; i >= 0; i--) {
    
        const float d = sphereIntersect(&spheres[i], r);
        if (d != 0.0 && d < maxt) {
        
            return true;
        }
    }
    return false;
}


 void uniformSampleSphere (float u1, float u2,  Vec* v) {


    


    const float zz = 1.0 - 2.0 * u1;
    const float r = sqrt(max(0.0, 1.0 - (1.0 - 2.0 * u1) * (1.0 - 2.0 * u1)));
    const float phi = 6.283185307179586476920 * u2;
    const float xx = r * cos(6.283185307179586476920 * u2);
    const float yy = r * sin(6.283185307179586476920 * u2);
    vinit(v, xx, yy, 1.0 - 2.0 * u1);
}


 void sampleLights (int nrSpheres, __global const Sphere* spheres,  uint* 
         seed0,  uint* seed1, const Vec* hitPoint, const Vec* normal,  Vec* 
         result) {


    


    vclr(result);
    for (int i = 0; i < nrSpheres; i++) {
    
        Vec sphereEmission;
        vassignfm(&sphereEmission, &spheres[i].emission);
        if (!viszero(&sphereEmission)) {
        
            Ray shadowRay;
            vassign(&shadowRay.origin, hitPoint);
            Vec unitSpherePoint;
            const float u1 = getRandom(seed0, seed1);
            const float u2 = getRandom(seed0, seed1);
            uniformSampleSphere(u1, u2, &unitSpherePoint);
            Vec spherePoint;
            vsmul(&spherePoint, spheres[i].radius, &unitSpherePoint);
            Vec spherePosition;
            vassignfm(&spherePosition, &spheres[i].position);
            vadd(&spherePoint, &spherePoint, &spherePosition);
            vsub(&shadowRay.direction, &spherePoint, hitPoint);
            const float len = sqrt(vdot(&shadowRay.direction, &shadowRay.
                    direction));
            vsmul(&shadowRay.direction, 1.0 / len, &shadowRay.direction);
            float wo = vdot(&shadowRay.direction, &unitSpherePoint);
            if (wo <= 0.0) {
            
                wo = -wo;
                const float wi = vdot(&shadowRay.direction, normal);
                if (wi > 0.0 && !intersectP(nrSpheres, spheres, &shadowRay, 
                        len - 0.01)) {
                
                    const float s = 12.566370614359172953840 * 
                            spheres[i].radius * spheres[i].radius * wi * wo / 
                            (len * len);
                    vsmul(&sphereEmission, 12.566370614359172953840 * 
                            spheres[i].radius * spheres[i].radius * wi * wo / 
                            (len * len), &sphereEmission);
                    vadd(result, result, &sphereEmission);
                }
            }
        }
    }
}


 int radiancePathTracing (int nrSpheres, __global const Sphere* spheres, const 
         Ray* startRay,  uint* seed0,  uint* seed1,  Vec* result) {


    


    Ray currentRay;
    rassign(&currentRay, startRay);
    Vec rad;
    vinit(&rad, 0.0, 0.0, 0.0);
    Vec throughput;
    vinit(&throughput, 1.0, 1.0, 1.0);
    bool specularBounce = true;
    for (int depth = 0; true; depth++) {
    
        if (depth > 6) {
        
            vassign(result, &rad);
            return -1;
        }
        float t;
        int id = 0;
        if (!intersect(nrSpheres, spheres, &currentRay, &t, &id)) {
        
            vassign(result, &rad);
            return -1;
        }
        Vec hitPoint;
        vsmul(&hitPoint, t, &currentRay.direction);
        vadd(&hitPoint, &currentRay.origin, &hitPoint);
        Vec normal;
        Vec spherePosition;
        vassignfm(&spherePosition, &spheres[id].position);
        vsub(&normal, &hitPoint, &spherePosition);
        vnorm(&normal);
        const float dp = vdot(&normal, &currentRay.direction);
        Vec nl;
        const float invSignDP = -1.0 * sign(dp);
        vsmul(&nl, invSignDP, &normal);
        Vec eCol;
        Vec sphereEmission;
        vassignfm(&sphereEmission, &spheres[id].emission);
        Vec sphereColor;
        vassignfm(&sphereColor, &spheres[id].color);
        vassign(&eCol, &sphereEmission);
        if (!viszero(&eCol)) {
        
            if (specularBounce) {
            
                vsmul(&eCol, fabs(dp), &eCol);
                vmul(&eCol, &throughput, &eCol);
                vadd(&rad, &rad, &eCol);
            }
            vassign(result, &rad);
            return -1;
        }
        if (spheres[id].reflectionType == 0.0) {
        
            specularBounce = false;
            vmul(&throughput, &throughput, &sphereColor);
            Vec ld;
            sampleLights(nrSpheres, spheres, seed0, seed1, &hitPoint, &nl, 
                    &ld);
            vmul(&ld, &throughput, &ld);
            vadd(&rad, &rad, &ld);
            const float r1 = 6.283185307179586476920 * getRandom(seed0, seed1);
            const float r2 = getRandom(seed0, seed1);
            const float r2s = sqrt(r2);
            Vec w;
            vassign(&w, &nl);
            Vec u;
            Vec a;
            if (fabs(w.x) > 0.1) {
            
                vinit(&a, 0.0, 1.0, 0.0);
            } else {
            
                vinit(&a, 1.0, 0.0, 0.0);
            }
            vxcross(&u, &a, &w);
            vnorm(&u);
            Vec v;
            vxcross(&v, &w, &u);
            Vec newDir;
            vsmul(&u, cos(r1) * r2s, &u);
            vsmul(&v, sin(r1) * r2s, &v);
            vadd(&newDir, &u, &v);
            vsmul(&w, sqrt(1 - r2), &w);
            vadd(&newDir, &newDir, &w);
            vassign(&currentRay.origin, &hitPoint);
            vassign(&currentRay.direction, &newDir);
        } else if (spheres[id].reflectionType == 1.0) {
        
            specularBounce = true;
            Vec newDir;
            vsmul(&newDir, 2.0 * vdot(&normal, &currentRay.direction), 
                    &normal);
            vsub(&newDir, &currentRay.direction, &newDir);
            vmul(&throughput, &throughput, &sphereColor);
            rinit(&currentRay, &hitPoint, &newDir);
        } else {
        
            specularBounce = true;
            Vec newDir;
            vsmul(&newDir, 2.0 * vdot(&normal, &currentRay.direction), 
                    &normal);
            vsub(&newDir, &currentRay.direction, &newDir);
            Ray reflRay;
            rinit(&reflRay, &hitPoint, &newDir);
            const bool into = vdot(&normal, &nl) > 0;
            const float nc = 1.0;
            const float nt = 1.5;
            float nnt = 0.0;
            if (into) {
            
                nnt = 0.6666666667;
            } else {
            
                nnt = 1.5;
            }
            const float ddn = vdot(&currentRay.direction, &nl);
            const float cos2t = 1.0 - nnt * nnt * (1.0 - ddn * ddn);
            if (1.0 - nnt * nnt * (1.0 - ddn * ddn) < 0.0) {
            
                vmul(&throughput, &throughput, &sphereColor);
                rassign(&currentRay, &reflRay);
            } else {
            
                float kk = 0.0;
                if (into) {
                
                    kk = 1.0;
                } else {
                
                    kk = -1.0;
                }
                kk = kk * (ddn * nnt + sqrt(1.0 - nnt * nnt * (1.0 - ddn * 
                        ddn)));
                Vec nkk;
                vsmul(&nkk, kk, &normal);
                Vec transDir;
                vsmul(&transDir, nnt, &currentRay.direction);
                vsub(&transDir, &transDir, &nkk);
                vnorm(&transDir);
                const float a = 1.5 - 1.0;
                const float b = 2.5;
                const float r0 = (1.5 - 1.0) * (1.5 - 1.0) / 6.25;
                float c = 1.0;
                if (into) {
                
                    c = c - -ddn;
                } else {
                
                    c = c - vdot(&transDir, &normal);
                }
                const float re = (1.5 - 1.0) * (1.5 - 1.0) / 6.25 + (1.0 - 
                        (1.5 - 1.0) * (1.5 - 1.0) / 6.25) * c * c * c * c * c;
                const float tr = 1.0 - ((1.5 - 1.0) * (1.5 - 1.0) / 6.25 + 
                        (1.0 - (1.5 - 1.0) * (1.5 - 1.0) / 6.25) * c * c * c * 
                        c * c);
                const float p = 0.25 + 0.5 * ((1.5 - 1.0) * (1.5 - 1.0) / 6.25 
                        + (1.0 - (1.5 - 1.0) * (1.5 - 1.0) / 6.25) * c * c * c 
                        * c * c);
                const float rp = ((1.5 - 1.0) * (1.5 - 1.0) / 6.25 + (1.0 - 
                        (1.5 - 1.0) * (1.5 - 1.0) / 6.25) * c * c * c * c * c) 
                        / (0.25 + 0.5 * ((1.5 - 1.0) * (1.5 - 1.0) / 6.25 + 
                        (1.0 - (1.5 - 1.0) * (1.5 - 1.0) / 6.25) * c * c * c * 
                        c * c));
                const float tp = (1.0 - ((1.5 - 1.0) * (1.5 - 1.0) / 6.25 + 
                        (1.0 - (1.5 - 1.0) * (1.5 - 1.0) / 6.25) * c * c * c * 
                        c * c)) / (1.0 - (0.25 + 0.5 * ((1.5 - 1.0) * (1.5 - 
                        1.0) / 6.25 + (1.0 - (1.5 - 1.0) * (1.5 - 1.0) / 6.25) 
                        * c * c * c * c * c)));
                if (getRandom(seed0, seed1) < 0.25 + 0.5 * ((1.5 - 1.0) * (1.5 
                        - 1.0) / 6.25 + (1.0 - (1.5 - 1.0) * (1.5 - 1.0) / 
                        6.25) * c * c * c * c * c)) {
                
                    vsmul(&throughput, ((1.5 - 1.0) * (1.5 - 1.0) / 6.25 + 
                            (1.0 - (1.5 - 1.0) * (1.5 - 1.0) / 6.25) * c * c * 
                            c * c * c) / (0.25 + 0.5 * ((1.5 - 1.0) * (1.5 - 
                            1.0) / 6.25 + (1.0 - (1.5 - 1.0) * (1.5 - 1.0) / 
                            6.25) * c * c * c * c * c)), &throughput);
                    vmul(&throughput, &throughput, &sphereColor);
                    rassign(&currentRay, &reflRay);
                } else {
                
                    vsmul(&throughput, (1.0 - ((1.5 - 1.0) * (1.5 - 1.0) / 
                            6.25 + (1.0 - (1.5 - 1.0) * (1.5 - 1.0) / 6.25) * 
                            c * c * c * c * c)) / (1.0 - (0.25 + 0.5 * ((1.5 - 
                            1.0) * (1.5 - 1.0) / 6.25 + (1.0 - (1.5 - 1.0) * 
                            (1.5 - 1.0) / 6.25) * c * c * c * c * c))), 
                            &throughput);
                    vmul(&throughput, &throughput, &sphereColor);
                    rinit(&currentRay, &hitPoint, &transDir);
                }
            }
        }
    }
}


 void generateCameraRay (__global const Camera* camera,  uint* seed0,  uint* 
         seed1, int width, int height, int x, int y,  Ray* ray) {


    


    const float invWidth = 1.0 / width;
    const float invHeight = 1.0 / height;
    const float r1 = getRandom(seed0, seed1) - 0.5;
    const float r2 = getRandom(seed0, seed1) - 0.5;
    const float kcx = (x + r1) * (1.0 / width) - 0.5;
    const float kcy = (y + r2) * (1.0 / height) - 0.5;
    Vec rdir;
    vinit(&rdir, camera->x.x * ((x + r1) * (1.0 / width) - 0.5) + camera->y.x 
            * ((y + r2) * (1.0 / height) - 0.5) + camera->direction.x, 
            camera->x.y * ((x + r1) * (1.0 / width) - 0.5) + camera->y.y * ((y 
            + r2) * (1.0 / height) - 0.5) + camera->direction.y, camera->x.z * 
            ((x + r1) * (1.0 / width) - 0.5) + camera->y.z * ((y + r2) * (1.0 
            / height) - 0.5) + camera->direction.z);
    Vec rorig;
    vsmul(&rorig, 0.1, &rdir);
    Vec corig;
    vassignfm(&corig, &camera->orig);
    vadd(&rorig, &rorig, &corig);
    vnorm(&rdir);
    rinit(ray, &rorig, &rdir);
}


__kernel void radianceKernel (int h, int w, __global Vec* colors, int height, 
        int width, __global uint* seeds, int nrSpheres, __global const Sphere* 
        spheres, __global const Camera* camera, int xOffset, int yOffset, int 
        currentSample) {


    const int ttx = get_local_id(0);
    const int wtx = get_local_id(1);
    const int bx = get_group_id(0);
    const int y = get_group_id(1);
    


    const int nrThreadsNrThreadsW = 32;
    const int nrWarpsNrThreadsW = 4;
    const int tx = 32 * wtx + ttx;
    const int x = 128 * bx + (32 * wtx + ttx);
    const int realY = y + yOffset;
    const int realX = 128 * bx + (32 * wtx + ttx) + xOffset;
    if (y < h) {
    
        uint seed0 = seeds[(y + yOffset) * (2 * width) + 2 * (128 * bx + (32 * 
                wtx + ttx) + xOffset)];
        uint seed1 = seeds[(y + yOffset) * (2 * width) + 2 * (128 * bx + (32 * 
                wtx + ttx) + xOffset) + 1];
        Ray ray;
        generateCameraRay(camera, &seed0, &seed1, width, height, 128 * bx + 
                (32 * wtx + ttx) + xOffset, y + yOffset, &ray);
        Vec r;
        radiancePathTracing(nrSpheres, spheres, &ray, &seed0, &seed1, &r);
        if (currentSample == 0) {
        
            vassign2m(&colors[128 * bx + (32 * wtx + ttx) + y * w], &r);
        } else {
        
            const float k1 = currentSample;
            const float k2 = 1.0 / (currentSample + 1.0);
            colors[128 * bx + (32 * wtx + ttx) + y * w].x = (colors[128 * bx + 
                    (32 * wtx + ttx) + y * w].x * currentSample + r.x) * (1.0 
                    / (currentSample + 1.0));
            colors[128 * bx + (32 * wtx + ttx) + y * w].y = (colors[128 * bx + 
                    (32 * wtx + ttx) + y * w].y * currentSample + r.y) * (1.0 
                    / (currentSample + 1.0));
            colors[128 * bx + (32 * wtx + ttx) + y * w].z = (colors[128 * bx + 
                    (32 * wtx + ttx) + y * w].z * currentSample + r.z) * (1.0 
                    / (currentSample + 1.0));
        }
        seeds[(y + yOffset) * (2 * width) + 2 * (128 * bx + (32 * wtx + ttx) + 
                xOffset)] = seed0;
        seeds[(y + yOffset) * (2 * width) + 2 * (128 * bx + (32 * wtx + ttx) + 
                xOffset) + 1] = seed1;
    }
}





