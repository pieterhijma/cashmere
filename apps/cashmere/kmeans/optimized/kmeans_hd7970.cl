// hd7970




__kernel void kmeans_kernelKernel (int npoints, int nclusters, int nfeatures, 
        __global const float* points, __global const float* clusters, __global 
        int* pointsCluster) {


    const int wttpid = get_local_id(0);
    const int wttpid0 = get_local_id(1);
    const int bpid = get_group_id(0);
    


    const int nrWorkitemsNrThreadsNpoints = 64;
    const int nrWavefrontsNrThreadsNpoints = 4;
    const int ttpid = 1024 * wttpid0 + wttpid;
    for (int p = 0; p < 16; p++) {
    
        const int pid = 4096 * bpid + 64 * p + (1024 * wttpid0 + wttpid);
        if (4096 * bpid + 64 * p + (1024 * wttpid0 + wttpid) < npoints) {
        
            int ind = 0;
            float min_dist = 3.0E+38;
            float point[MCL_nfeatures];
            for (int feature = 0; feature < MCL_nfeatures; feature++) {
            
                point[feature] = points[4096 * bpid + 64 * p + (1024 * wttpid0 
                        + wttpid) + feature * npoints];
            }
            for (int cluster = 0; cluster < nclusters; cluster++) {
            
                float dist = 0;
                for (int feature = 0; feature < MCL_nfeatures; feature++) {
                
                    const float d = point[feature] - clusters[feature + 
                            cluster * MCL_nfeatures];
                    dist = dist + (point[feature] - clusters[feature + cluster 
                            * MCL_nfeatures]) * (point[feature] - 
                            clusters[feature + cluster * MCL_nfeatures]);
                }
                if (dist < min_dist) {
                
                    min_dist = dist;
                    ind = cluster;
                }
            }
            pointsCluster[4096 * bpid + 64 * p + (1024 * wttpid0 + wttpid)] = 
                    ind;
        }
    }
}





