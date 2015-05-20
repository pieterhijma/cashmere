/* $Id: IRVector.java 11554 2009-11-20 18:57:58Z ceriel $ */

package ibis.cashmere.impl.spawnSync;

import ibis.ipl.IbisIdentifier;
import ibis.cashmere.impl.Config;
import ibis.cashmere.impl.Cashmere;

/** A vector of invocation records. */

public final class IRVector implements Config {
    private InvocationRecord[] l = new InvocationRecord[500];

    private int count = 0;

    private Cashmere cashmere;

    public IRVector(Cashmere s) {
        this.cashmere = s;
    }

    public void add(InvocationRecord r) {
        if (ASSERTS) {
            Cashmere.assertLocked(cashmere);
        }

        if (count >= l.length) {
            InvocationRecord[] nl = new InvocationRecord[l.length * 2];
            System.arraycopy(l, 0, nl, 0, l.length);
            l = nl;
        }

        l[count] = r;
        count++;
    }

    public int size() {
        if (ASSERTS) {
            Cashmere.assertLocked(cashmere);
        }
        return count;
    }

    public InvocationRecord remove(Stamp stamp) {
        InvocationRecord res = null;

        if (ASSERTS) {
            Cashmere.assertLocked(cashmere);
        }

        for (int i = 0; i < count; i++) {
            if (l[i].getStamp().stampEquals(stamp)) {
                res = l[i];
                count--;
                l[i] = l[count];
                l[count] = null;
                return res;
            }
        }

        // Sometimes (in case of crashes or aborts), we try to remove
        // non-existent elements. This is not a problem, just return null.
        spawnLogger.debug("IRVector: removing non-existent elt: " + stamp);
        return null;
    }

    public InvocationRecord remove(InvocationRecord r) {
        if (ASSERTS) {
            Cashmere.assertLocked(cashmere);
        }

        for (int i = count - 1; i >= 0; i--) {
            if (l[i].equals(r)) {
                InvocationRecord res = l[i];
                count--;
                l[i] = l[count];
                l[count] = null;
                return res;
            }
        }

        // Sometimes (in case of crashes or aborts), we try to remove
        // non-existent elements. This is not a problem, just return null.
        spawnLogger.debug("IRVector: removing non-existent elt: "
            + r.getStamp());
        return null;
    }

    public void killChildrenOf(Stamp targetStamp, boolean store) {
        if (ASSERTS) {
            Cashmere.assertLocked(cashmere);
        }

        for (int i = 0; i < count; i++) {
            InvocationRecord curr = l[i];
            if (curr.aborted) {
                continue; // already handled.
            }

            if ((curr.getParent() != null && curr.getParent().aborted)
                || curr.isDescendentOf(targetStamp)) {
                curr.aborted = true;
                if (abortLogger.isDebugEnabled()) {
                    abortLogger.debug("found stolen child: " + curr.getStamp()
                        + ", it depends on " + targetStamp);
                }
                curr.decrSpawnCounter();
                cashmere.stats.abortedJobs++;
                cashmere.stats.abortMessages++;
                // Curr is removed, but not put back in cache.
                // this is OK. Moreover, it might have children,
                // so we should keep it alive.
                // cleanup is done inside the spawner itself.
                removeIndex(i);
                i--;
                if (store) {
                    cashmere.ft.sendAbortAndStoreMessage(curr);
                } else {
                    cashmere.aborts.sendAbortMessage(curr);
                }
            }
        }
    }

    // Abort every job that was spawned on targetOwner
    // or is a child of a job spawned on targetOwner.
    public void killAndStoreSubtreeOf(IbisIdentifier targetOwner) {
        if (ASSERTS) {
            Cashmere.assertLocked(cashmere);
        }

        for (int i = 0; i < count; i++) {
            InvocationRecord curr = l[i];
            if ((curr.getParent() != null && curr.getParent().aborted)
                || curr.isDescendentOf(targetOwner)
                || curr.getOwner().equals(targetOwner)) {
                //this shouldnt happen, actually
                curr.aborted = true;
                if (abortLogger.isDebugEnabled()) {
                    abortLogger.debug("found stolen child: " + curr.getStamp()
                        + ", it depends on " + targetOwner);
                }
                curr.decrSpawnCounter();
                cashmere.stats.abortedJobs++;
                cashmere.stats.abortMessages++;
                removeIndex(i);
                i--;
                cashmere.ft.sendAbortAndStoreMessage(curr);
            }
        }
    }
    
    // Abort every job that was spawned on targetOwner
    // or is a child of a job spawned on targetOwner.
    public void killSubtreeOf(IbisIdentifier targetOwner) {
        if (ASSERTS) {
            Cashmere.assertLocked(cashmere);
        }

        for (int i = 0; i < count; i++) {
            InvocationRecord curr = l[i];
            if ((curr.getParent() != null && curr.getParent().aborted)
                    || curr.isDescendentOf(targetOwner)
                    || curr.getOwner().equals(targetOwner)) {
                //this shouldnt happen, actually
                curr.aborted = true;
                if (abortLogger.isDebugEnabled()) {
                    abortLogger.debug("found stolen child: " + curr.getStamp()
                            + ", it depends on " + targetOwner);
                }
                curr.decrSpawnCounter();
                cashmere.stats.abortedJobs++;
                cashmere.stats.abortMessages++;
                removeIndex(i);
                i--;
                cashmere.ft.sendAbortMessage(curr);
            }
        }
    }


    public void killAll() {
        if (ASSERTS) {
            Cashmere.assertLocked(cashmere);
        }

        for (int i = 0; i < count; i++) {
            InvocationRecord curr = l[i];
            curr.aborted = true;
            curr.decrSpawnCounter();
            removeIndex(i);
            i--;
        }
    }

    public InvocationRecord removeIndex(int i) {
        if (ASSERTS) {
            Cashmere.assertLocked(cashmere);
        }
        if (i >= count) {
            return null;
        }

        InvocationRecord res = l[i];
        count--;
        l[i] = l[count];
        l[count] = null;
        return res;
    }

    /**
     * Used for fault tolerance. Remove all the jobs stolen by targetOwner and
     * put them back in the taskQueue. 
     */
    public void redoStolenBy(IbisIdentifier crashedIbis) {
        Cashmere.assertLocked(cashmere);

        for (int i = count - 1; i >= 0; i--) {
            if (crashedIbis.equals(l[i].getStealer())) {
                if (ftLogger.isDebugEnabled()) {
                    ftLogger.debug("Found a job to restart: " + l[i].getStamp());
                } 
                l[i].setReDone(true);
                l[i].setStealer(null);
                cashmere.q.addToTail(l[i]);
                cashmere.stats.restartedJobs++;
                count--;
                l[i] = l[count];
            }
        }
    }

    public void print(java.io.PrintStream out) {
        Cashmere.assertLocked(cashmere);

        out.println("=IRVector " + cashmere.ident + ":=============");
        for (int i = 0; i < count; i++) {
            out.println("outjobs [" + i + "] = " + l[i] + ","
                + l[i].getStealer());
        }
        out.println("end of IRVector: " + cashmere.ident + "=");
    }

    public InvocationRecord first() {
        return l[0];
    }
}
