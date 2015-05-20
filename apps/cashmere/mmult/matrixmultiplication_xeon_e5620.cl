// xeon_e5620




__kernel void matmulKernel (int n, int m, int p, __global float* c, __global 
        const float* a, __global const float* b) {


    const int vj = get_local_id(0);
    const int tj = get_group_id(0);
    const int ti = get_group_id(1);
    


    for (int ej = 0; ej < 1; ej++) {
    
        float cTemp[128];
        for (int ei = 0; ei < 128; ei++) {
        
            cTemp[ei] = 0.0;
        }
        for (int bk = 0; bk < p / 16; bk++) {
        
            float bTemp[16];
            for (int ek = 0; ek < 16; ek++) {
            
                bTemp[ek] = b[vj + 4 * tj + 4 * ej + (16 * bk + ek) * (4 * (m 
                        / 4))];
            }
            for (int ei = 0; ei < 128; ei++) {
            
                float sum = 0.0;
                for (int ek = 0; ek < 16; ek++) {
                
                    sum += a[ek + 16 * bk + (ei + 128 * ti) * (16 * (p / 16))] 
                            * bTemp[ek];
                }
                cTemp[ei] += sum;
            }
        }
        for (int ei = 0; ei < 128; ei++) {
        
            c[vj + 4 * tj + 4 * ej + (128 * ti + ei) * (4 * (m / 4))] += 
                    cTemp[ei];
        }
    }
}





