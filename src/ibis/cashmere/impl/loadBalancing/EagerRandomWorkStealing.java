/* $Id:EagerRandomWorkStealing.java 6190 2007-08-30 11:03:56Z rob $ */

package ibis.cashmere.impl.loadBalancing;

import ibis.cashmere.impl.Cashmere;
import ibis.cashmere.impl.spawnSync.InvocationRecord;
import ibis.util.ThreadPool;

// Random stealing, but steals one more.

public final class EagerRandomWorkStealing extends LoadBalancingAlgorithm implements Runnable {

    InvocationRecord job = null;
    boolean haveResult = false;
        
    long failedAttempts;

    public void run() {
        for (;;) {
            synchronized(this) {
                while (job != null) {
                    try {
                        wait();
                    } catch(Throwable e) {
                        // ignore
                    }
                }
            }
            InvocationRecord j = stealJob();
            synchronized(this) {
                haveResult = true;
                job = j;
                notifyAll();
            }
        }
    }

    private InvocationRecord stealJob() {
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

    public EagerRandomWorkStealing(Cashmere s) {
        super(s);
        ThreadPool.createNew(this, "EagerStealer");
    }

    public synchronized InvocationRecord clientIteration() {
        while (job == null) {
            try {
                wait(100);
            } catch(Throwable e) {
                // ignore
            }
            if (haveResult) {
                break;
            }
        }
        InvocationRecord v = job;
        haveResult = false;
        job = null;
        notifyAll();
        return v;
    }
}
