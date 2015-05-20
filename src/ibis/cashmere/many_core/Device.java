package ibis.cashmere.many_core;

import static org.jocl.CL.CL_CONTEXT_PLATFORM;
import static org.jocl.CL.CL_DEVICE_NAME;
import static org.jocl.CL.CL_KERNEL_FUNCTION_NAME;
import static org.jocl.CL.CL_MEM_READ_WRITE;
import static org.jocl.CL.CL_PROFILING_COMMAND_QUEUED;
import static org.jocl.CL.CL_PROGRAM_BINARIES;
import static org.jocl.CL.CL_PROGRAM_BINARY_SIZES;
import static org.jocl.CL.CL_PROGRAM_BUILD_LOG;
import static org.jocl.CL.CL_QUEUE_PROFILING_ENABLE;
import static org.jocl.CL.CL_TRUE;
import static org.jocl.CL.clBuildProgram;
import static org.jocl.CL.clCreateBuffer;
import static org.jocl.CL.clCreateCommandQueue;
import static org.jocl.CL.clCreateContext;
import static org.jocl.CL.clCreateKernelsInProgram;
import static org.jocl.CL.clCreateProgramWithSource;
import static org.jocl.CL.clEnqueueWriteBuffer;
import static org.jocl.CL.clGetDeviceInfo;
import static org.jocl.CL.clGetKernelInfo;
import static org.jocl.CL.clGetProgramBuildInfo;
import static org.jocl.CL.clGetProgramInfo;
import static org.jocl.CL.clReleaseMemObject;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_event;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.jocl.cl_platform_id;
import org.jocl.cl_program;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Device implements Comparable<Device> {
    private static class DeviceInfo {
	final String name;
	final int speed;
	final String nickName;

	DeviceInfo(String name, int speed, String nickName) {
	    this.name = name;
	    this.speed = speed;
	    this.nickName = nickName;
	}

	@Override
	public String toString() {
	    return nickName;
	}
    }

    static final Logger logger = LoggerFactory
	    .getLogger("ibis.cashmere.many_core.Device");
    private static final Map<String, DeviceInfo> OPENCL_TO_MCL_DEVICE_INFO;
    private static final Set<String> ACCELERATORS;
    private static final Map<cl_context, Device> devices = new HashMap<cl_context, Device>();
    static {
	OPENCL_TO_MCL_DEVICE_INFO = new HashMap<String, DeviceInfo>();
	// node030
	OPENCL_TO_MCL_DEVICE_INFO.put("GeForce GTX 480", new DeviceInfo(
		"cc_2_0", 20, "gtx480"));
	OPENCL_TO_MCL_DEVICE_INFO.put("GeForce GTX 680", new DeviceInfo(
		"cc_2_0", 40, "gtx680"));
	OPENCL_TO_MCL_DEVICE_INFO.put("GeForce GTX TITAN", new DeviceInfo(
		"cc_2_0", 60, "titan"));
	OPENCL_TO_MCL_DEVICE_INFO.put("Tesla K20m", new DeviceInfo("cc_2_0",
		40, "k20"));
	OPENCL_TO_MCL_DEVICE_INFO.put("Tesla C2050", new DeviceInfo("cc_2_0",
		10, "c2050"));
	OPENCL_TO_MCL_DEVICE_INFO.put(
		"Intel(R) Xeon(R) CPU           E5620  @ 2.40GHz",
		new DeviceInfo("xeon_e5620", 1, "xeon_e5620"));
	OPENCL_TO_MCL_DEVICE_INFO.put(
		"Intel(R) Xeon(R) CPU E5-2630 0 @ 2.30GHz", new DeviceInfo(
			"xeon_e5620", 1, "xeon_e5620"));

	OPENCL_TO_MCL_DEVICE_INFO.put(
		"Intel(R) Xeon(R) CPU           X5650  @ 2.67GHz",
		new DeviceInfo("xeon_e5620", 1, "xeon_e5620"));

	OPENCL_TO_MCL_DEVICE_INFO.put("Tahiti", new DeviceInfo("hd7970", 60,
		"hd7970"));

	// node078, node079
	OPENCL_TO_MCL_DEVICE_INFO.put(
		"Intel(R) Xeon(R) CPU E5-2620 0 @ 2.00GHz", new DeviceInfo(
			"xeon_e5620", 1, "xeon_e5620"));
	OPENCL_TO_MCL_DEVICE_INFO.put(
		"Intel(R) Many Integrated Core Acceleration Card",
		new DeviceInfo("xeon_phi", 10, "xeon_phi"));

	ACCELERATORS = new HashSet<String>();
	ACCELERATORS.add("cpu");
    }

    private Map<String, cl_program> kernels;
    private long offsetHostDevice;

    private DeviceInfo info;

    cl_context context;
    cl_command_queue writeQueue;
    cl_command_queue executeQueue;
    cl_command_queue readQueue;

    private Map<cl_event, Integer> eventRefs = new IdentityHashMap<cl_event, Integer>();

    private Map<float[], FloatArrayArgument> floatArrayArguments;
    private Map<int[], IntArrayArgument> intArrayArguments;
    private Map<ByteBuffer, ByteBufferArgument> byteBufferArguments;

    private Map<float[], cl_event> writeEventsFloats;
    private Map<int[], cl_event> writeEventsInts;
    private Map<ByteBuffer, cl_event> writeEventsBuffers;

    private Map<ByteBuffer, ArrayList<cl_event>> executeEventsBuffers;
    private Map<float[], ArrayList<cl_event>> executeEventsFloats;
    private Map<int[], ArrayList<cl_event>> executeEventsInts;

    ArrayList<cl_event> readBufferEvents;

    int nrKernelLaunches;
    private int launched;
    private int waiting;

    // //////////////////////////////////////////////////////////////////////////
    // The following methods are the public interface for applications:
    // //////////////////////////////////////////////////////////////////////////

    public static Device getDevice(cl_context context) {
	return devices.get(context);
    }

    public synchronized void registerEvent(cl_event e) {
	Integer i = eventRefs.get(e);
	if (i == null) {
	    if (logger.isDebugEnabled()) {
		logger.debug("New event " + e);
	    }
	    eventRefs.put(e, 1);
	} else {
	    if (logger.isDebugEnabled()) {
		logger.debug("Event " + e + " + now has refcount " + (i + 1));
	    }
	    eventRefs.put(e, i + 1);
	}
    }

    public void deleteEvent(cl_event e) {
	synchronized (this) {
	    Integer i = eventRefs.remove(e);
	    if (i != 1) {
		if (logger.isDebugEnabled()) {
		    logger.debug("Event " + e + " + now has refcount "
			    + (i - 1));
		}
		eventRefs.put(e, i - 1);
		return;
	    }
	}
	if (logger.isDebugEnabled()) {
	    logger.debug("Release event " + e);
	}
	MCCashmere.releaseEvent(e);
    }

    public void copy(ByteBuffer a, Argument.Direction d) {
	if (!byteBufferArguments.containsKey(a)) {
	    ArrayList<cl_event> writeBufferEvents = new ArrayList<cl_event>();
	    byteBufferArguments.put(a, new ByteBufferArgument(context,
		    writeQueue, readQueue, writeBufferEvents, a, d, null));
	    if (writeBufferEvents.size() == 1) {
		if (logger.isDebugEnabled()) {
		    logger.debug("Copy ByteBuffer: event = "
			    + writeBufferEvents.get(0));
		}
		writeEventsBuffers.put(a, writeBufferEvents.get(0));
	    }
	}
    }

    public void copy(float[] a, Argument.Direction d) {
	if (!floatArrayArguments.containsKey(a)) {
	    ArrayList<cl_event> writeBufferEvents = new ArrayList<cl_event>();
	    floatArrayArguments.put(a, new FloatArrayArgument(context,
		    writeQueue, readQueue, writeBufferEvents, a, d, null));
	    if (writeBufferEvents.size() == 1) {
		if (logger.isDebugEnabled()) {
		    logger.debug("Copy float[]: event = "
			    + writeBufferEvents.get(0));
		}
		writeEventsFloats.put(a, writeBufferEvents.get(0));
	    }
	}
    }

    public void copy(int[] a, Argument.Direction d) {
	if (!intArrayArguments.containsKey(a)) {
	    ArrayList<cl_event> writeBufferEvents = new ArrayList<cl_event>();
	    intArrayArguments.put(a, new IntArrayArgument(context, writeQueue,
		    readQueue, writeBufferEvents, a, d, null));
	    if (writeBufferEvents.size() == 1) {
		if (logger.isDebugEnabled()) {
		    logger.debug("Copy int[]: event = "
			    + writeBufferEvents.get(0));
		}
		writeEventsInts.put(a, writeBufferEvents.get(0));
	    }
	}
    }

    public void get(ByteBuffer a) {
	ByteBufferArgument baa = byteBufferArguments.get(a);
	baa.scheduleReads(getExecuteEvents(a, executeEventsBuffers),
		readBufferEvents);
    }

    public void get(float[] a) {
	FloatArrayArgument faa = floatArrayArguments.get(a);
	ArrayList<cl_event> execEvents = getExecuteEvents(a,
		executeEventsFloats);
	faa.scheduleReads(execEvents, readBufferEvents);
	executeEventsFloats.remove(a);
	for (cl_event ev : execEvents) {
	    deleteEvent(ev);
	    for (int[] arr : executeEventsInts.keySet()) {
		ArrayList<cl_event> e = executeEventsInts.get(arr);
		if (e.remove(ev)) {
		    deleteEvent(ev);
		}
	    }
	    for (float[] arr : executeEventsFloats.keySet()) {
		ArrayList<cl_event> e = executeEventsFloats.get(arr);
		if (e.remove(ev)) {
		    deleteEvent(ev);
		}
	    }
	}
    }

    public void get(int[] a) {
	IntArrayArgument iaa = intArrayArguments.get(a);
	ArrayList<cl_event> execEvents = getExecuteEvents(a, executeEventsInts);
	iaa.scheduleReads(execEvents, readBufferEvents);
	executeEventsInts.remove(a);
	for (cl_event ev : execEvents) {
	    deleteEvent(ev);
	    for (int[] arr : executeEventsInts.keySet()) {
		ArrayList<cl_event> e = executeEventsInts.get(arr);
		if (e.remove(ev)) {
		    deleteEvent(ev);
		}
	    }
	    for (float[] arr : executeEventsFloats.keySet()) {
		ArrayList<cl_event> e = executeEventsFloats.get(arr);
		if (e.remove(ev)) {
		    deleteEvent(ev);
		}
	    }
	}
    }

    // //////////////////////////////////////////////////////////////////////////
    // end public interface
    // //////////////////////////////////////////////////////////////////////////

    public int compareTo(Device device) {
	double factor;
	double expectedTermination;
	double expectedTerminationDevice;
	synchronized (this) {
	    factor = 1.0 / info.speed;
	    expectedTermination = nrKernelLaunches + 1;
	}
	synchronized (device) {
	    factor = factor * device.info.speed;
	    expectedTerminationDevice = device.nrKernelLaunches + 1;
	}

	expectedTermination *= factor;

	if (logger.isInfoEnabled()) {
	    logger.info("compareTo: " + this + ": expectedTermination = "
		    + expectedTermination + ", " + device
		    + ": expectedTermination = " + expectedTerminationDevice);
	}

	return expectedTermination < expectedTerminationDevice ? -1
		: expectedTermination == expectedTerminationDevice ? 0 : 1;
    }

    cl_device_id dev;

    Device(cl_device_id device, cl_platform_id platform) {
	this.dev = device;
	// initialize the context properties
	cl_context_properties contextProperties = new cl_context_properties();
	contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);

	// create a context for the device
	context = clCreateContext(contextProperties, 1,
		new cl_device_id[] { device }, null, null, null);

	devices.put(context, this);

	writeQueue = clCreateCommandQueue(context, device,
		CL_QUEUE_PROFILING_ENABLE, null);
	executeQueue = clCreateCommandQueue(context, device,
		CL_QUEUE_PROFILING_ENABLE, null);
	readQueue = clCreateCommandQueue(context, device,
		CL_QUEUE_PROFILING_ENABLE, null);

	long size[] = new long[1];
	clGetDeviceInfo(device, CL_DEVICE_NAME, 0, null, size);
	byte buffer[] = new byte[(int) size[0]];
	clGetDeviceInfo(device, CL_DEVICE_NAME, buffer.length,
		Pointer.to(buffer), null);
	String openCLDeviceName = new String(buffer, 0, buffer.length - 1)
		.trim();
	if (OPENCL_TO_MCL_DEVICE_INFO.containsKey(openCLDeviceName)) {
	    this.info = OPENCL_TO_MCL_DEVICE_INFO.get(openCLDeviceName);
	    logger.info("Found MCL device: " + this.info.name + " ("
		    + openCLDeviceName + ")");
	    measureTimeOffset();
	} else {
	    this.info = new DeviceInfo("unknown", 1, "Unknown");
	    logger.warn("Found OpenCL device: " + openCLDeviceName);
	    logger.warn("This is an unkown MCL device, please add it to MCL");
	}

	readBufferEvents = new ArrayList<cl_event>();

	this.kernels = new HashMap<String, cl_program>();

	this.byteBufferArguments = new HashMap<ByteBuffer, ByteBufferArgument>();
	this.floatArrayArguments = new HashMap<float[], FloatArrayArgument>();
	this.intArrayArguments = new HashMap<int[], IntArrayArgument>();

	this.writeEventsBuffers = new HashMap<ByteBuffer, cl_event>();
	this.writeEventsFloats = new HashMap<float[], cl_event>();
	this.writeEventsInts = new HashMap<int[], cl_event>();

	this.executeEventsBuffers = new HashMap<ByteBuffer, ArrayList<cl_event>>();
	this.executeEventsFloats = new HashMap<float[], ArrayList<cl_event>>();
	this.executeEventsInts = new HashMap<int[], ArrayList<cl_event>>();
    }

    long getOffsetHostDevice() {
	return offsetHostDevice;
    }

    void sync() {
	// TODO: fix this.
	/*
	 * for (Kernel kernel : kernels.values()) { kernel.sync(); }
	 */
    }

    private void writeBinary(cl_program program, String nameKernel,
	    String nameDevice) {
	int nrDevices = 1;
	long[] sizes = new long[nrDevices];
	clGetProgramInfo(program, CL_PROGRAM_BINARY_SIZES, nrDevices
		* Sizeof.size_t, Pointer.to(sizes), null);
	byte[][] buffers = new byte[nrDevices][(int) sizes[0] + 1];
	Pointer[] pointers = new Pointer[nrDevices];
	pointers[0] = Pointer.to(buffers[0]);
	Pointer p = Pointer.to(pointers);
	clGetProgramInfo(program, CL_PROGRAM_BINARIES, nrDevices
		* Sizeof.POINTER, p, null);
	String binary = new String(buffers[0], 0, buffers[0].length - 1).trim();
	try {
	    PrintStream out = new PrintStream(new File(nameDevice + "_"
		    + nameKernel + ".ptx"));
	    out.println(binary);
	    out.close();
	} catch (IOException e) {
	    System.err.println(e.getMessage());
	}
    }

    void addKernel(String kernelSource) {
	cl_program program = clCreateProgramWithSource(context, 1,
		new String[] { kernelSource },
		new long[] { kernelSource.length() }, null);

	clBuildProgram(program, 0, null, null, null, null);
	// clBuildProgram(program, 0, null,
	// "-cl-nv-verbose -cl-nv-maxrregcount=20", null, null);
	// assuming there is only one kernel for now.

	long size[] = new long[1];
	clGetProgramBuildInfo(program, dev, CL_PROGRAM_BUILD_LOG, 0, null, size);
	byte buffer[] = new byte[(int) size[0]];
	clGetProgramBuildInfo(program, dev, CL_PROGRAM_BUILD_LOG,
		buffer.length, Pointer.to(buffer), null);
	String log = new String(buffer, 0, buffer.length - 1).trim();

	System.out.println(log);

	cl_kernel[] kernelArray = new cl_kernel[1];
	clCreateKernelsInProgram(program, 1, kernelArray, null);
	cl_kernel kernel = kernelArray[0];

	String nameKernel = getName(kernel);
	this.kernels.put(nameKernel, program);

	writeBinary(program, nameKernel, info.name);

	logger.info("Registered kernel " + nameKernel + " on device "
		+ info.nickName);
    }

    cl_kernel getKernel() {
	Collection<cl_program> programCollection = kernels.values();
	cl_program[] programs = new cl_program[programCollection.size()];
	programs = programCollection.toArray(programs);
	cl_program program = programs[0];
	return getKernelProgram(program);
    }

    cl_kernel getKernel(String name) {
	if (name == null) {
	    return getKernel();
	}
	cl_program program = kernels.get(name);
	return getKernelProgram(program);
    }

    boolean registeredKernel(String name) {
	if (name == null) {
	    return kernels.size() == 1;
	} else {
	    return kernels.containsKey(name);
	}
    }

    String getName() {
	return info.name;
    }

    String getNickName() {
	return info.nickName;
    }

    FloatArrayArgument getArgument(float[] a) {
	return floatArrayArguments.get(a);
    }

    ByteBufferArgument getArgument(ByteBuffer a) {
	return byteBufferArguments.get(a);
    }

    IntArrayArgument getArgument(int[] a) {
	return intArrayArguments.get(a);
    }

    void addEvent(float[] a, cl_event event) {
	ArrayList<cl_event> events = getExecuteEvents(a, executeEventsFloats);
	events.add(event);
	registerEvent(event);
    }

    void addEvent(int[] a, cl_event event) {
	ArrayList<cl_event> events = getExecuteEvents(a, executeEventsInts);
	events.add(event);
	registerEvent(event);
    }

    void addEvent(ByteBuffer a, cl_event event) {
	ArrayList<cl_event> events = getExecuteEvents(a, executeEventsBuffers);
	events.add(event);
	registerEvent(event);
    }

    cl_event getWriteEvent(float[] a) {
	return writeEventsFloats.get(a);
    }

    cl_event getWriteEvent(int[] a) {
	return writeEventsInts.get(a);
    }

    cl_event getWriteEvent(ByteBuffer a) {
	return writeEventsBuffers.get(a);
    }

    synchronized void setBusy() {
	nrKernelLaunches++;
    }

    synchronized void launched() {
	launched++;
    }

    synchronized void setNotBusy() {
	nrKernelLaunches--;
	launched--;
	if (waiting != 0) {
	    notifyAll();
	}
    }

    synchronized boolean waitForSomeJobToFinish() {
	int n = nrKernelLaunches;
	while (n != 0 && nrKernelLaunches == n) {
	    if (launched != 0) {
		waiting++;
		try {
		    wait();
		} catch (InterruptedException e) {
		    // ignore
		}
		waiting--;
	    } else {
		// In this case, there are KernelLaunches created, but none
		// actually got
		// to the launch() call.
		return false;
	    }
	}
	return n == 0;
    }

    int getSpeed() {
	return info.speed;
    }

    private String getName(cl_kernel kernel) {
	long size[] = new long[1];
	clGetKernelInfo(kernel, CL_KERNEL_FUNCTION_NAME, 0, null, size);
	byte buffer[] = new byte[(int) size[0]];
	clGetKernelInfo(kernel, CL_KERNEL_FUNCTION_NAME, buffer.length,
		Pointer.to(buffer), null);
	return new String(buffer, 0, buffer.length - 1);
    }

    private <K> ArrayList<cl_event> getExecuteEvents(K k,
	    Map<K, ArrayList<cl_event>> map) {
	ArrayList<cl_event> events;
	if (map.containsKey(k)) {
	    events = map.get(k);
	} else {
	    events = new ArrayList<cl_event>();
	    map.put(k, events);
	}
	return events;
    }

    private boolean isAccelerator() {
	return ACCELERATORS.contains(info.name);
    }

    // ???? --Ceriel
    private long getFlags() {
	if (isAccelerator()) {
	    return CL_MEM_READ_WRITE;
	} else {
	    return CL_MEM_READ_WRITE;
	}
    }

    private void measureTimeOffset() {
	float f[] = { 0.0f };
	Pointer fPointer = Pointer.to(f);
	cl_mem memObject = clCreateBuffer(context, getFlags(), Sizeof.cl_float,
		null, null);
	cl_event event = new cl_event();
	long startHost = System.nanoTime();
	clEnqueueWriteBuffer(writeQueue, memObject, CL_TRUE, 0,
		Sizeof.cl_float, fPointer, 0, null, event);
	clReleaseMemObject(memObject);
	long startDevice = MCCashmere.getValue(event,
		CL_PROFILING_COMMAND_QUEUED);

	offsetHostDevice = startHost - startDevice;
    }

    private cl_kernel getKernelProgram(cl_program program) {
	cl_kernel[] kernelArray = new cl_kernel[1];
	clCreateKernelsInProgram(program, 1, kernelArray, null);
	cl_kernel kernel = kernelArray[0];
	return kernel;
    }

    // Mechanism to not allow writes until the last write of the previous launch
    // has been
    // done.

    private Thread locker;
    private cl_event[] prevWrite;

    public synchronized cl_event[] lockForWrites() {
	Thread t = Thread.currentThread();
	if (locker != null && t != locker) {
	    while (locker != null) {
		try {
		    wait();
		} catch (InterruptedException e) {
		    // ignore
		}
	    }
	}
	locker = t;
	cl_event[] retval = prevWrite;
	prevWrite = null;
	return retval;
    }

    public synchronized void releaseForWrites(cl_event[] events) {
	if (locker != null) {
	    locker = null;
	    notifyAll();
	}
	prevWrite = events;
	for (cl_event e : events) {
	    registerEvent(e);
	}
    }

    @Override
    public String toString() {
	return info.toString();
    }
}
