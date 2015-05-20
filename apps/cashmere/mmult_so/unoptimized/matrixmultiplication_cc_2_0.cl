// cc_2_0




__kernel void matmulKernel (int n, int m, int p, __global float* c, __global 
        const float* a, __global const float* b) {


    const int ttj = get_local_id(0);
    const int wtj = get_local_id(1);
    const int bj = get_group_id(0);
    const int i = get_group_id(1);
    


    const int nrThreadsNrThreadsM = 32;
    const int nrWarpsNrThreadsM = 32;
    const int tj = 32 * wtj + ttj;
    const int j = 1024 * bj + (32 * wtj + ttj);
    float sum = 0.0;
    for (int k = 0; k < p; k++) {
    
        sum = sum + a[k + i * p] * b[1024 * bj + (32 * wtj + ttj) + k * m];
    }
    c[1024 * bj + (32 * wtj + ttj) + i * m] += sum;
}





