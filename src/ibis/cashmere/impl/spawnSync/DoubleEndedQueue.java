/* $Id: DoubleEndedQueue.java 5847 2007-06-08 15:34:44Z ceriel $ */

package ibis.cashmere.impl.spawnSync;

import ibis.cashmere.impl.Config;
import ibis.cashmere.impl.Cashmere;

/** The implementation of a double-ended queue. */

// There is no need to delete aborted invocation records, the spawner keeps an
// outstandingJobs list.
public final class DoubleEndedQueue implements Config {
    private InvocationRecord head = null;

    private InvocationRecord tail = null;

    private int length = 0;

    private Cashmere cashmere;

    public DoubleEndedQueue(Cashmere cashmere) {
        this.cashmere = cashmere;
    }

    public InvocationRecord getFromHead() {
        synchronized (cashmere) {
            if (length == 0) {
                return null;
            }

            InvocationRecord rtn = head;
            head = head.getQnext();
            if (head == null) {
                tail = null;
            } else {
                head.setQprev(null);
            }
            length--;

            rtn.setQnext(null);
            rtn.setQprev(null);
            return rtn;
        }
    }

    public InvocationRecord getFromTail() {
        synchronized (cashmere) {
            if (length == 0) {
                return null;
            }

            InvocationRecord rtn = tail;
            tail = tail.getQprev();
            if (tail == null) {
                head = null;
            } else {
                tail.setQnext(null);
            }
            length--;

            rtn.setQnext(null);
            rtn.setQprev(null);
            return rtn;
        }
    }

    public void addToHead(InvocationRecord o) {
        synchronized (cashmere) {
            if (length == 0) {
                head = tail = o;
            } else {
                o.setQnext(head);
                head.setQprev(o);
                head = o;
            }
            length++;
        }
    }

    public void addToTail(InvocationRecord o) {
        synchronized (cashmere) {
            if (length == 0) {
                head = tail = o;
            } else {
                o.setQprev(tail);
                tail.setQnext(o);
                tail = o;
            }
            length++;
        }
    }

    private void removeElement(InvocationRecord curr) {
        // curr MUST be in q.
        if (ASSERTS) {
            Cashmere.assertLocked(cashmere);
        }
        if (curr.getQprev() != null) {
            curr.getQprev().setQnext(curr.getQnext());
        } else {
            head = curr.getQnext();
            if (head != null) {
                head.setQprev(null);
            }
        }

        if (curr.getQnext() != null) {
            curr.getQnext().setQprev(curr.getQprev());
        } else {
            tail = curr.getQprev();
            if (tail != null) {
                tail.setQnext(null);
            }
        }
        length--;
    }

    public int size() {
        synchronized (cashmere) {
            return length;
        }
    }

    public void killChildrenOf(Stamp targetStamp) {
        if (ASSERTS) {
            Cashmere.assertLocked(cashmere);
        }

        InvocationRecord curr = tail;
        while (curr != null) {
            if (curr.aborted) {
                curr = curr.getQprev();
                // This is correct, even if curr was just removed.
                continue; // already handled.
            }

            if ((curr.getParent() != null && curr.getParent().aborted)
                || curr.isDescendentOf(targetStamp)) {

                if (abortLogger.isDebugEnabled()) {
                    abortLogger.debug("found local child: " + curr.getStamp()
                        + ", it depends on " + targetStamp);
                }

                curr.decrSpawnCounter();
                cashmere.stats.abortedJobs++;
                curr.aborted = true;

                // Curr is removed, but not put back in cache.
                // this is OK. Moreover, it might have children,
                // so we should keep it alive.
                // cleanup is done inside the spawner itself.
                removeElement(curr);
            }

            curr = curr.getQprev();
            // This is correct, even if curr was just removed.
        }
    }

    /**
     * Used for fault-tolerance Aborts all the descendents of any job stolen for
     * the given (crashed) processor
     * 
     * @param owner
     *            IbisIdentifier of the processor whose jobs (and their
     *            descendents) will be aborted
     */

    public void killSubtreeOf(ibis.ipl.IbisIdentifier owner) {
        if (ASSERTS) {
            Cashmere.assertLocked(cashmere);
        }

        InvocationRecord curr = tail;
        while (curr != null) {
            if (curr.aborted) {
                curr = curr.getQprev();
                // This is correct, even if curr was just removed.
                continue; // already handled.
            }

            if ((curr.getParent() != null && curr.getParent().aborted)
                || curr.isDescendentOf(owner) || curr.getOwner().equals(owner)) {
                //shouldn't happen
                curr.decrSpawnCounter();

                curr.aborted = true;

                cashmere.stats.abortedJobs++;
                cashmere.stats.killedOrphans++;
                removeElement(curr);
            }

            curr = curr.getQprev();
        }
    }

    public void print(java.io.PrintStream out) {
        out.println("=Q " + cashmere.ident + ":=======================");
        InvocationRecord curr = head;
        while (curr != null) {
            out.println("    " + curr);
            curr = curr.getQnext();
        }
        out.println("=end of Q=======================");
    }
}
