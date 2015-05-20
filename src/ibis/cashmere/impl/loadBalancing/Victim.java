/* $Id: Victim.java 13181 2011-03-30 14:50:04Z ceriel $ */

package ibis.cashmere.impl.loadBalancing;

import ibis.ipl.IbisIdentifier;
import ibis.ipl.ReceivePortIdentifier;
import ibis.ipl.SendPort;
import ibis.ipl.WriteMessage;
import ibis.cashmere.impl.Config;
import ibis.cashmere.impl.Cashmere;
import ibis.cashmere.impl.communication.Communication;

import java.io.IOException;

/**
 * 
 * @author rob
 *
 * A Victim represents an Ibis we can steal work from.
 * This class is immutable, only the sendport itself could be connected and 
 * disconnected.
 *  
 */
public final class Victim implements Config {

    // @@@ TODO should be synchronized on static object, not sendport! 
    private static volatile int connectionCount = 0;

    private IbisIdentifier ident;

    private SendPort sendPort;

    private ReceivePortIdentifier r;

    private boolean connected = false;

    private boolean closed = false;

    private final boolean inDifferentCluster;

    private int referenceCount = 0;
    
    public Victim(IbisIdentifier ident, SendPort s) {
        this.ident = ident;
        this.sendPort = s;
        if (s != null) {
            inDifferentCluster = !clusterOf(ident).equals(clusterOf(s.identifier().ibisIdentifier()));
        } else {
            inDifferentCluster = false;
        }
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof Victim) {
            Victim other = (Victim) o;
            return other.ident.equals(ident);
        }
        return false;
    }

    public boolean equals(Victim other) {
        if (other == this) {
            return true;
        }
        return other.ident.equals(ident);
    }

    public int hashCode() {
        return ident.hashCode();
    }

    public String toString() {
        return ident.toString();
    }
    
    public boolean isConnected() {
        return connected;
    }

    private void disconnect() throws IOException {
        if (connected) {
            connected = false;
            connectionCount--;
            sendPort.disconnect(r);
            if (commLogger.isDebugEnabled()) {
        	commLogger.debug("CASHMERE '" + sendPort.identifier().ibisIdentifier()
                        + "': disconnected from " + ident);
            }
        }
    }

    public void connect() {
        synchronized (sendPort) {
            getSendPort();
        }
    }
    
    private SendPort getSendPort() {
        if (closed) {
            return null;
        }

        if (!connected) {
            if (commLogger.isDebugEnabled()) {
        	commLogger.debug("CASHMERE '" + sendPort.identifier().ibisIdentifier()
                        + "': connecting to " + ident);
            }
            r = Communication.connect(sendPort, ident, "cashmere port",
                Cashmere.CONNECT_TIMEOUT);
            if (r == null) {
                commLogger.warn("CASHMERE '" + sendPort.identifier().ibisIdentifier()
                    + "': unable to connect to " + ident
                    + ", might have crashed");
                return null;
            }
            connected = true;
            connectionCount++;
        }
        
        return sendPort;
    }

    public WriteMessage newMessage() throws IOException {
        SendPort send;
        synchronized (sendPort) {
            send = getSendPort();
            if (send != null) {
                referenceCount++;
            } else {
                throw new IOException("CASHMERE '" + sendPort.identifier().ibisIdentifier()
                        + "': Could not connect to " + ident);
            }
        }
        return send.newMessage();
    }

    public long finish(WriteMessage m) throws IOException {
        try {
            long cnt = m.finish();
            if (inDifferentCluster) {
                Cashmere.addInterClusterStats(cnt);
            } else {
                Cashmere.addIntraClusterStats(cnt);
            }
            return cnt;
        } catch(IOException e) {
            finish(m, e);
            throw e;
        } catch(Throwable e) {
            commLogger.error("Got exception in finish", e);
            IOException ex = new IOException();
            ex.initCause(e);
             throw ex;
        }  finally {
            synchronized(sendPort) {
                referenceCount--;
                optionallyDropConnection();
            }
        }
    }
    
         
    public void finish(WriteMessage m, IOException e) {
        m.finish(e);
    }


    private void optionallyDropConnection() throws IOException {
        if (CLOSE_CONNECTIONS) {
            if (referenceCount == 0) {
                if (KEEP_INTRA_CONNECTIONS) {
                    if (inDifferentCluster) {
                        disconnect();
                    }
                    return;
                } 
                if (connectionCount >= MAX_CONNECTIONS) {
                    disconnect();
                }
            }
        }
    }

    public void close() {
        synchronized (sendPort) {
            if (connected) {
                connected = false;
                connectionCount--;
            }
            closed = true;
            try {
                sendPort.close();
            } catch (Exception e) {
                // ignore
                Config.commLogger.warn("CASHMERE '" + sendPort.identifier().ibisIdentifier()
                    + "': port.close() throws exception (ignored), dest = " + ident, e);
            }
        }
    }

    public IbisIdentifier getIdent() {
        return ident;
    }

    public static String clusterOf(IbisIdentifier id) {
        return id.location().getParent().toString();
    }
}
