/* $Id: Plummer.java 6051 2007-08-08 15:25:59Z rob $ */

//import java.util.*;
/**
 * see ../../rmi/nbody/Plummer.java for the original version
 */

class Plummer {
    private RandomNumber rand;

    Plummer() {
        rand = new RandomNumber();
        rand.setSeed(123);
    }

    public void generate(float[] mass, float[] positions, float[] velocities) {

        int numBodies = mass.length;

        int i, hNumBodies;
        float offset = 4.0f;
        float m = 1.0f/numBodies;
        double rsc, vsc, r, v, x, y;
        double cmr_x = 0.0, cmr_y = 0.0, cmr_z = 0.0;
        double cmv_x = 0.0, cmv_y = 0.0, cmv_z = 0.0;
        double[] shell;

        rsc = 9.0 * Math.PI / 16.0;
        vsc = Math.sqrt(1.0 / rsc);

        hNumBodies = (numBodies + 1) >> 1;

        for (i = 0; i < hNumBodies; i++) {

            do {
                r = 1 / Math
                    .sqrt(Math.pow(rand.xRand(0.0, 0.999), -2.0 / 3.0) - 1);
            } while (r > 9.0);

            shell = rand.pickShell(rsc * r);
            positions[3*i] = (float) shell[0];
            positions[3*i+1] = (float) shell[1];
            positions[3*i+2] = (float) shell[2];

            cmr_x += shell[0];
            cmr_y += shell[1];
            cmr_z += shell[2];

            do {
                x = rand.xRand(0.0, 1.0);
                y = rand.xRand(0.0, 0.1);
            } while (y > x * x * Math.pow(1 - x * x, 3.5));

            v = Math.sqrt(2.0) * x / Math.pow(1 + r * r, 0.25);

            shell = rand.pickShell(vsc * v);

            velocities[3*i] = (float) shell[0];
            velocities[3*i+1] = (float) shell[1];
            velocities[3*i+2] = (float) shell[2];

            cmv_x += shell[0];
            cmv_y += shell[1];
            cmv_z += shell[2];
        }

        for (i = hNumBodies; i < numBodies; i++) {
            positions[3*i] = positions[(i-hNumBodies)*3] + offset;

            cmr_x += positions[3*i];
            cmr_y += positions[3*i+1];
            cmr_z += positions[3*i+2];

            velocities[3*i] = velocities[(i-hNumBodies)*3];

            cmv_x += velocities[3*i];
            cmv_y += velocities[3*i+1];
            cmv_z += velocities[3*i+2];

            positions[3*i+1] = positions[(i-hNumBodies)*3+1] + offset;

            cmr_x += positions[3*i];
            cmr_y += positions[3*i+1];
            cmr_z += positions[3*i+2];

            velocities[3*i+1] = velocities[(i-hNumBodies)*3+1];

            cmv_x += velocities[3*i];
            cmv_y += velocities[3*i+1];
            cmv_z += velocities[3*i+2];

            positions[3*i+2] = positions[(i-hNumBodies)*3+2] + offset;

            cmr_x += positions[3*i];
            cmr_y += positions[3*i+1];
            cmr_z += positions[3*i+2];

            velocities[3*i+2] = velocities[(i-hNumBodies)*3+2];

            cmv_x += velocities[3*i];
            cmv_y += velocities[3*i+1];
            cmv_z += velocities[3*i+2];
        }

        cmr_x /= (double) (numBodies);
        cmr_y /= (double) (numBodies);
        cmr_z /= (double) (numBodies);

        cmv_x /= (double) (numBodies);
        cmv_y /= (double) (numBodies);
        cmv_z /= (double) (numBodies);

        for (i = 0; i < numBodies; i++) {

            positions[3*i] -= cmr_x;
            positions[3*i+1] -= cmr_y;
            positions[3*i+2] -= cmr_z;

            velocities[3*i] -= cmv_x;
            velocities[3*i+1] -= cmv_y;
            velocities[3*i+2] -= cmv_z;

            mass[i] = m;
        }
    }
}
