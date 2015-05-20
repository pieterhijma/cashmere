package ibis.cashmere.many_core;


import ibis.cashmere.impl.Cashmere;
import ibis.util.Timer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.base.Function;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;


public class MCTimer implements java.io.Serializable {

    private static final long serialVersionUID = 1L;
    private static final AtomicInteger nrQueues = new AtomicInteger();


    private ArrayList<Event> events;


    private String standardNode;
    private String standardDevice;
    private String standardThread;
    private String standardAction;
    private int standardQueue;
    private int currentEvent;


    // not sure 
    public static int getNextQueue() {
	return nrQueues.getAndIncrement();
    }


    public int getQueue() {
	return standardQueue;
    }


    public String getAction() {
	if (standardAction == null) {
	    Event event = events.get(0);
	    return event.getAction();
	}
	else {
	    return standardAction;
	}
    }


    public String getNode() {
	return standardNode;
    }


    MCTimer() {
	events = new ArrayList<Event>();
	this.standardNode = Cashmere.getCashmere().ident.name();
	this.standardThread = null;
	this.standardAction = null;
	this.standardQueue = getNextQueue();
	this.currentEvent = -1;
    }


    MCTimer(String standardDevice, String standardThread,
	    String standardAction, int nrEvents) {
	this.events = new ArrayList<Event>();
	this.standardNode = Cashmere.getCashmere().ident.name();
	this.standardDevice = standardDevice;
	this.standardThread = standardThread;
	this.standardAction = standardAction;
	this.standardQueue = getNextQueue();
	this.currentEvent = -1;

	addNewEvents(nrEvents);
    }


    void equalize(TimeSyncInfo timeSyncInfo) {
	for (Event e : events) {
	    long offsetToMaster = timeSyncInfo.getOffsetToMaster(e.node);
	    e.queued += offsetToMaster;
	    e.submitted += offsetToMaster;
	    e.start += offsetToMaster;
	    e.end += offsetToMaster;
	}
    }


    private void addNewEvents(int nrEvents) {
	for (int i = 0; i < nrEvents; i++) {
	    events.add(new Event(standardNode, standardDevice, standardThread,
			standardQueue, standardAction, 0, 0, 0, 0)); 
	}
    }

    public int start() {
        int eventNo;
        synchronized(this) {
            eventNo = ++currentEvent;
            if (currentEvent == events.size()) {
                addNewEvents(events.size() * 3);
            }
	}
	Event event = events.get(eventNo);
	event.queued = event.submitted = event.start = System.nanoTime();
        return eventNo;
    }


    public int start(String action) {
	int eventNo = start();
	Event event = events.get(eventNo);
	event.action = action;
        return eventNo;
    }


    public void addBytes(long nrBytes, int eventNo) {
	Event event = events.get(eventNo);
	event.nrBytes = nrBytes;
    }


    public void stop(int eventNo) {
	Event event = events.get(eventNo);
	event.end = System.nanoTime();
    }




    void add(Event event) {
	this.events.add(event);
    }

    void onlyDataTransfers() {
	ArrayList<Event> filtered = new ArrayList<Event>();
	for (Event e : events) {
	    if (e.hasDataTransfers()) {
		filtered.add(e);
	    }
	}
	this.events = filtered;
    }

    List<MCTimer> groupByAction() {
	Function<Event, String> f = new Function<Event, String>() {
	    public String apply(Event event) {
		return event.getAction();
	    }
	};
	return groupBy(f);
    }





    List<MCTimer> groupByDevice() {
	Function<Event, String> f = new Function<Event, String>() {
	    public String apply(Event event) {
		return event.getDevice();
	    }
	};
	return groupBy(f);
    }


    List<MCTimer> groupByNode() {
	Function<Event, String> f = new Function<Event, String>() {
	    public String apply(Event event) {
		return event.getNode();
	    }
	};
	return groupBy(f);
    }



    private <T> List<MCTimer> groupBy(Function<Event, T> f) {
	Multimap<T, Event> index = Multimaps.index(events, f);
	ArrayList<MCTimer> timers = new ArrayList<MCTimer>();
	for (T t : index.keySet()) {
	    MCTimer timer = new MCTimer();
	    timer.events.addAll(index.get(t));
	    timers.add(timer);
	}
	return timers;
    }


    void add(MCTimer mcTimer) {
	this.events.addAll(mcTimer.events);
    }


    void clean() {
	ArrayList<Event> cleaned = new ArrayList<Event>();
	for (Event e : events) {
	    if (!e.isEmptyEvent()) {
		cleaned.add(e);
	    }
	}
	this.events = cleaned;
    }


    public int nrTimes() {
	return events.size();
    }


    private double toDoubleMicroSecondsFromNanos(long nanos) {
	return nanos / 1000.0;
    }


    public double totalTimeVal() {
	double total = 0.0;
	for (Event event : events) {
	    total += toDoubleMicroSecondsFromNanos(event.time());
	}
	return total; 
    }


    public double averageTimeVal() {
	return totalTimeVal() / nrTimes();
    }


    public String averageTime() {
	return Timer.format(averageTimeVal());
    }


    public String totalTime() {
	return Timer.format(totalTimeVal());
    }


    public long getMinimumTime() {
	long min = Long.MAX_VALUE;
	for (Event event : events) {
	    min = Math.min(event.getQueued(), min);
	}
	return min;
    }


    public void normalize(long min) {
	for (Event event : events) {
	    event.normalize(min);
	}
    }


    public String dataTransferOutput() {
	StringBuffer sb = new StringBuffer();
	for (Event event : events) {
	    sb.append(event.toDataTransferString());
	}
	return sb.toString();
    }


    public String extensiveOutput() {
	StringBuffer sb = new StringBuffer();
	for (Event event : events) {
	    sb.append(event);	
	}
	return sb.toString();
    }


    public void append(StringBuffer sb, String node, String device, String
	    thread, int queue, long start, long end, String action, boolean
	    perThread) {
	sb.append(String.format("%s %s %s\t%f\t%f\t%s\n", node, device,
		    perThread ? thread : "queue" + queue, start / 1e6, end /
		    1e6, action));    
    }


    private boolean isSmallSteal(Event event) {
	// event is assumed to be a steal event
	return event.getEnd() - event.getStart() < 200000;
    }


    private boolean isTooEarlySteal(Event event, long startExecute) {
	return event.getStart() < startExecute;
	// event is assumed to be a steal event
    }


    private boolean isUninterestingSteal(Event event, long startExecute) {
	return event.getAction().equals("steal") && (isSmallSteal(event) ||
		isTooEarlySteal(event, startExecute));
    }

 
    private long getStartTimeExecute() {
	long startExecute = Long.MAX_VALUE;
	for (Event event : events) {
	    if (event.getAction().equals("execute")) {
		if (event.getStart() < startExecute) {
		    startExecute = event.getStart();
		}
	    }
	}
	return startExecute;
    }


    private Event getOverallEvent() {
	for (Event event : events) {
	    if (event.isOverallEvent()) {
		return event;
	    }
	}
	return null;
    }

    /** Filters all events that are not within the 'overall' frame.
     *
     * We filter 10% before the overallStartTime and 10% after to make up for
     * imprecisions in synchronizing between nodes.
     */
    public void filterOverall() {
	Event overallEvent = getOverallEvent();
	if (overallEvent == null) return;

	long startTime = overallEvent.getStart();
	long time = overallEvent.time();
	long startFilter = Math.max(startTime - (long) (0.1 * time), 0);
	long endFilter = time + (long) (2 * 0.1 * time);

	normalize(startFilter);
 
	ArrayList<Event> filtered = new ArrayList<Event>();
        // System.out.println("Filter: end = " + Timer.format(endFilter/1000.0));
	for (Event event : events) {
	    if (event.getStart() >= 0 && event.getEnd() < endFilter) {
		filtered.add(event);
	    } 
	    /*
	    else {
		System.out.println("Filtered out event: " + event);
	    }
	    */
	}
	this.events = filtered;
    }


    public void filterSteals() {
	ArrayList<Event> filtered = new ArrayList<Event>();
	long startExecute = getStartTimeExecute();
	for (Event e : events) {
	    if (!isUninterestingSteal(e, startExecute)) {
		filtered.add(e);
	    }
	}
	this.events = filtered;
    }


    public String gnuPlotData(boolean perThread) {
	StringBuffer sb = new StringBuffer();
	for (Event event : events) {
	    /*
	    append(sb, event.getNode(), event.getQueue(), event.getQueued(),
		    event.getQueued() + 3000, "queuing for " +
		    event.getAction());
	    append(sb, event.getNode(), event.getQueue(), event.getSubmitted(),
		    event.getSubmitted() + 3000, "submitting for " +
		    event.getAction());
		    */
            if (event.getEnd() > 0) {
		append(sb, event.getNode(), event.getDevice(),
			event.getThread(), event.getQueue(), event.getStart(),
			event.getEnd(), event.getAction(), perThread);
            }
	}
	return sb.toString();
    }
}
