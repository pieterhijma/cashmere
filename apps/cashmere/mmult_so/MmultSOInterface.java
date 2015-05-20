/* $Id: MmultInterface.java 3447 2006-01-24 16:09:16Z rob $ */

interface MmultSOInterface extends ibis.cashmere.Spawnable {
    public MatrixSO mult(int task, int rec, SharedMatrix a, byte[] aPos,
	    SharedMatrix b, byte[] bPos, MatrixSO c, boolean gpu);
    public Integer makeSOInvocationStart(SharedMatrix a);
}
