import ibis.ipl.Ibis;
import ibis.ipl.IbisCapabilities;
import ibis.ipl.IbisFactory;
import ibis.ipl.IbisIdentifier;
import ibis.ipl.PortType;
import ibis.ipl.Registry;
import ibis.ipl.SendPort;
import ibis.ipl.WriteMessage;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Properties;

class BodyList {

    private float[] bodies;

    int iteration;

    long runTime;

    BodyList(float[] bodies, int iteration, long runTime) {
        this.bodies = bodies;
        this.iteration = iteration;
        this.runTime = runTime;
    }

    float[] getBodies() {
        return bodies;
    }

    int getIteration() {
        return iteration;
    }

    public long getRunTime() {
        return runTime;
    }

    public void setRunTime(long runTime) {
        this.runTime = runTime;
    }
}

public class RemoteVisualization extends Thread {

    private int maxLen = 10;

    private Ibis ibis;

    private PortType t;

    private boolean haveClient = false;

    private boolean stop = false;

    private boolean quit = false;

    private SendPort sport;

    private DataOutputStream out;

    private LinkedList<BodyList> list = new LinkedList<BodyList>();

    public RemoteVisualization() {
        init();
        start();
    }

    public RemoteVisualization(String file) throws IOException {
        haveClient = true;
        out = new DataOutputStream(new BufferedOutputStream(
            new FileOutputStream(file), 128 * 1024));
        start();
    }

    public synchronized void stopRemoteViz() {
        stop = true;
        notifyAll();
    }

    private void init() {
        try {
            IbisCapabilities s = new IbisCapabilities(
                IbisCapabilities.ELECTIONS_STRICT);
            t = new PortType(PortType.SERIALIZATION_DATA,
                PortType.COMMUNICATION_RELIABLE,
                PortType.CONNECTION_ONE_TO_ONE, PortType.RECEIVE_EXPLICIT);

            Properties p = new Properties();
            // p.setProperty("ibis.serialization", "ibis");
            p.setProperty("ibis.pool.name", "nbody-viz");

            ibis = IbisFactory.createIbis(s, p, true, null, t);
        } catch (Exception e) {
            System.err.println("failed to init ibis: " + e);
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void connect() {
        try {
            System.err.println("connecting...");
            Registry registry = ibis.registry();
            System.err.println("got reg");

//            IbisIdentifier vizHost = registry.elect("nbodyViz");
            IbisIdentifier vizHost = registry.getElectionResult("nbodyViz");
            System.err.println("got election res: " + vizHost);
            sport = ibis.createSendPort(t, "nbody-viz-port");
            sport.connect(vizHost, "nbody-viz-port", 10*1000, false);
            System.err.println("port connected");
            haveClient = true;
        } catch (Exception e) {
            if(sport != null) {
                try {
                    sport.close();
                } catch (IOException x) {
                    // ignore
                }
            }
            System.err.println("failed connect: " + e);
            e.printStackTrace();
        }
    }

    private void close() {
        try {
            if(out != null) out.close();
        } catch (Throwable e) {
            // ignore            
        }

        if(sport != null) {
            try {
                sport.close();
            } catch (IOException x) {
                // ignore
            }
        }

        if(ibis != null) {
            try {
                ibis.end();
            } catch (IOException x) {
                // ignore
            }
        }

        haveClient = false;
    }

    public void showBodies(float[] positions, int iteration, long runtime) {

        // Check if the buffer is already full. If so, we skip this round!
        synchronized (this) {
            if (list.size() > maxLen) {
                return;
            }
        }

        // And store it...
        synchronized (this) {
            list.addLast(new BodyList(positions, iteration, runtime));
            notifyAll();
        }
    }

    private synchronized BodyList getBodies() {

        while (list.size() == 0 && ! stop) {
            try {
                wait();
            } catch (Exception e) {
                // ignore
            }
        }

        if (list.size() == 0) {
            quit = true;
            return null;
        }

        return list.removeFirst();
    }

    private void writeToFile() {
        long start = 0;
        try {
            //  System.out.println("Sending");

            BodyList bodies = getBodies();

            if (bodies == null) {
                return;
            }

            start = System.currentTimeMillis();

            out.writeInt(bodies.getBodies().length / 3);
            out.writeInt(bodies.getIteration());
            out.writeLong(bodies.getRunTime());

            float[] b = bodies.getBodies();
            for (int i = 0; i < b.length; i++) {
                out.writeFloat(b[i]);
            }

            long time = System.currentTimeMillis() - start;
            System.err.println("writes took " + time + " ms");

            out.flush();

            //   System.out.println("Sending Done");

        } catch (Exception e) {
            System.out.println("Lost connection during send!");
            close();
        }
        long time = System.currentTimeMillis() - start;
        System.err.println("send took " + time + " ms");
    }

    private void send() {
        long start = 0;
        try {
//            System.out.println("Sending");

            BodyList bodies = getBodies();

            if (bodies == null) {
                return;
            }

            start = System.currentTimeMillis();

            WriteMessage m = sport.newMessage();
            
            m.writeInt(bodies.getBodies().length / 3);
            m.writeInt(bodies.getIteration());
            m.writeLong(bodies.getRunTime());

            float[] b = bodies.getBodies();
            m.writeArray(b);
            m.finish();
            
            long time = System.currentTimeMillis() - start;
            System.err.println("writes took " + time + " ms");


//            System.out.println("Sending Done");

        } catch (Exception e) {
            System.out.println("Lost connection during send!");
            if(sport != null) {
                try {
                    sport.close();
                } catch (IOException x) {
                    // ignore
                }
            }
            haveClient = false;
        }
        long time = System.currentTimeMillis() - start;
        System.err.println("send took " + time + " ms");
    }

    private void doSend() {
        if(out != null) {
            writeToFile();
        } else {
            send();
        }
    }
    
    public void run() {

        while (! quit) {

            if (!haveClient) {
                connect();
            }

            doSend();
        }

        close();
    }
}
