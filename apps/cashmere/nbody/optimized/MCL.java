import ibis.cashmere.many_core.Argument;
import ibis.cashmere.many_core.KernelLaunch;
import ibis.cashmere.many_core.MCCashmereNotAvailable;


class MCL {


    static void launchComputeAccelerationKernel(KernelLaunch kl, int start, 
            int nCompute, int nBodies, float[] positions, float[] mass, 
            float[] accel, float softsq) throws MCCashmereNotAvailable {
        launchComputeAccelerationKernel(kl, start, nCompute, nBodies, 
                positions, true, mass, true, accel, true, softsq);
    }


    static void launchComputeAccelerationKernel(KernelLaunch kl, int start, 
            int nCompute, int nBodies, float[] positions, boolean 
            copypositions, float[] mass, boolean copymass, float[] accel, 
            boolean copyaccel, float softsq) throws MCCashmereNotAvailable {
        
        kl.setArgument(start, Argument.Direction.IN);
        kl.setArgument(nCompute, Argument.Direction.IN);
        kl.setArgument(nBodies, Argument.Direction.IN);
        if (copypositions) {
            kl.setArgument(positions, Argument.Direction.IN);
        }
        else {
            kl.setArgumentNoCopy(positions);
        }
        if (copymass) {
            kl.setArgument(mass, Argument.Direction.IN);
        }
        else {
            kl.setArgumentNoCopy(mass);
        }
        if (copyaccel) {
            kl.setArgument(accel, Argument.Direction.OUT);
        }
        else {
            kl.setArgumentNoCopy(accel);
        }
        kl.setArgument(softsq, Argument.Direction.IN);



        if (kl.getDeviceName().equals("xeon_phi")) {
            kl.launch(16 * ((nCompute + 16 - 1) / 16), 1 * 1, 1 * 1, 16, 1, 1);
        }
        else if (kl.getDeviceName().equals("hd7970")) {
            kl.launch(64 * ((nCompute + 128 - 1) / 128), 2 * 1, 1 * 1, 64, 2, 1);
        }
        else if (kl.getDeviceName().equals("cc_2_0")) {
            kl.launch(32 * ((nCompute + 128 - 1) / 128), 4 * 1, 1 * 1, 32, 4, 1);
        }
        else if (kl.getDeviceName().equals("xeon_e5620")) {
            kl.launch(4 * (nCompute / 4), 1 * 1, 1 * 1, 4, 1, 1);
        }
        else {
            throw new MCCashmereNotAvailable("no compatible device found");
        }
    }
}
