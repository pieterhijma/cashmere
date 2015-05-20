package ibis.cashmere.many_core;

import ibis.util.Timer;

public class Event implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    String node;
    String device;
    String thread;
    int queueIndex;
    String action;

    long queued;
    long submitted;
    long start;
    long end;

    long nrBytes;

    public Event(String node, String device, String thread, int queueIndex,
	    String action, long queued, long submitted, long start, long end) {

	this.node = node;
	this.device = device;
	this.thread = thread;
	this.queueIndex = queueIndex;
	this.action = action;
	this.queued = queued;
	this.submitted = submitted;
	this.start = start;
	this.end = end;
	this.nrBytes = 0;
    }


    public String getNode() {
	return node;
    }
    public String getDevice() {
	return device;
    }
    public int getQueue() {
	return queueIndex;
    }
    public String getAction() {
	return action;
    }
    public long getQueued() {
	return queued;
    }
    public long getSubmitted() {
	return submitted;
    }
    public long getStart() {
	return start;
    }
    public long getEnd() {
	return end;
    }
    public String getThread() {
	return thread;
    }


    public void normalize(long min) {
	queued -= min;
	submitted -= min;
	start -= min;
	end -= min;
    }

    public long time() {
	return end - start;
    }


    public boolean isEmptyEvent() {
	return queued == 0 &&
	    submitted == 0 &&
	    start == 0 &&
	    end == 0;
    }


    boolean isOverallEvent() {
	return device.equals("java") &&
	    thread.equals("main") &&
	    action.equals("overall");
    }


    boolean hasDataTransfers() {
	return nrBytes > 0;
    }


    private double getRate() {
	double nrMBs = nrBytes / 1024.0 / 1024.0;
	double duration = (end - start) / 1e9;
	return nrMBs / duration;
    }


    private String getRateString() {
	return String.format("%4.3f MB/s", getRate());
    }


    String toDataTransferString() {
	return String.format("%-8s  |  %-14s  |  start: %-6s  |  " + 
		"end: %-6s  |  duration: %-6s  | nrBytes: %4d MB  |  " + 
		"rate: %13s\n", 
		node, 
		action, 
		Timer.format(start / 1000.0),
		Timer.format(end / 1000.0), 
		Timer.format((end - start) / 1000.0),
		nrBytes / 1024 / 1024, 
		getRateString());
    }


    public String toString() {
	return String.format("%-8s  |  %-10s  |  %-22s  |  queue: %-2d  |  " +
		"%-14s  |  queued: %-6s  |  submitted: %-6s  |  " + 
		"start: %-6s  |  end: %-6s\n", 
		node, 
		device,
		thread,
		queueIndex,
		action, 
		Timer.format(queued / 1000.0),
		Timer.format(submitted / 1000.0),
		Timer.format(start / 1000.0),
		Timer.format(end / 1000.0));
    }
}
