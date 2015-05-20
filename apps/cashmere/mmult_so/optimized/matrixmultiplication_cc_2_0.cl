// cc_2_0




__kernel void matmulKernel (int n, int m, int p, __global float* c, __global 
        const float* a, __global const float* b) {


    const int ttj = get_local_id(0);
    const int wtj = get_local_id(1);
    const int bj = get_group_id(0);
    const int bi = get_group_id(1);
    


    __local float l_a[2048];
    const int tj = 32 * wtj + ttj;
    float sums[16];
    const int j = 128 * bj + (32 * wtj + ttj);
    for (int ei = 0; ei < 16; ei++) {
    
        sums[ei] = 0.0;
    }
    for (int l = 0; l < p / 128; l++) {
    
        for (int ei = 0; ei < 16; ei++) {
        
            l_a[32 * wtj + ttj + 128 * ei] = a[32 * wtj + ttj + 128 * l + (ei 
                    + 16 * bi) * (128 * (p / 128))];
        }
        barrier(CLK_LOCAL_MEM_FENCE);
        for (int k2 = 0; k2 < p / (p / 128); k2++) {
        
            const int k = l * p / (p / 128) + k2;
            const float bkj = b[128 * bj + (32 * wtj + ttj) + (l * p / (p / 
                    128) + k2) * m];
            for (int ei = 0; ei < 16; ei++) {
            
                sums[ei] += l_a[k2 + 128 * ei] * bkj;
            }
        }
        barrier(CLK_LOCAL_MEM_FENCE);
    }
    for (int ei = 0; ei < 16; ei++) {
    
        c[32 * wtj + ttj + 128 * bj + (ei + 16 * bi) * (128 * (m / 128))] += 
                sums[ei];
    }
}





