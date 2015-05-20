// hd7970




__kernel void matmulKernel (int n, int m, int p, __global float* c, __global 
        const float* a, __global const float* b) {


    const int wtj = get_local_id(0);
    const int wtj0 = get_local_id(1);
    const int bj = get_group_id(0);
    const int bi = get_group_id(1);
    


    __local float l_a[4096];
    const int nrWorkitemsNrThreadsM = 64;
    const int nrWavefrontsNrThreadsM = 4;
    const int tj = 64 * wtj0 + wtj;
    float sums[16];
    const int j = 256 * bj + (64 * wtj0 + wtj);
    for (int ei = 0; ei < 16; ei++) {
    
        sums[ei] = 0.0;
    }
    for (int l = 0; l < p / 256; l++) {
    
        for (int ei = 0; ei < 16; ei++) {
        
            l_a[64 * wtj0 + wtj + 256 * ei] = a[64 * wtj0 + wtj + 256 * l + 
                    (ei + 16 * bi) * (256 * (p / 256))];
        }
        barrier(CLK_LOCAL_MEM_FENCE);
        for (int k2 = 0; k2 < p / (p / 256); k2++) {
        
            const int k = l * p / (p / 256) + k2;
            const float bkj = b[256 * bj + (64 * wtj0 + wtj) + (l * p / (p / 
                    256) + k2) * m];
            for (int ei = 0; ei < 16; ei++) {
            
                sums[ei] += l_a[k2 + 256 * ei] * bkj;
            }
        }
        barrier(CLK_LOCAL_MEM_FENCE);
    }
    for (int ei = 0; ei < 16; ei++) {
    
        c[64 * wtj0 + wtj + 256 * bj + (ei + 16 * bi) * (256 * (m / 256))] += 
                sums[ei];
    }
}





