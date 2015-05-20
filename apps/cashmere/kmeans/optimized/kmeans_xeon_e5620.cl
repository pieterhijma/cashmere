// xeon_e5620




__kernel void kmeans_kernelKernel (int npoints, int nclusters, int nfeatures, 
        __global const float* points, __global const float* clusters, __global 
        int* pointsCluster) {


    const int vpid = get_local_id(0);
    const int tpid = get_group_id(0);
    


    const int pid = 4 * tpid + vpid;
    int ind = 0;
    float min_dist = 3.0E+38;
    for (int cluster = 0; cluster < nclusters; cluster++) {
    
        float dist = 0;
        for (int feature = 0; feature < nfeatures; feature++) {
        
            const float d = points[4 * tpid + vpid + feature * npoints] - 
                    clusters[feature + cluster * nfeatures];
            dist = dist + (points[4 * tpid + vpid + feature * npoints] - 
                    clusters[feature + cluster * nfeatures]) * (points[4 * 
                    tpid + vpid + feature * npoints] - clusters[feature + 
                    cluster * nfeatures]);
        }
        if (dist < min_dist) {
        
            min_dist = dist;
            ind = cluster;
        }
    }
    pointsCluster[4 * tpid + vpid] = ind;
}





