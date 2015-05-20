// cc_2_0




__kernel void kmeans_kernelKernel (int npoints, int nclusters, int nfeatures, 
        __global const float* points, __global const float* clusters, __global 
        int* pointsCluster) {


    const int ttpid = get_local_id(0);
    const int wtpid = get_local_id(1);
    const int bpid = get_group_id(0);
    


    const int nrThreadsNrThreadsNpoints = 32;
    const int nrWarpsNrThreadsNpoints = 32;
    __local float clusterBlock[MCL_nfeatures * (2048 / MCL_nfeatures)];
    const int tpid = 32 * wtpid + ttpid;
    int ind[1];
    float min_dist[1];
    for (int i = 0; i < 1; i++) {
    
        ind[i] = 0;
        min_dist[i] = 3.0E+38;
    }
    for (int clusterIter = 0; clusterIter < (nclusters + 2048 / MCL_nfeatures 
            - 1) / (2048 / MCL_nfeatures); clusterIter++) {
    
        int cbsz = 2048 / MCL_nfeatures;
        const int cboffset = clusterIter * (2048 / MCL_nfeatures);
        if (nclusters < clusterIter * (2048 / MCL_nfeatures) + 2048 / 
                MCL_nfeatures) {
        
            cbsz = nclusters - clusterIter * (2048 / MCL_nfeatures);
        }
        for (int i = 32 * wtpid + ttpid; i < cbsz * MCL_nfeatures; i += 1024) {
        
            clusterBlock[i] = clusters[i + MCL_nfeatures * (clusterIter * 
                    (2048 / MCL_nfeatures))];
        }
        barrier(CLK_LOCAL_MEM_FENCE);
        for (int p = 0; p < 1; p++) {
        
            const int pid = 1024 * bpid + 32 * p + (32 * wtpid + ttpid);
            if (1024 * bpid + 32 * p + (32 * wtpid + ttpid) < npoints) {
            
                float point[MCL_nfeatures];
                for (int feature = 0; feature < MCL_nfeatures; feature++) {
                
                    point[feature] = points[1024 * bpid + 32 * p + (32 * wtpid 
                            + ttpid) + feature * npoints];
                }
                for (int cluster = 0; cluster < cbsz; cluster++) {
                
                    float dist = 0;
                    for (int feature = 0; feature < MCL_nfeatures; feature++) {
                    
                        const float d = point[feature] - clusterBlock[feature 
                                + cluster * MCL_nfeatures];
                        dist = dist + (point[feature] - clusterBlock[feature + 
                                cluster * MCL_nfeatures]) * (point[feature] - 
                                clusterBlock[feature + cluster * 
                                MCL_nfeatures]);
                    }
                    if (dist < min_dist[p]) {
                    
                        min_dist[p] = dist;
                        ind[p] = cluster + clusterIter * (2048 / 
                                MCL_nfeatures);
                    }
                }
            }
        }
        barrier(CLK_LOCAL_MEM_FENCE);
    }
    for (int p = 0; p < 1; p++) {
    
        const int pid = 1024 * bpid + 32 * p + (32 * wtpid + ttpid);
        if (1024 * bpid + 32 * p + (32 * wtpid + ttpid) < npoints) {
        
            pointsCluster[1024 * bpid + 32 * p + (32 * wtpid + ttpid)] = 
                    ind[p];
        }
    }
}





