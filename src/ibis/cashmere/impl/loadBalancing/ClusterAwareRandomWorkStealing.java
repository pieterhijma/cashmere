/* $Id: ClusterAwareRandomWorkStealing.java 11554 2009-11-20 18:57:58Z ceriel $ */

package ibis.cashmere.impl.loadBalancing;

import ibis.ipl.IbisIdentifier;
import ibis.cashmere.impl.Config;
import ibis.cashmere.impl.Cashmere;
import ibis.cashmere.impl.communication.Protocol;
import ibis.cashmere.impl.spawnSync.InvocationRecord;

import java.io.IOException;

public final class ClusterAwareRandomWorkStealing extends
        LoadBalancingAlgorithm implements Protocol, Config {

    private Cashmere s;

    private boolean gotAsyncStealReply = false;

    private InvocationRecord asyncStolenJob = null;

    private IbisIdentifier asyncCurrentVictim = null;

    private long asyncStealStart;

    /**
     * This means we have sent an ASYNC request, and are waiting for the reply.
     * These are/should only (be) used in clientIteration.
     */
    private boolean asyncStealInProgress = false;

    private long failedLocalAttempts;
    private long failedRemoteAttempts;

    public ClusterAwareRandomWorkStealing(Cashmere s) {
        super(s);
        this.s = s;
    }

    public InvocationRecord clientIteration() {
        Victim localVictim;
        Victim remoteVictim = null;
        boolean canDoAsync = true;

        // First look if there was an outstanding WAN steal request that resulted
        // in a job.
        InvocationRecord job = checkForAsyncReply();
        if (job != null) {
            if (stealLogger.isInfoEnabled()) {
                stealLogger.info("Executing intercluster job " + job.getStamp());
            }

            return job;
        }

        // Else .. we are idle, try to steal a job.
        synchronized (cashmere) {
            localVictim = cashmere.victims.getRandomLocalVictim();
            if (localVictim != null) {
                cashmere.lb.setCurrentVictim(localVictim.getIdent());
            }
            if (!asyncStealInProgress) {
                remoteVictim = cashmere.victims.getRandomRemoteVictim();
                if (remoteVictim != null) {
                    asyncCurrentVictim = remoteVictim.getIdent();
                }
            }
            // Until we download the table, only the cluster coordinator can
            // issue wide-area steal requests 
            // @@@ why? --Rob
            if (cashmere.ft.getTable && !cashmere.clusterCoordinator) {
                canDoAsync = false;
            }
        }

        // Send an asynchronous wide-area steal request,
        // if not is outstanding
        // remoteVictim can be null on a single cluster run.
        if (remoteVictim != null && !asyncStealInProgress) {
            if (FT_NAIVE || canDoAsync) {
                asyncStealInProgress = true;
                s.stats.asyncStealAttempts++;
                try {
                    asyncStealStart = System.currentTimeMillis();
                    cashmere.lb.sendStealRequest(remoteVictim, false, false);
                } catch (IOException e) {
                    commLogger.warn("CASHMERE '" + s.ident
                            + "': Got exception during wa steal request: " + e);
                    // Ignore this?
                }
            }
        }

        // do a local steal, if possible (we might be the only node in this
        // cluster)
        if (localVictim != null) {
            job = cashmere.lb.stealJob(localVictim, false);
            if (job != null) {
                failedLocalAttempts = 0;
                return job;
            } else {
                failedLocalAttempts++;
                throttle(failedLocalAttempts);
            }
        }

        return null;
    }

    private InvocationRecord checkForAsyncReply() {
        if (!asyncStealInProgress) {
            return null;
        }

        boolean failedAttempt = false;
        synchronized (cashmere) {
            boolean gotTimeout =
                    System.currentTimeMillis() - asyncStealStart >= STEAL_WAIT_TIMEOUT;
            if (gotTimeout && !gotAsyncStealReply) {
                ftLogger
                        .warn("CASHMERE '"
                                + s.ident
                                + "': a timeout occurred while waiting for a wide-area steal reply from "
                                + asyncCurrentVictim + ", timeout = "
                                + STEAL_WAIT_TIMEOUT / 1000 + " seconds.");
            }

            if (gotAsyncStealReply || gotTimeout) {
                failedAttempt = true;
                gotAsyncStealReply = false;
                asyncStealInProgress = false;
                asyncCurrentVictim = null;
                InvocationRecord remoteJob = asyncStolenJob;
                asyncStolenJob = null;
                asyncStealStart = 0;

                if (remoteJob != null) {
                    s.stats.asyncStealSuccess++;
                    failedRemoteAttempts = 0;
                    return remoteJob;
                }
            }
        }

        if (failedAttempt) {
            failedRemoteAttempts++;
            throttle(failedRemoteAttempts);
        }

        return null;
    }

    public void stealReplyHandler(InvocationRecord ir, IbisIdentifier sender,
            int opcode) {
        switch (opcode) {
        case STEAL_REPLY_SUCCESS:
        case STEAL_REPLY_FAILED:
        case STEAL_REPLY_SUCCESS_TABLE:
        case STEAL_REPLY_FAILED_TABLE:
            cashmere.lb.gotJobResult(ir, sender);
            break;
        case ASYNC_STEAL_REPLY_SUCCESS:
        case ASYNC_STEAL_REPLY_FAILED:
        case ASYNC_STEAL_REPLY_SUCCESS_TABLE:
        case ASYNC_STEAL_REPLY_FAILED_TABLE:
            if (stealLogger.isInfoEnabled() && ir != null) {
                stealLogger.info("Stole intercluster job " + ir.getStamp());
            }
            synchronized (cashmere) {
                if (sender.equals(asyncCurrentVictim)) {
                    gotAsyncStealReply = true;
                    asyncStolenJob = ir;
                    //            		cashmere.notifyAll(); // not needed I think, we naver wait for the async job.
                } else {
                    ftLogger
                            .warn("CASHMERE '"
                                    + s.ident
                                    + "': received an async job from a node that caused a timeout before.");
                    if (ir != null) {
                        s.q.addToTail(ir);
                    }
                }
            }
            break;
        default:
            s.assertFailed("illegal opcode in CRS stealReplyHandler",
                    new Exception());
        }
    }

    public void exit() {
        // wait for a pending async steal reply
        if (asyncStealInProgress) {
            stealLogger.info("waiting for a pending async steal reply from "
                    + asyncCurrentVictim);
            synchronized (cashmere) {
                while (asyncStealInProgress && !gotAsyncStealReply) {
                    try {
                        cashmere.handleDelayedMessages(); //TODO move outside lock --Rob
                        cashmere.wait(250);
                    } catch (InterruptedException e) {
                        //ignore
                    }
                }
            }
            if (ASSERTS && asyncStolenJob != null) {
                stealLogger.warn("Cashmere: CRS: EEK, stole async job "
                        + "after exiting!");
            }
        }
    }

    /**
     * Used in fault tolerance; check if the asynchronous steal victim crashed;
     * if so, cancel the steal request; if the job already arrived, remove it
     * (it should be aborted anyway, since it was stolen from a crashed machine)
     * if the owner of the asynchronously stolen job
     * crashed, abort the job.
     */
    public void handleCrash(IbisIdentifier crashedIbis) {
        Cashmere.assertLocked(cashmere);
        if (crashedIbis.equals(asyncCurrentVictim)) {
            /*
             * current async victim crashed, reset the flag, remove the stolen
             * job
             */
            asyncStealInProgress = false;
            asyncStolenJob = null;
            asyncCurrentVictim = null;
            gotAsyncStealReply = false;
        }

        if (asyncStolenJob != null) {
            if (asyncStolenJob.getOwner().equals(crashedIbis)) {
                asyncStolenJob = null;
            }
        }
    }
}
