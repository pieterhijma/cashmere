// cc_2_0




__kernel void computeAccelerationKernel (int start, int nCompute, int nBodies, 
        __global const float* positions, __global const float* mass, __global 
        float* accel, float softsq) {


    const int ttbody = get_local_id(0);
    const int wtbody = get_local_id(1);
    const int bbody = get_group_id(0);
    


    __local float xcopy[128];
    __local float ycopy[128];
    __local float zcopy[128];
    __local float mcopy[128];
    const int nrThreadsNrThreadsNCompute = 32;
    const int nrWarpsNrThreadsNCompute = 4;
    const int tbody = 32 * wtbody + ttbody;
    const int body = 128 * bbody + (32 * wtbody + ttbody);
    const int bodyOffset = 128 * bbody + (32 * wtbody + ttbody) + start;
    if (128 * bbody + (32 * wtbody + ttbody) + start < nBodies) {
    
        float acc0 = 0.0;
        float acc1 = 0.0;
        float acc2 = 0.0;
        const float pos0 = positions[3 * (128 * bbody + (32 * wtbody + ttbody) 
                + start)];
        const float pos1 = positions[3 * (128 * bbody + (32 * wtbody + ttbody) 
                + start) + 1];
        const float pos2 = positions[3 * (128 * bbody + (32 * wtbody + ttbody) 
                + start) + 2];
        const int l = 128 * (nBodies / 128);
        for (int k = 0; k < 128 * (nBodies / 128); k += 128) {
        
            xcopy[32 * wtbody + ttbody] = positions[3 * (k + (32 * wtbody + 
                    ttbody))];
            ycopy[32 * wtbody + ttbody] = positions[3 * (k + (32 * wtbody + 
                    ttbody)) + 1];
            zcopy[32 * wtbody + ttbody] = positions[3 * (k + (32 * wtbody + 
                    ttbody)) + 2];
            mcopy[32 * wtbody + ttbody] = mass[k + (32 * wtbody + ttbody)];
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
            if (32 * wtbody + ttbody < nBodies - 128 * (nBodies / 128)) {
            
                xcopy[32 * wtbody + ttbody] = positions[3 * (128 * (nBodies / 
                        128) + (32 * wtbody + ttbody))];
                ycopy[32 * wtbody + ttbody] = positions[3 * (128 * (nBodies / 
                        128) + (32 * wtbody + ttbody)) + 1];
                zcopy[32 * wtbody + ttbody] = positions[3 * (128 * (nBodies / 
                        128) + (32 * wtbody + ttbody)) + 2];
                mcopy[32 * wtbody + ttbody] = mass[128 * (nBodies / 128) + (32 
                        * wtbody + ttbody)];
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
        accel[3 * (128 * bbody + (32 * wtbody + ttbody))] = acc0;
        accel[3 * (128 * bbody + (32 * wtbody + ttbody)) + 1] = acc1;
        accel[3 * (128 * bbody + (32 * wtbody + ttbody)) + 2] = acc2;
    }
}





