/* $Id: MasterWorker.java 9591 2008-10-02 08:26:33Z ceriel $ */

package ibis.cashmere.impl.loadBalancing;

import ibis.cashmere.impl.Config;
import ibis.cashmere.impl.Cashmere;
import ibis.cashmere.impl.spawnSync.InvocationRecord;

/** The master-worker distribution algorithm. */

public final class MasterWorker extends LoadBalancingAlgorithm implements
        Config {

    public MasterWorker(Cashmere s) {
        super(s);
    }

    public InvocationRecord clientIteration() {
        Victim v;

        if (cashmere.isMaster()) {
            return null;
        }

        synchronized (cashmere) {
            v = cashmere.victims.getVictim(cashmere.getMasterIdent());
        }

        if (v == null) return null; // node might have crashed

        cashmere.lb.setCurrentVictim(v.getIdent());

        return cashmere.lb.stealJob(v, true); // blocks at the server side
    }

    public void jobAdded() {
        synchronized (cashmere) {
            if (!cashmere.isMaster()) {
                spawnLogger.error("with the master/worker algorithm, "
                    + "work can only be spawned on the master!");
                System.exit(1); // Failed assertion
            }

            cashmere.notifyAll();
        }
    }

    public void exit() {
        synchronized (cashmere) {
            cashmere.notifyAll();
        }
    }
}
