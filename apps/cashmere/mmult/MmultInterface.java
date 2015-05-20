/* $Id: MmultInterface.java 2844 2004-11-24 10:52:27Z ceriel $ */

interface MmultInterface extends ibis.cashmere.Spawnable {
    public Matrix mult(int task, int rec, Matrix a, Matrix b, Matrix c);
}