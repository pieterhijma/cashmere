import ibis.cashmere.many_core.Argument;
import ibis.cashmere.many_core.KernelLaunch;
import ibis.cashmere.many_core.MCCashmereNotAvailable;

import java.nio.ByteBuffer;

class MCL {


    static void launchMatmulKernel(KernelLaunch kl, int n, int m, int p, 
            ByteBuffer c, float[] a, float[] b) throws MCCashmereNotAvailable {
        launchMatmulKernel(kl, n, m, p, c, true, a, true, b, true);
    }


    static void launchMatmulKernel(KernelLaunch kl, int n, int m, int p, 
            ByteBuffer c, boolean copyc, float[] a, boolean copya, float[] b, 
            boolean copyb) throws MCCashmereNotAvailable {
        
        kl.setArgument(n, Argument.Direction.IN);
        kl.setArgument(m, Argument.Direction.IN);
        kl.setArgument(p, Argument.Direction.IN);
        if (copyc) {
            kl.setArgument(c, Argument.Direction.INOUT);
        }
        else {
            kl.setArgumentNoCopy(c);
        }
        if (copya) {
            kl.setArgument(a, Argument.Direction.IN);
        }
        else {
            kl.setArgumentNoCopy(a);
        }
        if (copyb) {
            kl.setArgument(b, Argument.Direction.IN);
        }
        else {
            kl.setArgumentNoCopy(b);
        }



        if (kl.getDeviceName().equals("xeon_phi")) {
            kl.launch(16 * (m / 16), 1 * n, 1 * 1, 16, 1, 1);
        }
        else if (kl.getDeviceName().equals("hd7970")) {
            kl.launch(64 * (m / 256), 4 * n, 1 * 1, 64, 4, 1);
        }
        else if (kl.getDeviceName().equals("cc_2_0")) {
            kl.launch(32 * (m / 1024), 32 * n, 1 * 1, 32, 32, 1);
        }
        else if (kl.getDeviceName().equals("xeon_e5620")) {
            kl.launch(4 * (m / 4), 1 * n, 1 * 1, 4, 1, 1);
        }
        else {
            throw new MCCashmereNotAvailable("no compatible device found");
        }
    }
}
