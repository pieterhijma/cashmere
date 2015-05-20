// xeon_phi




__kernel void kmeans_kernelKernel (int npoints, int nclusters, int nfeatures, 
        __global const float* points, __global const float* clusters, __global 
        int* pointsCluster) {


    const int vpid = get_local_id(0);
    const int tpid = get_group_id(0);
    


    for (int elNo = 0; elNo < (npoints + 30208 - 1) / 30208; elNo++) {
    
        const int pid = 16 * (tpid * ((npoints + 30208 - 1) / 30208) + elNo) + 
                vpid;
        if (16 * (tpid * ((npoints + 30208 - 1) / 30208) + elNo) + vpid < 
                npoints) {
        
            int ind = 0;
            float point[MCL_nfeatures];
            for (int feature = 0; feature < MCL_nfeatures; feature++) {
            
                point[feature] = points[16 * (tpid * ((npoints + 30208 - 1) / 
                        30208) + elNo) + vpid + feature * npoints];
            }
            float min_dist = 3.0E+38;
            for (int cluster = 0; cluster < nclusters; cluster++) {
            
                float dist = 0;
                for (int feature = 0; feature < MCL_nfeatures; feature++) {
                
                    const float d = point[feature] - clusters[feature + 
                            cluster * MCL_nfeatures];
                    dist += (point[feature] - clusters[feature + cluster * 
                            MCL_nfeatures]) * (point[feature] - 
                            clusters[feature + cluster * MCL_nfeatures]);
                }
                if (dist < min_dist) {
                
                    min_dist = dist;
                    ind = cluster;
                }
            }
            pointsCluster[16 * (tpid * ((npoints + 30208 - 1) / 30208) + elNo) 
                    + vpid] = ind;
        }
    }
}





