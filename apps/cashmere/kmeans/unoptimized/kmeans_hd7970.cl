// hd7970




__kernel void kmeans_kernelKernel (int npoints, int nclusters, int nfeatures, 
        __global const float* points, __global const float* clusters, __global 
        int* pointsCluster) {


    const int wtpid = get_local_id(0);
    const int wtpid0 = get_local_id(1);
    const int bpid = get_group_id(0);
    


    const int nrWorkitemsNrThreadsNpoints = 64;
    const int nrWavefrontsNrThreadsNpoints = 4;
    const int tpid = 64 * wtpid0 + wtpid;
    const int pid = 256 * bpid + (64 * wtpid0 + wtpid);
    int ind = 0;
    float min_dist = 3.0E+38;
    for (int cluster = 0; cluster < nclusters; cluster++) {
    
        float dist = 0;
        for (int feature = 0; feature < nfeatures; feature++) {
        
            const float d = points[256 * bpid + (64 * wtpid0 + wtpid) + 
                    feature * npoints] - clusters[feature + cluster * 
                    nfeatures];
            dist = dist + (points[256 * bpid + (64 * wtpid0 + wtpid) + feature 
                    * npoints] - clusters[feature + cluster * nfeatures]) * 
                    (points[256 * bpid + (64 * wtpid0 + wtpid) + feature * 
                    npoints] - clusters[feature + cluster * nfeatures]);
        }
        if (dist < min_dist) {
        
            min_dist = dist;
            ind = cluster;
        }
    }
    pointsCluster[256 * bpid + (64 * wtpid0 + wtpid)] = ind;
}





