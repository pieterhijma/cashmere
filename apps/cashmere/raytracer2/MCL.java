import ibis.cashmere.many_core.Argument;
import ibis.cashmere.many_core.KernelLaunch;
import ibis.cashmere.many_core.MCCashmereNotAvailable;


class MCL {


    static void launchRadianceKernel(KernelLaunch kl, int h, int w, float[] 
            colors, int height, int width, int[] seeds, int nrSpheres, float[] 
            spheres, float[] camera, int xOffset, int yOffset, int 
            currentSample) throws MCCashmereNotAvailable {
        launchRadianceKernel(kl, h, w, colors, true, height, width, seeds, 
                true, nrSpheres, spheres, true, camera, true, xOffset, yOffset,
                 currentSample);
    }


    static void launchRadianceKernel(KernelLaunch kl, int h, int w, float[] 
            colors, boolean copycolors, int height, int width, int[] seeds, 
            boolean copyseeds, int nrSpheres, float[] spheres, boolean 
            copyspheres, float[] camera, boolean copycamera, int xOffset, int 
            yOffset, int currentSample) throws MCCashmereNotAvailable {
        
        kl.setArgument(h, Argument.Direction.IN);
        kl.setArgument(w, Argument.Direction.IN);
        if (copycolors) {
            kl.setArgument(colors, Argument.Direction.INOUT);
        }
        else {
            kl.setArgumentNoCopy(colors);
        }
        kl.setArgument(height, Argument.Direction.IN);
        kl.setArgument(width, Argument.Direction.IN);
        if (copyseeds) {
            kl.setArgument(seeds, Argument.Direction.INOUT);
        }
        else {
            kl.setArgumentNoCopy(seeds);
        }
        kl.setArgument(nrSpheres, Argument.Direction.IN);
        if (copyspheres) {
            kl.setArgument(spheres, Argument.Direction.IN);
        }
        else {
            kl.setArgumentNoCopy(spheres);
        }
        if (copycamera) {
            kl.setArgument(camera, Argument.Direction.IN);
        }
        else {
            kl.setArgumentNoCopy(camera);
        }
        kl.setArgument(xOffset, Argument.Direction.IN);
        kl.setArgument(yOffset, Argument.Direction.IN);
        kl.setArgument(currentSample, Argument.Direction.IN);



        if (kl.getDeviceName().equals("xeon_phi")) {
            kl.launch(16 * (w / 16), 1 * h, 1 * 1, 16, 1, 1);
        }
        else if (kl.getDeviceName().equals("hd7970")) {
            kl.launch(64 * (w / 256), 4 * h, 1 * 1, 64, 4, 1);
        }
        else if (kl.getDeviceName().equals("cc_2_0")) {
            kl.launch(32 * (w / 128), 4 * h, 1 * 1, 32, 4, 1);
        }
        else if (kl.getDeviceName().equals("xeon_e5620")) {
            kl.launch(4 * (w / 4), 1 * h, 1 * 1, 4, 1, 1);
        }
        else {
            throw new MCCashmereNotAvailable("no compatible device found");
        }
    }
}
