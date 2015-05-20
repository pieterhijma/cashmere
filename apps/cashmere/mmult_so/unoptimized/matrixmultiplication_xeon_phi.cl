// xeon_phi




__kernel void matmulKernel (int n, int m, int p, __global float* c, __global 
        const float* a, __global const float* b) {


    const int vj = get_local_id(0);
    const int tj = get_group_id(0);
    const int i = get_group_id(1);
    


    const int j = 16 * tj + vj;
    float sum = 0.0;
    for (int k = 0; k < p; k++) {
    
        sum = sum + a[k + i * p] * b[16 * tj + vj + k * m];
    }
    c[16 * tj + vj + i * m] += sum;
}





