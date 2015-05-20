/*
 * Created on Apr 26, 2006 by rob
 */
package ibis.cashmere.impl.aborts;

import ibis.ipl.IbisIdentifier;
import ibis.ipl.ReadMessage;
import ibis.ipl.WriteMessage;
import ibis.cashmere.impl.Config;
import ibis.cashmere.impl.Cashmere;
import ibis.cashmere.impl.communication.Protocol;
import ibis.cashmere.impl.loadBalancing.Victim;
import ibis.cashmere.impl.spawnSync.InvocationRecord;
import ibis.cashmere.impl.spawnSync.Stamp;

import java.io.IOException;
import java.util.ArrayList;

final class AbortsCommunication implements Config {

    static final class StampListElement {
        Stamp stamp;

        IbisIdentifier stealer;
    }

    final class AbortMessageSender extends Thread {
        AbortMessageSender() {
            setDaemon(true);
            setName("Cashmere AbortRequestHandler");
        }

        public void run() {
            for (;;) {
                StampListElement e;
                synchronized(stampsToAbortList) {
                    while (stampsToAbortList.size() == 0) {
                        try {
                            stampsToAbortList.wait();
                        } catch (Exception x) {
                            // ignore
                        }
                    }
                    e = stampsToAbortList.get(0);
                    // Don't remove it yet! Remove it after the send,
                    // otherwise a steal reply message may overtake
                    // an abort message.
                }
                soRealSendAbortMessage(e);
                synchronized(stampsToAbortList) {
                    stampsToAbortList.remove(0);
                    if (stampsToAbortList.size() == 0) {
                        stampsToAbortList.notifyAll();
                    }
                }
            }
        }
    }

    private Cashmere s;

    private ArrayList<StampListElement> stampsToAbortList =
            new ArrayList<StampListElement>();

    AbortsCommunication(Cashmere s) {
        this.s = s;
        new AbortMessageSender().start();
    }

    protected void sendAbortMessage(InvocationRecord r) {
        if (s.deadIbises.contains(r.getStealer())) {
            /* don't send abort and store messages to crashed ibises */
            return;
        }

        abortLogger.debug("CASHMERE '" + s.ident + ": sending abort message to: "
                + r.getStealer() + " for job " + r.getStamp() + ", parent = "
                + r.getParentStamp());

        StampListElement e = new StampListElement();
        e.stamp = r.getParentStamp();
        e.stealer = r.getStealer();

        synchronized (stampsToAbortList) {
            stampsToAbortList.add(e);
            stampsToAbortList.notifyAll();
        }
    }

    protected void waitForAborts() {
        synchronized (stampsToAbortList) {
            while (stampsToAbortList.size() != 0) {
                try {
                    stampsToAbortList.wait();
                } catch(Exception e) {
                    // ignored
                }
            }
        }
    }

    /*
     * message combining for abort messages does not work (I tried). It is very
     * unlikely that one node stole more than one job from me
     */
    protected void soRealSendAbortMessage(StampListElement e) {
        WriteMessage writeMessage = null;
        try {
            Victim v = null;
            synchronized (s) {
                v = s.victims.getVictim(e.stealer);
                if (v == null)
                    return; // node might have crashed
            }

            writeMessage = v.newMessage();
            writeMessage.writeByte(Protocol.ABORT);
            writeMessage.writeObject(e.stamp);
            v.finish(writeMessage);
        } catch (IOException x) {
            if (writeMessage != null) {
                writeMessage.finish(x);
            }
            abortLogger.warn("CASHMERE '" + s.ident
                    + "': Got Exception while sending abort message (continuing): " + x, x);
            // This should not be a real problem, it is just inefficient.
            // Let's continue...
        }
    }

    protected void handleAbort(ReadMessage m) {
        try {
            Stamp stamp = (Stamp) m.readObject();
            synchronized (s) {
                s.aborts.addToAbortList(stamp);
            }
            // m.finish();
        } catch (IOException e) {
            abortLogger.error("CASHMERE '" + s.ident
                    + "': got exception while reading job result: " + e, e);
        } catch (ClassNotFoundException e1) {
            abortLogger.error("CASHMERE '" + s.ident
                    + "': got exception while reading job result: " + e1, e1);
        }
    }
}
