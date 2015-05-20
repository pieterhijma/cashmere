package ibis.cashmere.many_core;



import static org.jocl.CL.CL_DEVICE_TYPE_ALL;
import static org.jocl.CL.CL_PROFILING_COMMAND_END;
import static org.jocl.CL.CL_PROFILING_COMMAND_QUEUED;
import static org.jocl.CL.CL_PROFILING_COMMAND_START;
import static org.jocl.CL.CL_PROFILING_COMMAND_SUBMIT;
import static org.jocl.CL.clGetDeviceIDs;
import static org.jocl.CL.clGetEventProfilingInfo;
import static org.jocl.CL.clGetPlatformIDs;
import static org.jocl.CL.clReleaseEvent;
import ibis.cashmere.impl.Cashmere;
import ibis.cashmere.impl.spawnSync.InvocationRecord;
import ibis.util.ThreadPool;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.jocl.CL;
import org.jocl.CLException;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_device_id;
import org.jocl.cl_event;
import org.jocl.cl_platform_id;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MCCashmere {

    static final Logger logger = LoggerFactory.getLogger("ibis.cashmere.many_core");

    private static class KernelDevice implements Comparable<KernelDevice> {
        private String kernel;
        private Device device;

        public KernelDevice(String kernel, Device device) {
            if (kernel == null) {
                kernel = "__DEFAULT__";
            }
            this.kernel = kernel;
            this.device = device;
        }

        public boolean equals(Object oo) {
            if (! (oo instanceof KernelDevice)) {
                return false;
            }
            KernelDevice o = (KernelDevice) oo;
            return kernel.equals(o.kernel) && device.equals(o.device);
        }

        public int hashCode() {
            return kernel.hashCode() + device.hashCode();
        }

        public int compareTo(KernelDevice kd) {
            double expectedTermination;
            double expectedTerminationDevice;
            synchronized(this.device) {
                expectedTermination = (this.device.nrKernelLaunches + 1) * kernelSpeeds.get(this);
                if (logger.isInfoEnabled()) {
                    logger.info("" + this + ": launches = " + this.device.nrKernelLaunches + ", speed = " + kernelSpeeds.get(this));
                }
            }
            synchronized(kd.device) {
                expectedTerminationDevice = (kd.device.nrKernelLaunches + 1) * kernelSpeeds.get(kd);
                if (logger.isInfoEnabled()) {
                    logger.info("" + kd + ": launches = " + kd.device.nrKernelLaunches + ", speed = " + kernelSpeeds.get(kd));
                }
            }
            if (logger.isInfoEnabled()) {
                logger.info("compareTo: " + this + ": expectedTermination = " + expectedTermination
		    + ", " + kd + ": expectedTermination = " + expectedTerminationDevice);
            }
	
            return expectedTermination < expectedTerminationDevice ? -1 :
                expectedTermination == expectedTerminationDevice ? 0 : 1;
        }

        public String toString() {
            return device.toString();
        }
    }

    // Maps a kernel/device combination to a discovered speed.
    private static Map<KernelDevice, Double> kernelSpeeds
        = new HashMap<KernelDevice, Double>();

    public static final MCStats stats = new MCStats();
    
    private static Map<String, Device> devices = new HashMap<String, Device>();
    private static List<Kernel> kernels =
	Collections.synchronizedList(new ArrayList<Kernel>());
    private static Map<String, MCTimer> timers = new HashMap<String, MCTimer>();

    private static Map<Integer, List<ByteBuffer>> freeList = new HashMap<Integer, List<ByteBuffer>>();
    private static Map<Integer, FreelistFiller> fillers = new HashMap<Integer, FreelistFiller>();

    ////////////////////////////////////////////////////////////////////////////
    // The following methods are the public interface for applications:
    ////////////////////////////////////////////////////////////////////////////
    //

    // Background thread creating new bytebuffers as needed.
    private static class FreelistFiller extends Thread {
        final int sz;
        final int increment;
        final int threshold;

        FreelistFiller(int sz, int cnt) {
            this.sz = sz;
            this.threshold = cnt/3;
            this.increment = cnt/2;
            this.setDaemon(true);
        }

        public void run() {
            for (;;) {
                int cnt;
                synchronized(freeList) {
                    try {
                        freeList.wait();
                    } catch(Throwable e) {
                        // ignore
                    }
                    if (logger.isDebugEnabled()) {
                        logger.debug("Filler woke up");
                    }
                    List<ByteBuffer> l = freeList.get(sz);
                    cnt = increment - l.size();
                }
                for (int i = 0; i < cnt; i++) {
                    ByteBuffer v = ByteBuffer.allocateDirect(sz).order(ByteOrder.nativeOrder());
                    releaseByteBuffer(v);
                }
            }
        }
    }

    public static void releaseByteBuffer(ByteBuffer b) {
        if (logger.isDebugEnabled()) {
            logger.debug("Releasing bytebuffer " + System.identityHashCode(b));
        }
        int sz = b.capacity();
        synchronized(freeList) {
            List<ByteBuffer> l = freeList.get(sz);
            if (l == null) {
                l = new ArrayList<ByteBuffer>();
                freeList.put(sz, l);
            }
            l.add(b);
        }
    }

    private static byte[] initBuffer = new byte[65536];

    public static ByteBuffer getByteBuffer(int sz, boolean needsClearing) {
        ByteBuffer b;
        synchronized(freeList) {
            List<ByteBuffer> l = freeList.get(sz);
            if (l == null || l.size() == 0) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Allocating new bytebuffer");
                }
                return ByteBuffer.allocateDirect(sz).order(ByteOrder.nativeOrder());
            }
            b = l.remove(0);
            FreelistFiller f = fillers.get(sz);
            if (l.size() < f.threshold) {
                freeList.notify();
            }
            if (logger.isDebugEnabled()) {
                logger.debug("bytebuffer " + System.identityHashCode(b) + " from cache");
            }
        }
        if (needsClearing) {
            // Clear buffer.
            b.position(0);
            while (b.position() + initBuffer.length <= b.capacity()) {
                b.put(initBuffer);
            }
            b.put(initBuffer, 0, b.capacity() - b.position());
        }
        return b;
    }

    public static void initializeByteBuffers(int sz, int count) {
        if (logger.isDebugEnabled()) {
            logger.debug("Allocating " + count + " buffers of size " + sz);
        }
        for (int i = 0; i < count; i++) {
            ByteBuffer v = ByteBuffer.allocateDirect(sz).order(ByteOrder.nativeOrder());
            releaseByteBuffer(v);
        }
        FreelistFiller filler = new FreelistFiller(sz, count);
        fillers.put(sz, filler);
        filler.start();
    }


    public static void initialize() {
	initialize(new HashMap<String, List<String>>());
    }

    public static void initialize(int cnt, int byteBufferSize) {
        initialize(new HashMap<String, List<String>>(), cnt, byteBufferSize);
    }

    public static void initialize(Map<String, List<String>> defines) {
	initialize(defines, 0, 0);
    }

    public static void initialize(Map<String, List<String>> defines, int cnt, int sz) {
	Cashmere.initializeManyCore(defines, cnt, sz);
    }
    
    public static Kernel getKernel() throws MCCashmereNotAvailable {
	return getKernel(null);
    }

    public static synchronized Kernel getKernel(String name) throws MCCashmereNotAvailable {
	Device device = pickDevice(name);
        device.setBusy();
	Kernel kernel = new Kernel(name, Thread.currentThread().getName(),
		device);
	kernels.add(kernel);
	return kernel;
    }


    public static MCTimer createOverallTimer() {
	return createTimer("java", "main", "overall", 1);
    }
    
    
    public static void manyCoreSync() {
	for (Device device : devices.values()) {
	    device.sync();
	}
    }
    
    public static void enableManycore() {
	Cashmere.getCashmere().enableSpawnToManycore();
    }
    
    public static boolean spawningToManycore() {
	return Cashmere.getCashmere().spawningToManycore();
    }


    public static int getSpeed() {
	int speed = 0;
	Collection<Device> deviceCollection = devices.values();
	for (Device device : deviceCollection) {
	    int deviceSpeed = device.getSpeed();
	    if (deviceSpeed > speed) {
		speed = deviceSpeed;
	    }
	}
	return speed;
    }
    
    ////////////////////////////////////////////////////////////////////////////
    // end public interface
    ////////////////////////////////////////////////////////////////////////////



    ////////////////////////////////////////////////////////////////////////////
    // The following methods are the public interface for the rest of the
    // framework:
    ////////////////////////////////////////////////////////////////////////////
    //

    public synchronized static void addTime(String kernelName, Device device, double time) {
        KernelDevice d = new KernelDevice(kernelName, device);
        Double v = kernelSpeeds.get(d);
        if (v == null || v < time) {
            kernelSpeeds.put(d, time);
        }
    }

    public synchronized static void addJob(final InvocationRecord r) {
	final String name = getName();
	ThreadPool.createNew(new Runnable() {

	    @Override
	    public void run() {
                try {
                    callCashmereManycoreFunction(r);
                    releaseName(name);
                } catch(Throwable e) {
                    System.err.println("Got exeption " + e);
                    e.printStackTrace(System.err);
                    System.exit(1);
                }
	    }
	    
	}, name);
    }
    
    public static boolean isManycoreThread() {
	return Thread.currentThread().getName().startsWith("ManyCore");
    }
    
    public static synchronized MCTimer getTimer(String standardDevice, String
	    standardThread, String standardAction, int nrEvents) {
        String s = standardDevice + "_" + standardThread + "_"
            + standardAction + "_" + nrEvents;
        MCTimer timer = timers.get(s);
        if (timer == null) {
            timer = createTimer(standardDevice, standardThread, standardAction, nrEvents);
            timers.put(s, timer);
        }
	return timer;
    }

    public static MCTimer createTimer(String standardDevice,
            String standardThread, String standardAction, int nrEvents) {
        MCTimer timer = new MCTimer(standardDevice, standardThread, standardAction, nrEvents);
        stats.addTimer(timer);
        return timer;
    }


    public static MCTimer createTimer() {
	MCTimer timer = new MCTimer();
	stats.addTimer(timer);
	return timer;
    }


    public static void initialize2(Map<String, List<String>> defines, int cnt, int sz) {
	initializeOpenCL();
        if (cnt > 0) {
            initializeByteBuffers(sz, cnt);
        }
	Map<String, String> kernelSources = getKernelSources();
	insertDefines(kernelSources, defines);

	storeKernels(kernelSources);
    }


    public static void d(String s) {
	System.out.printf("%s: %s\n", Thread.currentThread(), s);
    }


    public static void d(String s, Object... args) {
	System.out.printf("%s: %s", Thread.currentThread(),
		String.format(s, args));
    }


    static String getHeaderSource(String source) {
	StringBuilder sb = new StringBuilder();
	Scanner scanner = new Scanner(source);
	while (scanner.hasNext("//")) {
	    sb.append(scanner.nextLine());
	    sb.append("\n");
	}
	scanner.close();
	return sb.toString();
    }


    static String getBodySource(String source) {
	Scanner scanner = new Scanner(source);
	while (scanner.hasNext("//")) {
	    scanner.nextLine();
	}

	StringBuilder sb = new StringBuilder();
	scanner.useDelimiter("\\z");
	while (scanner.hasNext()) {
	    sb.append(scanner.next());
	}
	scanner.close();
	return sb.toString();
    }
    ////////////////////////////////////////////////////////////////////////////
    // end public interface for the framework
    ////////////////////////////////////////////////////////////////////////////

    
    private static int counter;
    private static ArrayList<String> threadNames = new ArrayList<String>();



    private static Map<cl_event, Long> queuedEvents = new IdentityHashMap<cl_event, Long>();
    private static Map<cl_event, Long> startEvents = new IdentityHashMap<cl_event, Long>();
    private static Map<cl_event, Long> endEvents = new IdentityHashMap<cl_event, Long>();
    private static Map<cl_event, Long> submitEvents = new IdentityHashMap<cl_event, Long>();
    
    private static synchronized String getName() {
	if (threadNames.size() != 0) {
	    return threadNames.remove(0);
	}
	return "ManyCore handler " + counter++;
    }
    
    private static synchronized void releaseName(String s) {
	threadNames.add(s);
    }



    ////////////////////////////////////////////////////////////////////////////
    // Initializing MCCashmere
    ////////////////////////////////////////////////////////////////////////////
    private static ArrayList<Device> getDevicesPlatform(
	    cl_platform_id platform) {

	ArrayList<Device> devices = new ArrayList<Device>();

	int numDevicesArray[] = new int[1];
	clGetDeviceIDs(platform, CL_DEVICE_TYPE_ALL, 0, null,
		numDevicesArray);
	int numDevices = numDevicesArray[0];

	cl_device_id[] device_ids = new cl_device_id[numDevices];
	clGetDeviceIDs(platform, CL_DEVICE_TYPE_ALL, numDevices, device_ids,
		null);

	for (cl_device_id device : device_ids) {
	    Device d = new Device(device, platform);
	    if (!d.getName().equals("unknown")) {
		devices.add(d);
	    }
	}

	return devices;
    }


    private static void getDevices(cl_platform_id[] platforms) {
	for (cl_platform_id platform : platforms) {
	    for (Device device : getDevicesPlatform(platform)) {
		devices.put(device.getName(), device);
	    }
	}
    }


    private static void initializeOpenCL() {
	try {
	    CL.setExceptionsEnabled(true);

	    int numPlatformsArray[] = new int[1];
	    clGetPlatformIDs(0, null, numPlatformsArray);
	    int numPlatforms = numPlatformsArray[0];

	    cl_platform_id platforms[] = new cl_platform_id[numPlatforms];
	    clGetPlatformIDs(platforms.length, platforms, null);

	    getDevices(platforms);
	}
	catch (CLException e) {
	    System.out.println(e);
	    e.printStackTrace();
	    System.exit(1);
	}
    }


    private static Map<String, String> getKernelSources() {
	HashMap<String, String> kernelSources = new HashMap<String, String>();

	String classpath = System.getProperty("java.class.path");
	String[] classpathEntries = classpath.split(File.pathSeparator);

	try {
	    // assuming that the application jar is the first
	    URL jar = new URL("file:" + classpathEntries[0]);
	    ZipInputStream zip = new ZipInputStream(jar.openStream());
	    ZipEntry ze = null;
	    while ((ze = zip.getNextEntry()) != null) {
		if (ze.getName().endsWith(".cl")) {
		    BufferedInputStream bis = new BufferedInputStream(zip);
		    byte[] bytes = new byte[(int) ze.getSize()];
		    int offset = 0;
		    int sz = bytes.length;
		    while (sz > 0) {
			int nrRead = bis.read(bytes, offset, sz);
			if (nrRead > 0) {
			    offset += nrRead;
			    sz -= nrRead;
			} else {
			    throw new Error("Something wrong while reading " +
				    "kernel " + ze.getName());
			}
		    }
		    kernelSources.put(ze.getName(), new String(bytes));
		}
	    }
	}
	catch (IOException e) {
	    System.out.println(e);
	}
	return kernelSources;
    }


    private static String insertDefines(String source, List<String> defines) {
	//System.out.println("the source is:");
	//System.out.println(source);
	String header = getHeaderSource(source);
	String body = getBodySource(source);
	//System.out.println("the body is:");
	//System.out.println(body);

	StringBuilder sb = new StringBuilder();
	sb.append(header);
	for (String s : defines) {
	    sb.append(s);
	}
	sb.append(body);
	return sb.toString();
    }


    private static void insertDefines(Map<String, String> sources, 
	    Map<String, List<String>> defines) {
	for (String k : defines.keySet()) {
	    String source = sources.get(k);
	    if (source == null) {
		// System.out.printf("%s not a known sourcefile\n", k);
	    }
	    else {
		//String s = insertDefines(source, defines.get(k));
		//System.out.println("hiero:");
		//System.out.println(s);
		sources.put(k, insertDefines(source, defines.get(k)));
	    }
	}
    }


    private static String getDeviceNameSource(String source) {
	Scanner scanner = new Scanner(source);
	scanner.next(); // read away "//"
	String retval = scanner.next(); 
	scanner.close();
	return retval;
    }


    private static void storeKernels(Map<String, String> sources) {
	for (String k : sources.keySet()) {
	    String kernelSource = sources.get(k);
	    String deviceName = getDeviceNameSource(kernelSource);
	    if (devices.containsKey(deviceName)) {
		Device device = devices.get(deviceName);
		if (device == null) {
		    System.out.printf("%s not available on this machine\n", 
			    deviceName);
		}
		else {
		    device.addKernel(kernelSource);
		}
	    }
	}
    }
    ////////////////////////////////////////////////////////////////////////////
    // End initializing MCCashmere
    ////////////////////////////////////////////////////////////////////////////

    private static void callCashmereManycoreFunction(InvocationRecord r) {
	InvocationRecord parent = Cashmere.getCashmere().parent;
	if (parent.eek == null) {
	    try {
		r.setParentLocals(null);
		r.runLocal();
	    } catch (Throwable t) {
		// This can only happen if an inlet has thrown an
		// exception, or if there was no try-catch block around
		// the spawn (i.e. no inlet).
		// The semantics of this: all work is aborted,
		// and the exception is passed on to the spawner.
		// The parent is aborted, it must handle the exception.
		// Note: this can now also happen on an abort. Check for
		// the AbortException!
		if (parent.eek == null) {
		    parent.eek = t;
		}
	    }
	}
        r.decrSpawnCounter();
    }



    ////////////////////////////////////////////////////////////////////////////
    // Concluding everyting
    ////////////////////////////////////////////////////////////////////////////
    /** Conclude all timers for the statistics.
     * Called by MCStats to conclude the statistics. Deferred to MCCashmere
     * because it keeps track of all OpenCL timers.
     */
    static void conclude() {
	MCTimer writeTimer = createTimer();
	MCTimer executeTimer = createTimer();
	MCTimer readTimer = createTimer();

	if (!kernels.isEmpty()) {
	    System.out.println("concluding");
	    for (Kernel kernel : kernels) {
		for (KernelLaunch kernelLaunch : kernel.kernelLaunches) {
		    conclude(kernelLaunch.getThread(), "writeBuffer",
			    kernel.getDevice(),
			    kernelLaunch.writeBufferEvents, writeTimer);
		    conclude(kernelLaunch.getThread(), "execute",
			    kernel.getDevice(),
			    kernelLaunch.executeEvents, executeTimer);
		    conclude(kernelLaunch.getThread(), "readBuffer",
			    kernel.getDevice(),
			    kernelLaunch.readBufferEvents, readTimer);
		}
		Device device = kernel.getDevice();
		conclude(kernel.getThread(), "readBuffer", kernel.getDevice(),
			device.readBufferEvents, readTimer);
	    }
	    System.out.println("done concluding");
	}

    }

    static synchronized void releaseEvent(cl_event event) {
	long queued = getValue(event, CL_PROFILING_COMMAND_QUEUED);
	long submit = getValue(event, CL_PROFILING_COMMAND_SUBMIT);
	long start = getValue(event, CL_PROFILING_COMMAND_START);
	long end = getValue(event, CL_PROFILING_COMMAND_END);
	if (end == 0 || end < start) {
	    // Sometimes seems to happen ...
	    if (logger.isDebugEnabled()) {
		logger.debug("start = " + start + ", end = " + end + ", event = " + event, new Throwable());
	    }
	    end = start + 1;
	}
	if (start > 0) {
	    queuedEvents.put(event, queued);
	    submitEvents.put(event, submit);
	    startEvents.put(event, start);
	    endEvents.put(event, end);
	} else {
	    // Sometimes happens on read events that result from synchronous reads.
	    // System.err.println("Release unstarted event?");
	    // (new Throwable()).printStackTrace(System.err);
	    return;
	}
	// if (event == null) {	// never; debug.
	    clReleaseEvent(event);
	// }
    }
    
    static long getValue(cl_event event, int command) {
	long[] value = new long[1];
	clGetEventProfilingInfo(event, command,
		Sizeof.cl_ulong, Pointer.to(value), null);
	return value[0];
    }


    private static void conclude(String thread, String action, Device device,
	    ArrayList<cl_event> events, MCTimer timer) {
	if (events == null) throw new Error("shit");

	long offsetHostDevice = device.getOffsetHostDevice();
	for (cl_event event : events) {
	    if (queuedEvents.get(event) == null) {
		releaseEvent(event);
	    }
	    long queued = queuedEvents.get(event);
	    long submit = submitEvents.get(event);
	    long start = startEvents.get(event);
	    long end = endEvents.get(event);
	    Event event2 = new Event(timer.getNode(), device.getNickName(),
		    thread, timer.getQueue(), action, queued +
		    offsetHostDevice, submit + offsetHostDevice, start +
		    offsetHostDevice, end + offsetHostDevice);
	    // System.out.println("start = " + start + ", end = " + end + ", offsetHostDevice = " + offsetHostDevice);
	    timer.add(event2);
	}
    }
    ////////////////////////////////////////////////////////////////////////////
    // End concluding everything
    ////////////////////////////////////////////////////////////////////////////
    


    ////////////////////////////////////////////////////////////////////////////
    // Picking device
    ////////////////////////////////////////////////////////////////////////////
    private static Device pickDevice(String name) throws MCCashmereNotAvailable {
	Collection<Device> deviceCollection = devices.values();
	ArrayList<Device> al = new ArrayList<Device>();
        ArrayList<KernelDevice> kd = new ArrayList<KernelDevice>();
        boolean measuredSpeeds = true;
	for (Device device : deviceCollection) {
	    if (device.registeredKernel(name)) {
                KernelDevice d = new KernelDevice(name, device);
                if (kernelSpeeds.get(d) == null) {
                    measuredSpeeds = false;
                } else {
                    kd.add(d);
                }
		al.add(device);
	    }
	}

        if (al.size() > 0) {
            if (measuredSpeeds) {
                Collections.sort(kd);
                return kd.get(0).device;
            }

            Collections.sort(al);
            // the first is the best.
            return al.get(0);
        }

	String kernelMessage = name == null ? "the kernel" : "kernel " + name;
	throw new MCCashmereNotAvailable("no devices found where " +
		kernelMessage + " is registered");
    }
    ////////////////////////////////////////////////////////////////////////////
    // End picking device
    ////////////////////////////////////////////////////////////////////////////
}
