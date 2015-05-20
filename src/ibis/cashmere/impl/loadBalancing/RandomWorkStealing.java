/* $Id: RandomWorkStealing.java 6190 2007-08-30 11:03:56Z rob $ */

package ibis.cashmere.impl.loadBalancing;

import ibis.cashmere.impl.Cashmere;
import ibis.cashmere.impl.spawnSync.InvocationRecord;

/** The random work-stealing distributed computing algorithm. */

public final class RandomWorkStealing extends LoadBalancingAlgorithm {

    long failedAttempts;
    
    public RandomWorkStealing(Cashmere s) {
        super(s);
    }

    public InvocationRecord clientIteration() {
        Victim v;

        synchronized (cashmere) {
            v = cashmere.victims.getRandomVictim();
            /*
             * Used for fault tolerance; we must know who the current victim is
             * in case it crashes..
             */
            if (v != null) {
                cashmere.lb.setCurrentVictim(v.getIdent());
            }
        }
        if (v == null) {
            return null; //can happen with open world if nobody joined.
        }

        InvocationRecord job = cashmere.lb.stealJob(v, false);
        if(job != null) {
            failedAttempts = 0;
            return job;
        } else {
            failedAttempts++;
            throttle(failedAttempts);
        }
        
        return null;
    }
}
