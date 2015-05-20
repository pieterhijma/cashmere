// xeon_e5620




__kernel void computeAccelerationKernel (int start, int nCompute, int nBodies, 
        __global const float* positions, __global const float* mass, __global 
        float* accel, float softsq) {


    const int vbody = get_local_id(0);
    const int tbody = get_group_id(0);
    


    const int body = 4 * tbody + vbody;
    const int bodyOffset = 4 * tbody + vbody + start;
    if (4 * tbody + vbody + start < nBodies) {
    
        float acc0 = 0.0;
        float acc1 = 0.0;
        float acc2 = 0.0;
        const float pos0 = positions[3 * (4 * tbody + vbody + start)];
        const float pos1 = positions[3 * (4 * tbody + vbody + start) + 1];
        const float pos2 = positions[3 * (4 * tbody + vbody + start) + 2];
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
        accel[3 * (4 * tbody + vbody)] = acc0;
        accel[3 * (4 * tbody + vbody) + 1] = acc1;
        accel[3 * (4 * tbody + vbody) + 2] = acc2;
    }
}





