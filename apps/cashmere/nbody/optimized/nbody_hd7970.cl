// hd7970




__kernel void computeAccelerationKernel (int start, int nCompute, int nBodies, 
        __global const float* positions, __global const float* mass, __global 
        float* accel, float softsq) {


    const int wtbody = get_local_id(0);
    const int wtbody0 = get_local_id(1);
    const int bbody = get_group_id(0);
    


    __local float xcopy[128];
    __local float ycopy[128];
    __local float zcopy[128];
    __local float mcopy[128];
    const int nrWorkitemsNrThreadsNCompute = 64;
    const int nrWavefrontsNrThreadsNCompute = 2;
    const int tbody = 64 * wtbody0 + wtbody;
    const int body = 128 * bbody + (64 * wtbody0 + wtbody);
    const int bodyOffset = 128 * bbody + (64 * wtbody0 + wtbody) + start;
    if (128 * bbody + (64 * wtbody0 + wtbody) + start < nBodies) {
    
        float acc0 = 0.0;
        float acc1 = 0.0;
        float acc2 = 0.0;
        const float pos0 = positions[3 * (128 * bbody + (64 * wtbody0 + 
                wtbody) + start)];
        const float pos1 = positions[3 * (128 * bbody + (64 * wtbody0 + 
                wtbody) + start) + 1];
        const float pos2 = positions[3 * (128 * bbody + (64 * wtbody0 + 
                wtbody) + start) + 2];
        const int l = 128 * (nBodies / 128);
        for (int k = 0; k < 128 * (nBodies / 128); k += 128) {
        
            xcopy[64 * wtbody0 + wtbody] = positions[3 * (k + (64 * wtbody0 + 
                    wtbody))];
            ycopy[64 * wtbody0 + wtbody] = positions[3 * (k + (64 * wtbody0 + 
                    wtbody)) + 1];
            zcopy[64 * wtbody0 + wtbody] = positions[3 * (k + (64 * wtbody0 + 
                    wtbody)) + 2];
            mcopy[64 * wtbody0 + wtbody] = mass[k + (64 * wtbody0 + wtbody)];
            barrier(CLK_LOCAL_MEM_FENCE);
            for (int i = 0; i < 128; i++) {
            
                const float diff_x = xcopy[i] - pos0;
                const float diff_y = ycopy[i] - pos1;
                const float diff_z = zcopy[i] - pos2;
                const float distsq = (xcopy[i] - pos0) * (xcopy[i] - pos0) + 
                        (ycopy[i] - pos1) * (ycopy[i] - pos1) + (zcopy[i] - 
                        pos2) * (zcopy[i] - pos2) + softsq;
                const float factor = mcopy[i] / (((xcopy[i] - pos0) * 
                        (xcopy[i] - pos0) + (ycopy[i] - pos1) * (ycopy[i] - 
                        pos1) + (zcopy[i] - pos2) * (zcopy[i] - pos2) + 
                        softsq) * sqrt((xcopy[i] - pos0) * (xcopy[i] - pos0) + 
                        (ycopy[i] - pos1) * (ycopy[i] - pos1) + (zcopy[i] - 
                        pos2) * (zcopy[i] - pos2) + softsq));
                acc0 = acc0 + (xcopy[i] - pos0) * factor;
                acc1 = acc1 + (ycopy[i] - pos1) * factor;
                acc2 = acc2 + (zcopy[i] - pos2) * factor;
            }
            barrier(CLK_LOCAL_MEM_FENCE);
        }
        if (128 * (nBodies / 128) < nBodies) {
        
            const int amount = nBodies - 128 * (nBodies / 128);
            if (64 * wtbody0 + wtbody < nBodies - 128 * (nBodies / 128)) {
            
                xcopy[64 * wtbody0 + wtbody] = positions[3 * (128 * (nBodies / 
                        128) + (64 * wtbody0 + wtbody))];
                ycopy[64 * wtbody0 + wtbody] = positions[3 * (128 * (nBodies / 
                        128) + (64 * wtbody0 + wtbody)) + 1];
                zcopy[64 * wtbody0 + wtbody] = positions[3 * (128 * (nBodies / 
                        128) + (64 * wtbody0 + wtbody)) + 2];
                mcopy[64 * wtbody0 + wtbody] = mass[128 * (nBodies / 128) + 
                        (64 * wtbody0 + wtbody)];
            }
            barrier(CLK_LOCAL_MEM_FENCE);
            for (int i = 0; i < nBodies - 128 * (nBodies / 128); i++) {
            
                const float diff_x = xcopy[i] - pos0;
                const float diff_y = ycopy[i] - pos1;
                const float diff_z = zcopy[i] - pos2;
                const float distsq = (xcopy[i] - pos0) * (xcopy[i] - pos0) + 
                        (ycopy[i] - pos1) * (ycopy[i] - pos1) + (zcopy[i] - 
                        pos2) * (zcopy[i] - pos2) + softsq;
                const float factor = mcopy[i] / (((xcopy[i] - pos0) * 
                        (xcopy[i] - pos0) + (ycopy[i] - pos1) * (ycopy[i] - 
                        pos1) + (zcopy[i] - pos2) * (zcopy[i] - pos2) + 
                        softsq) * sqrt((xcopy[i] - pos0) * (xcopy[i] - pos0) + 
                        (ycopy[i] - pos1) * (ycopy[i] - pos1) + (zcopy[i] - 
                        pos2) * (zcopy[i] - pos2) + softsq));
                acc0 = acc0 + (xcopy[i] - pos0) * factor;
                acc1 = acc1 + (ycopy[i] - pos1) * factor;
                acc2 = acc2 + (zcopy[i] - pos2) * factor;
            }
            barrier(CLK_LOCAL_MEM_FENCE);
        }
        accel[3 * (128 * bbody + (64 * wtbody0 + wtbody))] = acc0;
        accel[3 * (128 * bbody + (64 * wtbody0 + wtbody)) + 1] = acc1;
        accel[3 * (128 * bbody + (64 * wtbody0 + wtbody)) + 2] = acc2;
    }
}





