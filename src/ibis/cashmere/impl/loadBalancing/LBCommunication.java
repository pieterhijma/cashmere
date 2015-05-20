/*
 * Created on Apr 26, 2006 by rob
 */
package ibis.cashmere.impl.loadBalancing;

import ibis.cashmere.impl.Cashmere;
import ibis.cashmere.impl.Config;
import ibis.cashmere.impl.communication.Communication;
import ibis.cashmere.impl.communication.Protocol;
import ibis.cashmere.impl.faultTolerance.GlobalResultTableValue;
import ibis.cashmere.impl.spawnSync.InvocationRecord;
import ibis.cashmere.impl.spawnSync.ReturnRecord;
import ibis.cashmere.impl.spawnSync.Stamp;
import ibis.cashmere.many_core.MCCashmere;
import ibis.cashmere.many_core.MCTimer;
import ibis.ipl.IbisIdentifier;
import ibis.ipl.ReadMessage;
import ibis.ipl.SendPortIdentifier;
import ibis.ipl.WriteMessage;
import ibis.util.Timer;

import java.io.IOException;
import java.io.InvalidClassException;
import java.io.NotSerializableException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class LBCommunication implements Config, Protocol {

    public static final Logger bbLogger = LoggerFactory
	    .getLogger("ibis.cashmere.steal.bytebuffer");

    private Cashmere s;

    private LoadBalancing lb;
    private MCTimer timer;

    protected LBCommunication(Cashmere s, LoadBalancing lb) {
	this.s = s;
	this.lb = lb;
	this.timer = MCCashmere.createTimer("java", "loadbalance",
		"send input", 25);
    }

    protected void sendStealRequest(Victim v, boolean synchronous,
	    boolean blockUntilWorkIsAvailable) throws IOException {
	if (stealLogger.isDebugEnabled()) {
	    stealLogger.debug("CASHMERE '" + s.ident + "': sending "
		    + (synchronous ? "SYNC" : "ASYNC") + "steal message to "
		    + v.getIdent());
	}

	WriteMessage writeMessage = v.newMessage();
	byte opcode = -1;

	if (synchronous) {
	    if (blockUntilWorkIsAvailable) {
		opcode = Protocol.BLOCKING_STEAL_REQUEST;
	    } else {
		if (!FT_NAIVE) {
		    synchronized (s) {
			if (s.ft.getTable) {
			    opcode = Protocol.STEAL_AND_TABLE_REQUEST;
			} else {
			    opcode = Protocol.STEAL_REQUEST;
			}
		    }
		} else {
		    opcode = Protocol.STEAL_REQUEST;
		}
	    }
	} else {
	    if (!FT_NAIVE) {
		synchronized (s) {
		    if (s.clusterCoordinator && s.ft.getTable) {
			opcode = Protocol.ASYNC_STEAL_AND_TABLE_REQUEST;
		    } else {
			if (s.ft.getTable) {
			    if (grtLogger.isInfoEnabled()) {
				grtLogger.info("CASHMERE '" + s.ident
					+ ": EEEK sending async steal message "
					+ "while waiting for table!!");
			    }
			}
			opcode = Protocol.ASYNC_STEAL_REQUEST;
		    }
		}
	    } else {
		opcode = Protocol.ASYNC_STEAL_REQUEST;
	    }
	}

	try {
	    writeMessage.writeByte(opcode);
	    v.finish(writeMessage);
	} catch (IOException e) {
	    writeMessage.finish(e);
	    throw e;
	}
    }

    protected void handleJobResult(ReadMessage m, int opcode) {
	ReturnRecord rr = null;
	Stamp stamp = null;
	Throwable eek = null;
	Timer returnRecordReadTimer = null;
	boolean gotException = false;

	if (stealLogger.isInfoEnabled()) {
	    stealLogger.info("CASHMERE '" + s.ident
		    + "': got job result message from "
		    + m.origin().ibisIdentifier());
	}

	// This upcall may run in parallel with other upcalls.
	// Therefore, we cannot directly use the timer in Cashmere.
	// Use our own local timer, and add the result to the global timer
	// later.

	returnRecordReadTimer = Timer.createTimer();
	returnRecordReadTimer.start();
	int eventNo = timer.start("receive output");
	try {
	    if (opcode == JOB_RESULT_NORMAL) {
		rr = (ReturnRecord) m.readObject();
		stamp = rr.getStamp();
		eek = rr.getEek();
		int nByteBuffers = m.readInt();
		if (nByteBuffers > 0) {

		    List<ByteBuffer> l = new ArrayList<ByteBuffer>();
		    if (bbLogger.isDebugEnabled()) {
			bbLogger.debug("ReturnRecord: reading " + nByteBuffers
				+ " bytebuffers from "
				+ m.origin().ibisIdentifier());
		    }
		    for (int i = 0; i < nByteBuffers; i++) {
			int capacity = m.readInt();
			ByteBuffer b = MCCashmere
				.getByteBuffer(capacity, false);
			// ByteBuffer b = ByteBuffer.wrap(new
			// byte[capacity]).order(ByteOrder.nativeOrder());
			l.add(b);
		    }
		    for (ByteBuffer b : l) {
			if (stealLogger.isDebugEnabled()) {
			    stealLogger
				    .debug("ReturnRecord: reading bytebuffer of size "
					    + b.capacity());
			}
			b.position(0);
			b.limit(b.capacity());
			m.readByteBuffer(b);
			if (bbLogger.isDebugEnabled()) {
			    bbLogger.debug("ReturnRecord: read bytebuffer of size "
				    + b.capacity());
			}
		    }
		    rr.setByteBuffers(l);
		}
	    } else {
		eek = (Throwable) m.readObject();
		stamp = (Stamp) m.readObject();
	    }
	    timer.addBytes(m.bytesRead(), eventNo);
	    // m.finish();
	} catch (Exception e) {
	    spawnLogger.error("CASHMERE '" + s.ident
		    + "': got exception while reading job result: " + e
		    + opcode, e);
	    gotException = true;
	} finally {
	    timer.stop(eventNo);
	    returnRecordReadTimer.stop();
	}
	s.stats.returnRecordReadTimer.add(returnRecordReadTimer);

	if (gotException) {
	    return;
	}

	if (stealLogger.isInfoEnabled()) {
	    if (eek != null) {
		stealLogger.info("CASHMERE '" + s.ident
			+ "': handleJobResult: exception result: " + eek
			+ ", stamp = " + stamp, eek);
	    } else {
		stealLogger
			.info("CASHMERE '" + s.ident
				+ "': handleJobResult: normal result, stamp = "
				+ stamp);
	    }
	}

	lb.addJobResult(rr, eek, stamp);
    }

    protected void sendResult(InvocationRecord r, ReturnRecord rr) {
	if (/* exiting || */r.alreadySentExceptionResult()) {
	    return;
	}

	if (stealLogger.isInfoEnabled()) {
	    stealLogger.info("CASHMERE '" + s.ident
		    + "': sending job result to " + r.getOwner()
		    + ", exception = "
		    + (r.eek == null ? "null" : ("" + r.eek)) + ", stamp = "
		    + r.getStamp());
	}

	Victim v = null;

	synchronized (s) {
	    if (!FT_NAIVE && r.isOrphan()) {
		IbisIdentifier owner = s.ft.lookupOwner(r);
		if (ASSERTS && owner == null) {
		    grtLogger.error("CASHMERE '" + s.ident
			    + "': orphan not locked in the table");
		    System.exit(1); // Failed assertion
		}
		r.setOwner(owner);
		if (grtLogger.isInfoEnabled()) {
		    grtLogger.info("CASHMERE '" + s.ident
			    + "': storing an orphan");
		}
		s.ft.storeResult(r);
	    }
	    v = s.victims.getVictim(r.getOwner());
	}

	if (v == null) {
	    // probably crashed..
	    if (!FT_NAIVE && !r.isOrphan()) {
		synchronized (s) {
		    s.ft.storeResult(r);
		}
		if (grtLogger.isInfoEnabled()) {
		    grtLogger.info("CASHMERE '" + s.ident
			    + "': a job became an orphan??");
		}
	    }
	    return;
	}

	s.stats.returnRecordWriteTimer.start();
	int eventNo = timer.start("send output");
	WriteMessage writeMessage = null;
	try {
	    List<ByteBuffer> l = null;
	    writeMessage = v.newMessage();
	    if (r.eek == null) {
		writeMessage.writeByte(Protocol.JOB_RESULT_NORMAL);
		writeMessage.writeObject(rr);
		l = rr.getByteBuffers();
		if (l != null) {
		    writeMessage.writeInt(l.size());
		    if (bbLogger.isDebugEnabled()) {
			bbLogger.debug("ReturnRecord: writing " + l.size()
				+ " bytebuffers to " + v.getIdent());
		    }
		    for (ByteBuffer b : l) {
			b.position(0);
			b.limit(b.capacity());
			writeMessage.writeInt(b.capacity());
		    }
		    for (ByteBuffer b : l) {
			if (stealLogger.isDebugEnabled()) {
			    stealLogger
				    .debug("ReturnRecord: writing bytebuffer of size "
					    + b.capacity());
			}
			writeMessage.writeByteBuffer(b);
			if (bbLogger.isDebugEnabled()) {
			    bbLogger.debug("ReturnRecord: written bytebuffer of size "
				    + b.capacity());
			}
		    }
		} else {
		    writeMessage.writeInt(0);
		}
	    } else {
		if (rr == null) {
		    r.setAlreadySentExceptionResult(true);
		}
		writeMessage.writeByte(Protocol.JOB_RESULT_EXCEPTION);
		writeMessage.writeObject(r.eek);
		writeMessage.writeObject(r.getStamp());
	    }

	    long cnt = v.finish(writeMessage);
	    if (l != null) {
		for (ByteBuffer b : l) {
		    MCCashmere.releaseByteBuffer(b);
		}
	    }
	    List<ByteBuffer> li = r.getByteBuffers();
	    if (li != null) {
		for (ByteBuffer b : li) {
		    boolean found = false;
		    for (ByteBuffer b1 : l) {
			if (b == b1) {
			    found = true;
			    break;
			}
		    }
		    if (!found) {
			MCCashmere.releaseByteBuffer(b);
		    }
		}
	    }
	    s.stats.returnRecordBytes += cnt;
	    timer.addBytes(cnt, eventNo);
	} catch (IOException e) {
	    if (writeMessage != null) {
		writeMessage.finish(e);
	    }
	    if (e instanceof NotSerializableException
		    || e instanceof InvalidClassException) {
		ftLogger.warn(
			"CASHMERE '"
				+ s.ident
				+ "': Got exception while sending result of stolen job",
			e);
	    } else if (ftLogger.isInfoEnabled()) {
		ftLogger.info(
			"CASHMERE '"
				+ s.ident
				+ "': Got exception while sending result of stolen job",
			e);
	    }
	} finally {
	    s.stats.returnRecordWriteTimer.stop();
	    timer.stop(eventNo);
	}
    }

    protected void handleStealRequest(SendPortIdentifier ident, int opcode) {

	// This upcall may run in parallel with other upcalls.
	// Therefore, we cannot directly use the handleSteal timer in Cashmere.
	// Use our own local timer, and add the result to the global timer
	// later.
	// Not needed when steals are queued.

	Timer handleStealTimer = null;
	if (QUEUE_STEALS) {
	    s.stats.handleStealTimer.start();
	} else {
	    handleStealTimer = Timer.createTimer();
	    handleStealTimer.start();
	}
	s.stats.stealRequests++;

	try {

	    if (stealLogger.isDebugEnabled()) {
		stealLogger.debug("CASHMERE '" + s.ident
			+ "': dealing with steal request from "
			+ ident.ibisIdentifier() + " opcode = "
			+ Communication.opcodeToString(opcode));
	    }

	    InvocationRecord result = null;
	    Victim v = null;
	    Map<Stamp, GlobalResultTableValue> table = null;

	    synchronized (s) {
		v = s.victims.getVictim(ident.ibisIdentifier());
		if (v == null || s.deadIbises.contains(ident.ibisIdentifier())) {
		    // this message arrived after the crash of its sender was
		    // detected. Is this actually possible?
		    stealLogger.warn("CASHMERE '" + s.ident
			    + "': EEK!! got steal request from a dead ibis: "
			    + ident.ibisIdentifier());
		    return;
		}

		try {
		    result = lb.stealJobFromLocalQueue(ident,
			    opcode == BLOCKING_STEAL_REQUEST);
		} catch (IOException e) {
		    stealLogger.warn("CASHMERE '" + s.ident
			    + "': EEK!! got exception during steal request: "
			    + ident.ibisIdentifier());
		    return; // the stealing ibis died
		}

		if (!FT_NAIVE
			&& (opcode == STEAL_AND_TABLE_REQUEST || opcode == ASYNC_STEAL_AND_TABLE_REQUEST)) {
		    if (!s.ft.getTable) {
			table = s.ft.getContents();
		    }
		}
	    }

	    if (result == null) {
		sendStealFailedMessage(ident, opcode, v, table);
		return;
	    }

	    // we stole a job
	    sendStolenJobMessage(ident, opcode, v, result, table);
	} finally {
	    if (QUEUE_STEALS) {
		s.stats.handleStealTimer.stop();
	    } else {
		handleStealTimer.stop();
		s.stats.handleStealTimer.add(handleStealTimer);
	    }
	}
    }

    // Here, the timing code is OK, the upcall cannot run in parallel
    // (readmessage is not finished).
    protected void handleReply(ReadMessage m, int opcode) {
	SendPortIdentifier ident = m.origin();
	InvocationRecord tmp = null;

	if (stealLogger.isDebugEnabled()) {
	    stealLogger.debug("CASHMERE '" + s.ident
		    + "': got steal reply message from "
		    + ident.ibisIdentifier() + ": "
		    + Communication.opcodeToString(opcode));
	}

	switch (opcode) {
	case STEAL_REPLY_SUCCESS_TABLE:
	case ASYNC_STEAL_REPLY_SUCCESS_TABLE:
	    readAndAddTable(ident, m, opcode);
	    // fall through
	case STEAL_REPLY_SUCCESS:
	case ASYNC_STEAL_REPLY_SUCCESS:
	    s.stats.invocationRecordReadTimer.start();
	    int eventNo = timer.start("receive input");
	    try {
		tmp = (InvocationRecord) m.readObject();
		int nByteBuffers = m.readInt();
		if (bbLogger.isDebugEnabled()) {
		    bbLogger.debug("Reading " + nByteBuffers
			    + " bytebuffers from "
			    + m.origin().ibisIdentifier());
		}
		if (nByteBuffers > 0) {
		    List<ByteBuffer> l = new ArrayList<ByteBuffer>();
		    for (int i = 0; i < nByteBuffers; i++) {
			int capacity = m.readInt();
			ByteBuffer b = MCCashmere
				.getByteBuffer(capacity, false);
			// ByteBuffer b = ByteBuffer.wrap(new
			// byte[capacity]).order(ByteOrder.nativeOrder());
			l.add(b);
		    }
		    for (ByteBuffer b : l) {
			/*
			 * if (bb.isDebugEnabled()) {
			 * bbLogger.debug("Reading bytebuffer of size " +
			 * b.capacity()); }
			 */
			b.position(0);
			b.limit(b.capacity());
			m.readByteBuffer(b);
			if (bbLogger.isDebugEnabled()) {
			    bbLogger.debug("Read bytebuffer of size "
				    + b.capacity());
			}
		    }
		    tmp.setByteBuffers(l);
		}
		timer.addBytes(m.bytesRead(), eventNo);

		if (ASSERTS && tmp.aborted) {
		    stealLogger.warn("CASHMERE '" + s.ident
			    + ": stole aborted job!");
		}
	    } catch (Exception e) {
		stealLogger.error("CASHMERE '" + s.ident
			+ "': Got Exception while reading steal "
			+ "reply from " + ident + ", opcode:" + opcode
			+ ", exception: " + e, e);
	    } finally {
		s.stats.invocationRecordReadTimer.stop();
		timer.stop(eventNo);
	    }

	    synchronized (s) {
		if (s.deadIbises.contains(ident)) {
		    // this message arrived after the crash of its sender
		    // was detected. Is this actually possible?
		    stealLogger.error("CASHMERE '" + s.ident
			    + "': got reply from dead ibis??? Ignored");
		    break;
		}
	    }

	    s.algorithm.stealReplyHandler(tmp, ident.ibisIdentifier(), opcode);
	    break;

	case STEAL_REPLY_FAILED_TABLE:
	case ASYNC_STEAL_REPLY_FAILED_TABLE:
	    readAndAddTable(ident, m, opcode);
	    // fall through
	case STEAL_REPLY_FAILED:
	case ASYNC_STEAL_REPLY_FAILED:
	    s.algorithm.stealReplyHandler(null, ident.ibisIdentifier(), opcode);
	    break;
	default:
	    stealLogger.error("INTERNAL ERROR, opcode = " + opcode);
	    break;
	}
    }

    private void readAndAddTable(SendPortIdentifier ident, ReadMessage m,
	    int opcode) {
	try {
	    @SuppressWarnings("unchecked")
	    Map<Stamp, GlobalResultTableValue> table = (Map<Stamp, GlobalResultTableValue>) m
		    .readObject();
	    if (table != null) {
		synchronized (s) {
		    s.ft.getTable = false;
		    s.ft.addContents(table);
		}
	    }
	} catch (Exception e) {
	    stealLogger.error("CASHMERE '" + s.ident
		    + "': Got Exception while reading steal " + "reply from "
		    + ident + ", opcode:" + +opcode + ", exception: " + e, e);
	}
    }

    private void sendStealFailedMessage(SendPortIdentifier ident, int opcode,
	    Victim v, Map<Stamp, GlobalResultTableValue> table) {

	if (stealLogger.isDebugEnabled()) {
	    if (opcode == ASYNC_STEAL_REQUEST) {
		stealLogger
			.debug("CASHMERE '" + s.ident
				+ "': sending FAILED back to "
				+ ident.ibisIdentifier());
	    }
	    if (opcode == ASYNC_STEAL_AND_TABLE_REQUEST) {
		stealLogger.debug("CASHMERE '" + s.ident
			+ "': sending FAILED_TABLE back to "
			+ ident.ibisIdentifier());
	    }
	}

	WriteMessage m = null;
	try {
	    m = v.newMessage();
	    if (opcode == STEAL_REQUEST || opcode == BLOCKING_STEAL_REQUEST) {
		m.writeByte(STEAL_REPLY_FAILED);
	    } else if (opcode == ASYNC_STEAL_REQUEST) {
		m.writeByte(ASYNC_STEAL_REPLY_FAILED);
	    } else if (opcode == STEAL_AND_TABLE_REQUEST) {
		if (table != null) {
		    m.writeByte(STEAL_REPLY_FAILED_TABLE);
		    m.writeObject(table);
		} else {
		    m.writeByte(STEAL_REPLY_FAILED);
		}
	    } else if (opcode == ASYNC_STEAL_AND_TABLE_REQUEST) {
		if (table != null) {
		    m.writeByte(ASYNC_STEAL_REPLY_FAILED_TABLE);
		    m.writeObject(table);
		} else {
		    m.writeByte(ASYNC_STEAL_REPLY_FAILED);
		}
	    } else {
		stealLogger.error("UNHANDLED opcode " + opcode
			+ " in handleStealRequest");
	    }

	    v.finish(m);

	    if (stealLogger.isDebugEnabled()) {
		stealLogger.debug("CASHMERE '" + s.ident
			+ "': sending FAILED back to " + ident.ibisIdentifier()
			+ " DONE");
	    }
	} catch (IOException e) {
	    if (m != null) {
		m.finish(e);
	    }
	    stealLogger
		    .warn("CASHMERE '"
			    + s.ident
			    + "': trying to send FAILURE back, but got exception: "
			    + e, e);
	}
    }

    private void sendStolenJobMessage(SendPortIdentifier ident, int opcode,
	    Victim v, InvocationRecord result,
	    Map<Stamp, GlobalResultTableValue> table) {
	if (ASSERTS && result.aborted) {
	    stealLogger.warn("CASHMERE '" + s.ident
		    + ": trying to send aborted job!");
	}

	s.stats.stolenJobs++;

	if (stealLogger.isInfoEnabled()) {
	    stealLogger.info("CASHMERE '" + s.ident
		    + "': sending SUCCESS and job #" + result.getStamp()
		    + " back to " + ident.ibisIdentifier());
	}

	WriteMessage m = null;
	try {
	    m = v.newMessage();
	    int eventNo = timer.start();
	    if (opcode == STEAL_REQUEST || opcode == BLOCKING_STEAL_REQUEST) {
		m.writeByte(STEAL_REPLY_SUCCESS);
	    } else if (opcode == ASYNC_STEAL_REQUEST) {
		m.writeByte(ASYNC_STEAL_REPLY_SUCCESS);
	    } else if (opcode == STEAL_AND_TABLE_REQUEST) {
		if (table != null) {
		    m.writeByte(STEAL_REPLY_SUCCESS_TABLE);
		    m.writeObject(table);
		} else {
		    stealLogger.warn("CASHMERE '" + s.ident
			    + "': EEK!! sending a job but not a table !?");
		}
	    } else if (opcode == ASYNC_STEAL_AND_TABLE_REQUEST) {
		if (table != null) {
		    m.writeByte(ASYNC_STEAL_REPLY_SUCCESS_TABLE);
		    m.writeObject(table);
		} else {
		    stealLogger.warn("CASHMERE '" + s.ident
			    + "': EEK!! sending a job but not a table !?");
		}
	    } else {
		stealLogger.error("UNHANDLED opcode " + opcode
			+ " in handleStealRequest");
		// System.exit(1);
	    }

	    Timer invocationRecordWriteTimer = Timer.createTimer();
	    invocationRecordWriteTimer.start();
	    m.writeObject(result);
	    List<ByteBuffer> l = result.getByteBuffers();
	    if (l != null) {
		m.writeInt(l.size());
		if (bbLogger.isDebugEnabled()) {
		    bbLogger.debug("Writing " + l.size() + " bytebuffers to "
			    + v.getIdent());
		}
		for (ByteBuffer b : l) {
		    m.writeInt(b.capacity());
		    b.position(0);
		    b.limit(b.capacity());
		}
		for (ByteBuffer b : l) {
		    if (stealLogger.isDebugEnabled()) {
			stealLogger.debug("Writing bytebuffer of size "
				+ b.capacity());
		    }
		    m.writeByteBuffer(b);
		    if (bbLogger.isDebugEnabled()) {
			bbLogger.debug("Wrote bytebuffer of size "
				+ b.capacity());
		    }
		}
	    } else {
		m.writeInt(0);
	    }
	    long cnt = v.finish(m);
	    timer.stop(eventNo);
	    timer.addBytes(cnt, eventNo);
	    invocationRecordWriteTimer.stop();
	    s.stats.invocationRecordWriteTimer.add(invocationRecordWriteTimer);
	} catch (IOException e) {
	    if (m != null) {
		m.finish(e); // TODO always use victim.finish
	    }
	    stealLogger.warn("CASHMERE '" + s.ident
		    + "': trying to send a job back, but got exception: " + e,
		    e);
	}

	/*
	 * If we don't use fault tolerance with the global result table, we can
	 * set the object parameters to null, so the GC can clean them up --Rob
	 */
	// No, this cannot be right: it must be possible to put the job back
	// onto the work queue, so the parameters cannot be cleared. --Ceriel
	// if (FT_NAIVE) {
	// result.clearParams();
	// }
    }
}
