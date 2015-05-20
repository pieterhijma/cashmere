// cc_2_0




__kernel void kmeans_kernelKernel (int npoints, int nclusters, int nfeatures, 
        __global const float* points, __global const float* clusters, __global 
        int* pointsCluster) {


    const int ttpid = get_local_id(0);
    const int wtpid = get_local_id(1);
    const int bpid = get_group_id(0);
    


    const int nrThreadsNrThreadsNpoints = 32;
    const int nrWarpsNrThreadsNpoints = 32;
    const int tpid = 32 * wtpid + ttpid;
    const int pid = 1024 * bpid + (32 * wtpid + ttpid);
    int ind = 0;
    float min_dist = 3.0E+38;
    for (int cluster = 0; cluster < nclusters; cluster++) {
    
        float dist = 0;
        for (int feature = 0; feature < nfeatures; feature++) {
        
            const float d = points[1024 * bpid + (32 * wtpid + ttpid) + 
                    feature * npoints] - clusters[feature + cluster * 
                    nfeatures];
            dist = dist + (points[1024 * bpid + (32 * wtpid + ttpid) + feature 
                    * npoints] - clusters[feature + cluster * nfeatures]) * 
                    (points[1024 * bpid + (32 * wtpid + ttpid) + feature * 
                    npoints] - clusters[feature + cluster * nfeatures]);
        }
        if (dist < min_dist) {
        
            min_dist = dist;
            ind = cluster;
        }
    }
    pointsCluster[1024 * bpid + (32 * wtpid + ttpid)] = ind;
}





