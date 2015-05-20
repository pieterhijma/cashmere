// hd7970




__kernel void computeAccelerationKernel (int start, int nCompute, int nBodies, 
        __global const float* positions, __global const float* mass, __global 
        float* accel, float softsq) {


    const int wtbody = get_local_id(0);
    const int wtbody0 = get_local_id(1);
    const int bbody = get_group_id(0);
    


    const int nrWorkitemsNrThreadsNCompute = 64;
    const int nrWavefrontsNrThreadsNCompute = 4;
    const int tbody = 64 * wtbody0 + wtbody;
    const int body = 256 * bbody + (64 * wtbody0 + wtbody);
    const int bodyOffset = 256 * bbody + (64 * wtbody0 + wtbody) + start;
    if (256 * bbody + (64 * wtbody0 + wtbody) + start < nBodies) {
    
        float acc0 = 0.0;
        float acc1 = 0.0;
        float acc2 = 0.0;
        const float pos0 = positions[3 * (256 * bbody + (64 * wtbody0 + 
                wtbody) + start)];
        const float pos1 = positions[3 * (256 * bbody + (64 * wtbody0 + 
                wtbody) + start) + 1];
        const float pos2 = positions[3 * (256 * bbody + (64 * wtbody0 + 
                wtbody) + start) + 2];
        for (int i = 0; i < nBodies; i++) {
        
            const float diff_x = positions[3 * i] - pos0;
            const float diff_y = positions[3 * i + 1] - pos1;
            const float diff_z = positions[3 * i + 2] - pos2;
            const float distsq = (positions[3 * i] - pos0) * (positions[3 * i] 
                    - pos0) + (positions[3 * i + 1] - pos1) * (positions[3 * i 
                    + 1] - pos1) + (positions[3 * i + 2] - pos2) * 
                    (positions[3 * i + 2] - pos2) + softsq;
            const float factor = mass[i] / (((positions[3 * i] - pos0) * 
                    (positions[3 * i] - pos0) + (positions[3 * i + 1] - pos1) 
                    * (positions[3 * i + 1] - pos1) + (positions[3 * i + 2] - 
                    pos2) * (positions[3 * i + 2] - pos2) + softsq) * 
                    sqrt((positions[3 * i] - pos0) * (positions[3 * i] - pos0) 
                    + (positions[3 * i + 1] - pos1) * (positions[3 * i + 1] - 
                    pos1) + (positions[3 * i + 2] - pos2) * (positions[3 * i + 
                    2] - pos2) + softsq));
            acc0 = acc0 + (positions[3 * i] - pos0) * factor;
            acc1 = acc1 + (positions[3 * i + 1] - pos1) * factor;
            acc2 = acc2 + (positions[3 * i + 2] - pos2) * factor;
        }
        accel[3 * (256 * bbody + (64 * wtbody0 + wtbody))] = acc0;
        accel[3 * (256 * bbody + (64 * wtbody0 + wtbody)) + 1] = acc1;
        accel[3 * (256 * bbody + (64 * wtbody0 + wtbody)) + 2] = acc2;
    }
}





