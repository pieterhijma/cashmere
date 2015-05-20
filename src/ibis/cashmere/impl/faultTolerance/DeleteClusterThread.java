/* $Id: DeleteClusterThread.java 6012 2007-08-02 12:39:48Z rob $ */

package ibis.cashmere.impl.faultTolerance;

import ibis.cashmere.impl.Cashmere;
import ibis.cashmere.impl.loadBalancing.Victim;

class DeleteClusterThread extends Thread {

    private int milis;

    DeleteClusterThread(int time) {
        super("CashmereDeleteClusterThread");
        this.milis = 1000 * time;
    }

    public void run() {
        try {
            sleep(milis);
        } catch (InterruptedException e) {
            //ignore
        }
        Cashmere cashmere = Cashmere.getCashmere();
        cashmere.ft.deleteCluster(Victim.clusterOf(cashmere.ident));
    }

}
