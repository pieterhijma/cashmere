import ibis.cashmere.many_core.Argument;
import ibis.cashmere.many_core.KernelLaunch;
import ibis.cashmere.many_core.MCCashmereNotAvailable;


class MCL {


    static void launchKmeans_kernelKernel(KernelLaunch kl, int npoints, int 
            nclusters, int nfeatures, float[] points, float[] clusters, int[] 
            pointsCluster) throws MCCashmereNotAvailable {
        launchKmeans_kernelKernel(kl, npoints, nclusters, nfeatures, points, 
                true, clusters, true, pointsCluster, true);
    }


    static void launchKmeans_kernelKernel(KernelLaunch kl, int npoints, int 
            nclusters, int nfeatures, float[] points, boolean copypoints, 
            float[] clusters, boolean copyclusters, int[] pointsCluster, 
            boolean copypointsCluster) throws MCCashmereNotAvailable {
        
        kl.setArgument(npoints, Argument.Direction.IN);
        kl.setArgument(nclusters, Argument.Direction.IN);
        kl.setArgument(nfeatures, Argument.Direction.IN);
        if (copypoints) {
            kl.setArgument(points, Argument.Direction.IN);
        }
        else {
            kl.setArgumentNoCopy(points);
        }
        if (copyclusters) {
            kl.setArgument(clusters, Argument.Direction.IN);
        }
        else {
            kl.setArgumentNoCopy(clusters);
        }
        if (copypointsCluster) {
            kl.setArgument(pointsCluster, Argument.Direction.OUT);
        }
        else {
            kl.setArgumentNoCopy(pointsCluster);
        }



        if (kl.getDeviceName().equals("xeon_phi")) {
            kl.launch(16 * (npoints / 16), 1 * 1, 1 * 1, 16, 1, 1);
        }
        else if (kl.getDeviceName().equals("hd7970")) {
            kl.launch(64 * (npoints / 256), 4 * 1, 1 * 1, 64, 4, 1);
        }
        else if (kl.getDeviceName().equals("cc_2_0")) {
            kl.launch(32 * (npoints / 1024), 32 * 1, 1 * 1, 32, 32, 1);
        }
        else if (kl.getDeviceName().equals("xeon_e5620")) {
            kl.launch(4 * (npoints / 4), 1 * 1, 1 * 1, 4, 1, 1);
        }
        else {
            throw new MCCashmereNotAvailable("no compatible device found");
        }
    }
}
