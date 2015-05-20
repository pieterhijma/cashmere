/* $Id: DeleteThread.java 9321 2008-08-25 08:51:04Z ceriel $ */

package ibis.cashmere.impl.faultTolerance;

import ibis.cashmere.impl.Cashmere;

class DeleteThread extends Thread {

    private int milis;

//    private String cluster = null;

    DeleteThread(int time) {
        super("CashmereDeleteThread");
        this.milis = 1000 * time;
    }

    public void run() {
        try {
            sleep(milis);
        } catch (InterruptedException e) {
            //ignore
        }
        Cashmere cashmere = Cashmere.getCashmere();
        cashmere.ft.ftComm.gotSignal("delete", null);
    }
}
