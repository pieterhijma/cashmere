// cc_2_0




__kernel void computeAccelerationKernel (int start, int nCompute, int nBodies, 
        __global const float* positions, __global const float* mass, __global 
        float* accel, float softsq) {


    const int ttbody = get_local_id(0);
    const int wtbody = get_local_id(1);
    const int bbody = get_group_id(0);
    


    const int nrThreadsNrThreadsNCompute = 32;
    const int nrWarpsNrThreadsNCompute = 32;
    const int tbody = 32 * wtbody + ttbody;
    const int body = 1024 * bbody + (32 * wtbody + ttbody);
    const int bodyOffset = 1024 * bbody + (32 * wtbody + ttbody) + start;
    if (1024 * bbody + (32 * wtbody + ttbody) + start < nBodies) {
    
        float acc0 = 0.0;
        float acc1 = 0.0;
        float acc2 = 0.0;
        const float pos0 = positions[3 * (1024 * bbody + (32 * wtbody + 
                ttbody) + start)];
        const float pos1 = positions[3 * (1024 * bbody + (32 * wtbody + 
                ttbody) + start) + 1];
        const float pos2 = positions[3 * (1024 * bbody + (32 * wtbody + 
                ttbody) + start) + 2];
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
        accel[3 * (1024 * bbody + (32 * wtbody + ttbody))] = acc0;
        accel[3 * (1024 * bbody + (32 * wtbody + ttbody)) + 1] = acc1;
        accel[3 * (1024 * bbody + (32 * wtbody + ttbody)) + 2] = acc2;
    }
}





