/* $Id: KillerThread.java 11554 2009-11-20 18:57:58Z ceriel $ */

package ibis.cashmere.impl.faultTolerance;

import ibis.cashmere.impl.Config;
import ibis.cashmere.impl.Cashmere;

class KillerThread extends Thread implements Config {

    private int milis; //wait that long before dying

//    private String cluster = null; //die only if your are in this cluster

    KillerThread(int time) {
        super("CashmereKillerThread");
        this.milis = time * 1000;
    }

    public void run() {
        try {
            sleep(milis);
        } catch (InterruptedException e) {
            //ignore
        }
        // Cashmere cashmere = Cashmere.this_cashmere;
        // if (cashmere.allIbises.indexOf(cashmere.ident)
        //         >= (cashmere.allIbises.size() / 2)) {
        if (STATS && DETAILED_STATS) {
            Cashmere.getCashmere().stats.printDetailedStats(Cashmere.getCashmere().ident);
        }

        System.exit(1); // Kills this cashmere on purpose, this is a killerthread!
    }

}
