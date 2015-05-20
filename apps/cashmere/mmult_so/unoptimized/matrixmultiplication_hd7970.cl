// hd7970




__kernel void matmulKernel (int n, int m, int p, __global float* c, __global 
        const float* a, __global const float* b) {


    const int wtj = get_local_id(0);
    const int wtj0 = get_local_id(1);
    const int bj = get_group_id(0);
    const int i = get_group_id(1);
    


    const int nrWorkitemsNrThreadsM = 64;
    const int nrWavefrontsNrThreadsM = 4;
    const int tj = 64 * wtj0 + wtj;
    const int j = 256 * bj + (64 * wtj0 + wtj);
    float sum = 0.0;
    for (int k = 0; k < p; k++) {
    
        sum = sum + a[k + i * p] * b[256 * bj + (64 * wtj0 + wtj) + k * m];
    }
    c[256 * bj + (64 * wtj0 + wtj) + i * m] += sum;
}





