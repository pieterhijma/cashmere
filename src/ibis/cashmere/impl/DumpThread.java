/* $Id: DumpThread.java 3862 2006-06-20 14:14:19Z rob $ */

package ibis.cashmere.impl;

class DumpThread extends Thread {

    private Cashmere cashmere;

    DumpThread(Cashmere cashmere) {
        super("Cashmere dump thread");
        this.cashmere = cashmere;
    }

    public void run() {
        cashmere.q.print(System.err);
        cashmere.onStack.print(System.err);
        cashmere.outstandingJobs.print(System.err);
        cashmere.ft.print(System.err);
    }
}
