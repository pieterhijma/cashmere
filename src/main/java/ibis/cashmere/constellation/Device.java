/*
 * Copyright 2018 Vrije Universiteit Amsterdam, The Netherlands
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ibis.cashmere.constellation;

import static ibis.constellation.util.MemorySizes.GB;
import static ibis.constellation.util.MemorySizes.MB;
import static ibis.constellation.util.MemorySizes.toStringBytes;
import static org.jocl.CL.CL_CONTEXT_PLATFORM;
import static org.jocl.CL.CL_DEVICE_NAME;
import static org.jocl.CL.CL_KERNEL_FUNCTION_NAME;
import static org.jocl.CL.CL_MEM_READ_WRITE;
import static org.jocl.CL.CL_PROFILING_COMMAND_QUEUED;
import static org.jocl.CL.CL_PROGRAM_BUILD_LOG;
import static org.jocl.CL.CL_QUEUE_PROFILING_ENABLE;
import static org.jocl.CL.CL_QUEUE_PROPERTIES;
import static org.jocl.CL.CL_TRUE;
import static org.jocl.CL.clBuildProgram;
import static org.jocl.CL.clCreateBuffer;
import static org.jocl.CL.clCreateCommandQueueWithProperties;
import static org.jocl.CL.clCreateContext;
import static org.jocl.CL.clCreateKernelsInProgram;
import static org.jocl.CL.clCreateProgramWithSource;
import static org.jocl.CL.clEnqueueWriteBuffer;
import static org.jocl.CL.clGetDeviceInfo;
import static org.jocl.CL.clGetKernelInfo;
import static org.jocl.CL.clGetProgramBuildInfo;
import static org.jocl.CL.clReleaseMemObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jocl.CL;
import org.jocl.CLException;
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
import org.jocl.cl_queue_properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that represents a many-core <code>Device</code>.
 */
public class Device implements Comparable<Device> {

    /*
     * Administration of different kinds of devices
     */
    private static class DeviceInfo {
        final String name;
        final int speed;
        final String nickName;
        final long memSize;

        DeviceInfo(String name, int speed, String nickName, long memSize) {
            this.name = name;
            this.speed = speed;
            this.nickName = nickName;
            this.memSize = memSize;
        }

        @Override
        public String toString() {
            return nickName;
        }
    }

    private static final Map<String, DeviceInfo> OPENCL_TO_MCL_DEVICE_INFO;
    private static final Set<String> ACCELERATORS;
    static {
        OPENCL_TO_MCL_DEVICE_INFO = new HashMap<String, DeviceInfo>();
        OPENCL_TO_MCL_DEVICE_INFO.put("GeForce GTX 480", new DeviceInfo("fermi", 20, "gtx480", 256 * 5 * MB));
        OPENCL_TO_MCL_DEVICE_INFO.put("GeForce GTX 680", new DeviceInfo("fermi", 40, "gtx680", 256 * 5 * MB));
        OPENCL_TO_MCL_DEVICE_INFO.put("GeForce GTX 980", new DeviceInfo("fermi", 50, "gtx980", 256 * 5 * MB));
        OPENCL_TO_MCL_DEVICE_INFO.put("GeForce GTX TITAN", new DeviceInfo("fermi", 60, "titan", 256 * 5 * MB));
        OPENCL_TO_MCL_DEVICE_INFO.put("GeForce GTX TITAN X", new DeviceInfo("fermi", 60, "titanx", 11 * GB));
        OPENCL_TO_MCL_DEVICE_INFO.put("TITAN X (Pascal)", new DeviceInfo("fermi", 60, "titanx-pascal", 11 * GB));
        OPENCL_TO_MCL_DEVICE_INFO.put("Tesla K20m", new DeviceInfo("fermi", 40, "k20", 4 * GB));
        OPENCL_TO_MCL_DEVICE_INFO.put("Tesla K40c", new DeviceInfo("fermi", 60, "k40", 11 * GB));
        OPENCL_TO_MCL_DEVICE_INFO.put("Tesla C2050", new DeviceInfo("fermi", 10, "c2050", 256 * 5 * MB));
        OPENCL_TO_MCL_DEVICE_INFO.put("Intel(R) Xeon(R) CPU           E5620  @ 2.40GHz",
                new DeviceInfo("xeon_e5620", 1, "xeon_e5620", 256 * 5 * MB));
        OPENCL_TO_MCL_DEVICE_INFO.put("Intel(R) Xeon(R) CPU E5-2630 0 @ 2.30GHz",
                new DeviceInfo("xeon_e5620", 1, "xeon_e5620", 256 * 5 * MB));
        OPENCL_TO_MCL_DEVICE_INFO.put("Intel(R) Xeon(R) CPU           X5650  @ 2.67GHz",
                new DeviceInfo("xeon_e5620", 1, "xeon_e5620", 256 * 5 * MB));
        OPENCL_TO_MCL_DEVICE_INFO.put("Tahiti", new DeviceInfo("hd7970", 60, "hd7970", 256 * 5 * MB));

        // node078, node079
        OPENCL_TO_MCL_DEVICE_INFO.put("Intel(R) Xeon(R) CPU E5-2620 0 @ 2.00GHz",
                new DeviceInfo("xeon_e5620", 1, "xeon_e5620", 256 * 5 * MB));
        OPENCL_TO_MCL_DEVICE_INFO.put("Intel(R) Many Integrated Core Acceleration Card",
                new DeviceInfo("xeon_phi", 10, "xeon_phi", 7 * GB));

        ACCELERATORS = new HashSet<String>();
        ACCELERATORS.add("cpu");
    }

    /*
     * loggers
     */
    private static final Logger logger = LoggerFactory.getLogger("ibis.cashmere.constellation.Device");
    private static final Logger memlogger = LoggerFactory.getLogger("ibis.cashmere.constellation.Device/memory");
    private static final Logger eventLogger = Event.logger;

    /*
     * Administration for the Device in relation to OpenCL
     */

    private Cashmere cashmere;
    private cl_device_id deviceID;

    // these variables are also accessed by Launch
    cl_context context;
    cl_command_queue writeQueue;
    cl_command_queue executeQueue;
    cl_command_queue readQueue;
    int nrKernelLaunches;

    // A static variable that keeps track of all the devices in the compute node
    private static final Map<cl_context, Device> devices = new HashMap<cl_context, Device>();

    // the information for this device
    private DeviceInfo info;

    // the programs compiled for this Device
    private Map<String, cl_program> kernels;

    /*
     * Keeping track of the state of the Device
     */
    // the offset of OpenCL events timings to the host
    private long offsetHostDevice;

    // keeping track of the number of kernels launched
    private int launched;

    // keeping track of the amount of memory that is reserved
    private long memoryReserved;

    /*
     * Arguments and their relation to events
     */

    // mappings from arguments to a kernel to Argument and cl_events objects, etc
    private Map<float[], FloatArrayArgument> floatArrayArguments;
    private Map<double[], DoubleArrayArgument> doubleArrayArguments;
    private Map<int[], IntArrayArgument> intArrayArguments;
    private Map<byte[], ByteArrayArgument> byteArrayArguments;
    private Map<Buffer, BufferArgument> bufferArguments;
    private Map<Pointer, PointerArgument> pointerArguments;

    private Map<float[], cl_event> writeEventsFloats;
    private Map<double[], cl_event> writeEventsDoubles;
    private Map<int[], cl_event> writeEventsInts;
    private Map<byte[], cl_event> writeEventsBytes;
    private Map<Buffer, cl_event> writeEventsBuffers;
    private Map<Pointer, cl_event> writeEventsPointers;

    private Map<cl_event, float[]> writeEventsFloatsInversed;
    private Map<cl_event, double[]> writeEventsDoublesInversed;
    private Map<cl_event, int[]> writeEventsIntsInversed;
    private Map<cl_event, byte[]> writeEventsBytesInversed;
    private Map<cl_event, Buffer> writeEventsBuffersInversed;
    private Map<cl_event, Pointer> writeEventsPointersInversed;

    private Map<Buffer, ArrayList<cl_event>> executeEventsBuffers;
    private Map<Pointer, ArrayList<cl_event>> executeEventsPointers;
    private Map<float[], ArrayList<cl_event>> executeEventsFloats;
    private Map<double[], ArrayList<cl_event>> executeEventsDoubles;
    private Map<int[], ArrayList<cl_event>> executeEventsInts;
    private Map<byte[], ArrayList<cl_event>> executeEventsBytes;

    private Map<String, ArrayList<cl_event>> readBufferEventsMap;

    /*
     * Variables for debugging/logging
     */
    private long nrBytesAllocated = 0;

    /*
     * The constructor
     */
    Device(cl_device_id device, cl_platform_id platform, Cashmere cashmere) {
        this.cashmere = cashmere;
        this.deviceID = device;

        // initialize the context properties
        cl_context_properties contextProperties = new cl_context_properties();
        contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);

        cl_queue_properties queueProperties = new cl_queue_properties();
        queueProperties.addProperty(CL_QUEUE_PROPERTIES, CL_QUEUE_PROFILING_ENABLE);

        // create a context for the device
        this.context = clCreateContext(contextProperties, 1, new cl_device_id[] { device }, null, null, null);
        this.writeQueue = clCreateCommandQueueWithProperties(context, device, queueProperties, null);
        this.executeQueue = clCreateCommandQueueWithProperties(context, device, queueProperties, null);
        this.readQueue = clCreateCommandQueueWithProperties(context, device, queueProperties, null);

        devices.put(context, this);

        this.info = getDeviceInfo(device);
        measureTimeOffset();

        readBufferEventsMap = new HashMap<String, ArrayList<cl_event>>();

        this.kernels = new HashMap<String, cl_program>();

        this.bufferArguments = new IdentityHashMap<Buffer, BufferArgument>();
        this.pointerArguments = new IdentityHashMap<Pointer, PointerArgument>();
        this.floatArrayArguments = new IdentityHashMap<float[], FloatArrayArgument>();
        this.doubleArrayArguments = new IdentityHashMap<double[], DoubleArrayArgument>();
        this.intArrayArguments = new IdentityHashMap<int[], IntArrayArgument>();
        this.byteArrayArguments = new IdentityHashMap<byte[], ByteArrayArgument>();

        this.writeEventsBuffers = new IdentityHashMap<Buffer, cl_event>();
        this.writeEventsPointers = new IdentityHashMap<Pointer, cl_event>();
        this.writeEventsFloats = new IdentityHashMap<float[], cl_event>();
        this.writeEventsDoubles = new IdentityHashMap<double[], cl_event>();
        this.writeEventsInts = new IdentityHashMap<int[], cl_event>();
        this.writeEventsBytes = new IdentityHashMap<byte[], cl_event>();

        this.writeEventsBuffersInversed = new IdentityHashMap<cl_event, Buffer>();
        this.writeEventsPointersInversed = new IdentityHashMap<cl_event, Pointer>();
        this.writeEventsFloatsInversed = new IdentityHashMap<cl_event, float[]>();
        this.writeEventsDoublesInversed = new IdentityHashMap<cl_event, double[]>();
        this.writeEventsIntsInversed = new IdentityHashMap<cl_event, int[]>();
        this.writeEventsBytesInversed = new IdentityHashMap<cl_event, byte[]>();

        this.executeEventsBuffers = new IdentityHashMap<Buffer, ArrayList<cl_event>>();
        this.executeEventsPointers = new IdentityHashMap<Pointer, ArrayList<cl_event>>();
        this.executeEventsFloats = new IdentityHashMap<float[], ArrayList<cl_event>>();
        this.executeEventsDoubles = new IdentityHashMap<double[], ArrayList<cl_event>>();
        this.executeEventsInts = new IdentityHashMap<int[], ArrayList<cl_event>>();
        this.executeEventsBytes = new IdentityHashMap<byte[], ArrayList<cl_event>>();

        this.memoryReserved = 0;
    }

    /*
     * Public methods
     */

    /*
     * General device management
     */

    /**
     * Get the memory capacity of this device.
     *
     * @return the capacity of this device in bytes.
     */
    public long getMemoryCapacity() {
        return info.memSize;
    }

    /**
     * Compares this device with <code>Device</code> device in terms of when a device can launch kernels.
     * <p>
     * This method compares the number of kernel launches on a device to find out when a device is going to terminate.
     *
     * @param device
     *            the device to compare against
     * @return &lt; 0 if this device is expected to terminate its kernels sooner than device, 0 if this device is expected to
     *         terminate its kernels at the same time as the other device, &gt; 0 if this device is expected to terminate its
     *         kernels later than the other device.
     */
    @Override
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
            logger.info("compareTo: " + this + ": expectedTermination = " + expectedTermination + ", " + device
                    + ": expectedTermination = " + expectedTerminationDevice);
        }

        return expectedTermination < expectedTerminationDevice ? -1 : expectedTermination == expectedTerminationDevice ? 0 : 1;
    }

    /**
     * Returns the nickname of this device.
     *
     * @return the nickname of this device.
     */
    @Override
    public String toString() {
        return info.toString();
    }

    /*
     * Managing memory on the device
     */

    /**
     * Allocates <code>size</code> bytes of memory on the device.
     *
     * @param size
     *            the number of bytes to allocate
     * @return a <code>Pointer</code> to the memory on the device
     */
    public Pointer allocate(long size) {
        PointerArgument a = new PointerArgument(context, readQueue);
        a.createBuffer(context, size, null);

        Pointer pointer = a.getPointer();
        synchronized (pointerArguments) {
            pointerArguments.put(pointer, a);
        }
        return pointer;
    }

    /*
     * The copy methods call performCopy that will make a new BufferArgument. The
     * constructor of this BufferArgument calls Argument.writeBuffer that will do
     * an clEnqueueWriteBuffer without any events to wait on. This method will
     * return an event that is registered with registerEvent() and then added to
     * writeBufferEvents. This is an ArrayList of events that is will only hold
     * one element. This element, a cl_event is mapped to the key Buffer a in
     * the writeEventsBuffers map.
     *
     * Synchronization comments.
     *
     * PerformCopy keeps track of which Buffer belongs to which BufferArgument.
     * If another copy is being done of the same Buffer, we increment
     * ArrayArgument.referenceCount.
     *
     */
    /**
     * Copy a buffer to the device. After completion, the data has a representation on the host and on the device. This means that
     * after a kernel execution updates the data, the host representation can be updated with a {@link #get(Buffer)}.
     *
     * @param buffer
     *            a <code>Buffer</code> to be copied to the device
     * @param d
     *            indicates the direction of the copied value (only for reading, only for writing, or for both)
     */
    public void copy(Buffer buffer, Argument.Direction d) {
        performCopy(bufferArguments, writeEventsBuffers, writeEventsBuffersInversed, buffer,
                (writeBufferEvents) -> new BufferArgument(context, writeQueue, readQueue, writeBufferEvents, buffer, d),
                () -> buffer.capacity());
    }

    /**
     * Copy an array of floats to the device. After completion, the data has a representation on the host and on the device. This
     * means that after a kernel execution updates the data, the host representation can be updated with a {@link #get(float[])}.
     *
     * @param a
     *            a <code>float</code> array to be copied to the devie
     * @param d
     *            indicates the direction of the copied value (only for reading, only for writing, or for both)
     */
    public void copy(float[] a, Argument.Direction d) {
        performCopy(floatArrayArguments, writeEventsFloats, writeEventsFloatsInversed, a,
                (x) -> new FloatArrayArgument(context, writeQueue, readQueue, x, a, d), () -> a.length * 4);
    }

    /**
     * Copy an array of doubles to the device. After completion, the data has a representation on the host and on the device. This
     * means that after a kernel execution updates the data, the host representation can be updated with a {@link #get(double[])}.
     *
     * @param a
     *            a <code>double</code> array to be copied to the devie
     * @param d
     *            indicates the direction of the copied value (only for reading, only for writing, or for both)
     */
    public void copy(double[] a, Argument.Direction d) {
        performCopy(doubleArrayArguments, writeEventsDoubles, writeEventsDoublesInversed, a,
                (x) -> new DoubleArrayArgument(context, writeQueue, readQueue, x, a, d), () -> a.length * 8);
    }

    /**
     * Copy an array of ints to the device. After completion, the data has a representation on the host and on the device. This
     * means that after a kernel execution updates the data, the host representation can be updated with a {@link #get(int[])}.
     *
     * @param a
     *            a <code>int</code> array to be copied to the devie
     * @param d
     *            indicates the direction of the copied value (only for reading, only for writing, or for both)
     */
    public void copy(int[] a, Argument.Direction d) {
        performCopy(intArrayArguments, writeEventsInts, writeEventsIntsInversed, a,
                (x) -> new IntArrayArgument(context, writeQueue, readQueue, x, a, d), () -> a.length * 4);
    }

    /**
     * Copy an array of bytes to the device. After completion, the data has a representation on the host and on the device. This
     * means that after a kernel execution updates the data, the host representation can be updated with a {@link #get(byte[])}.
     *
     * @param a
     *            a <code>byte</code> array to be copied to the devie
     * @param d
     *            indicates the direction of the copied value (only for reading, only for writing, or for both)
     */
    public void copy(byte[] a, Argument.Direction d) {
        performCopy(byteArrayArguments, writeEventsBytes, writeEventsBytesInversed, a,
                (x) -> new ByteArrayArgument(context, writeQueue, readQueue, x, a, d), () -> a.length);
    }

    /**
     * Copy a buffer to memory on the device. Compared to {@link #copy(Buffer,Argument.Direction)}, this version is not coupled
     * with <code>from</code>.
     *
     * @param from
     *            a <code>Buffer</code> to be copied to the device
     * @param to
     *            a <code>Pointer</code> representing the address of the memory to which is copied
     */
    public void copy(Buffer from, Pointer to) {
        PointerArgument a;
        cl_event writePointerEvent = null;
        synchronized (pointerArguments) {
            a = pointerArguments.get(to);
            if (a != null) {
                writePointerEvent = a.writeBufferNoCreateBuffer(context, writeQueue, null, from.capacity(),
                        Pointer.to(from.byteBuffer));
            } else {
                throw new Error("Unknown pointer");
            }
        }

        synchronized (writeEventsPointers) {
            Logger logger = Device.logger.isDebugEnabled() ? Device.logger : Device.eventLogger;
            if (logger.isDebugEnabled()) {
                logger.debug("Copy Buffer to Pointer: event = " + writePointerEvent);
                logger.debug("storing last event in Device.writeEvents<type>");
                logger.debug("storing last event in Device.writeEventsInversed<type>");
            }
            cl_event old_event = writeEventsPointers.remove(to);
            if (old_event != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("old {} associated with {}, about to clean old event", old_event, to);
                }
                Event.clean(old_event);
                Pointer p = writeEventsPointersInversed.remove(old_event); // Added
                if (p == null) {
                    throw new Error("Inconsistency in writeEventsPointers");
                }
            }
            writeEventsPointers.put(to, writePointerEvent);
            writeEventsPointersInversed.put(writePointerEvent, to);
        }
    }

    /**
     * Whether the buffer is available on the device.
     *
     * @param buffer
     *            the <code>Buffer</code> of which is checked to be available on the device
     * @return true if and only if the buffer is on the device
     */
    public boolean available(Buffer buffer) {
        return performAvailable(buffer, bufferArguments);
    }

    /**
     * Whether the array of floats is available on the device.
     *
     * @param a
     *            the array of which is checked to be available on the device
     * @return true if and only if the buffer is on the device
     */
    public boolean available(float[] a) {
        return performAvailable(a, floatArrayArguments);
    }

    /**
     * Whether the array of bytes is available on the device.
     *
     * @param a
     *            the array of which is checked to be available on the device
     * @return true if and only if the buffer is on the device
     */
    public boolean available(byte[] a) {
        return performAvailable(a, byteArrayArguments);
    }

    /**
     * Whether the array of ints is available on the device.
     *
     * @param a
     *            the array of which is checked to be available on the device
     * @return true if and only if the buffer is on the device
     */
    public boolean available(int[] a) {
        return performAvailable(a, intArrayArguments);
    }

    /**
     * Whether the array of doubles is available on the device.
     *
     * @param a
     *            the array of which is checked to be available on the device
     * @return true if and only if the buffer is on the device
     */
    public boolean available(double[] a) {
        return performAvailable(a, doubleArrayArguments);
    }

    /**
     * Get the <code>Buffer</code> from the device. The <code>Buffer</code> is not removed from the device.
     *
     * @param buffer
     *            a <code>Buffer</code> in which the data
     */
    public void get(Buffer buffer) {
        performGet(buffer, bufferArguments, executeEventsBuffers);
    }

    /**
     * Get the array of floats from the device. The representation of the array on the device is not removed from the device.
     *
     * @param a
     *            the <code>float</code> array to which the contents of the device representation is copied
     */
    public void get(float[] a) {
        performGet(a, floatArrayArguments, executeEventsFloats);
    }

    /**
     * Get the array of doubles from the device. The representation of the array on the device is not removed from the device.
     *
     * @param a
     *            the <code>double</code> array to which the contents of the device representation is copied
     */
    public void get(double[] a) {
        performGet(a, doubleArrayArguments, executeEventsDoubles);
    }

    /**
     * Get the array of ints from the device. The representation of the array on the device is not removed from the device.
     *
     * @param a
     *            the <code>int</code> array to which the contents of the device representation is copied
     */
    public void get(int[] a) {
        performGet(a, intArrayArguments, executeEventsInts);
    }

    /**
     * Get the array of bytes from the device. The representation of the array on the device is not removed from the device.
     *
     * @param a
     *            the <code>byte</code> array to which the contents of the device representation is copied
     */
    public void get(byte[] a) {
        performGet(a, byteArrayArguments, executeEventsBytes);
    }

    /**
     * Get the contents of the memory on the device represented by <code>Pointer</code> <code>from</code> into <code>to</code>.
     *
     * @param to
     *            the <code>Buffer</code> to copy to
     * @param from
     *            the memory from which is copied
     */
    public void get(Buffer to, Pointer from) {
        performPointerGet(from, Pointer.to(to.byteBuffer), to.capacity());
    }

    /**
     * Get the contents of the memory on the device represented by <code>Pointer</code> <code>from</code> into <code>to</code>.
     *
     * @param to
     *            the array of doubles to copy to
     * @param from
     *            the memory from which is copied
     */
    public void get(double[] to, Pointer from) {
        performPointerGet(from, Pointer.to(to), to.length * Sizeof.cl_double);
    }

    /**
     * Get the contents of the memory on the device represented by <code>Pointer</code> <code>from</code> into <code>to</code>.
     *
     * @param to
     *            the array of floats to copy to
     * @param from
     *            the memory from which is copied
     */
    public void get(float[] to, Pointer from) {
        performPointerGet(from, Pointer.to(to), to.length * Sizeof.cl_float);
    }

    /*
     * Synchronization comments
     *
     * Protects: bufferArguments, referenceCount, executeEventsBuffers,
     * writeEventsBuffers
     */
    /**
     * Clean <code>Buffer</code> from the device.
     *
     * @param buffer
     *            the <code>Buffer</code> to be cleaned
     * @return the reference count of the <code>Buffer</code> value
     */
    public int clean(Buffer buffer) {
        if (buffer != null) {
            return performClean(buffer, bufferArguments, buffer.capacity(), () -> {
                return removeEvent(buffer, executeEventsBuffers, writeEventsBuffers, writeEventsBuffersInversed);
            });
        }
        return -1;
    }

    /**
     * Clean the float array from the device.
     *
     * @param a
     *            the array to be cleaned
     * @return the reference count of the array
     */
    public int clean(float[] a) {
        if (a != null) {
            return performClean(a, floatArrayArguments, a.length * 4, () -> {
                return removeEvent(a, executeEventsFloats, writeEventsFloats, writeEventsFloatsInversed);
            });
        }
        return -1;
    }

    /**
     * Clean the byte array from the device.
     *
     * @param a
     *            the array to be cleaned
     * @return the reference count of the array
     */
    public int clean(byte[] a) {
        if (a != null) {
            return performClean(a, byteArrayArguments, a.length, () -> {
                return removeEvent(a, executeEventsBytes, writeEventsBytes, writeEventsBytesInversed);
            });
        }
        return -1;
    }

    /**
     * Clean the int array from the device.
     *
     * @param a
     *            the array to be cleaned
     * @return the reference count of the array
     */
    public int clean(int[] a) {
        if (a != null) {
            return performClean(a, intArrayArguments, a.length * 4, () -> {
                return removeEvent(a, executeEventsInts, writeEventsInts, writeEventsIntsInversed);
            });
        }
        return -1;
    }

    /**
     * Clean the double array from the device.
     *
     * @param a
     *            the array to be cleaned
     * @return the reference count of the array
     */
    public int clean(double[] a) {
        if (a != null) {
            return performClean(a, doubleArrayArguments, a.length * 8, () -> {
                return removeEvent(a, executeEventsDoubles, writeEventsDoubles, writeEventsDoublesInversed);
            });
        }
        return -1;
    }

    /*
     * Package methods
     */

    /*
     * Initialization of the device
     */

    void initializeLibrary(InitLibraryFunction func) {
        func.initialize(context, executeQueue);
    }

    void deinitializeLibrary(DeInitLibraryFunction func) {
        func.deinitialize();
    }

    long getOffsetHostDevice() {
        return offsetHostDevice;
    }

    void addKernel(String kernelSource) {
        cl_program program = clCreateProgramWithSource(context, 1, new String[] { kernelSource },
                new long[] { kernelSource.length() }, null);

        clBuildProgram(program, 0, null, null, null, null);
        // clBuildProgram(program, 0, null,
        // "-cl-nv-verbose -cl-nv-maxrregcount=20", null, null);
        // assuming there is only one kernel for now.

        long size[] = new long[1];
        clGetProgramBuildInfo(program, deviceID, CL_PROGRAM_BUILD_LOG, 0, null, size);
        byte buffer[] = new byte[(int) size[0]];
        clGetProgramBuildInfo(program, deviceID, CL_PROGRAM_BUILD_LOG, buffer.length, Pointer.to(buffer), null);
        String log = new String(buffer, 0, buffer.length - 1).trim();

        if (log.length() > 0) {
            System.out.println(log);
        }

        cl_kernel[] kernelArray = new cl_kernel[1];
        clCreateKernelsInProgram(program, 1, kernelArray, null);
        cl_kernel kernel = kernelArray[0];

        String nameKernel = getName(kernel);
        this.kernels.put(nameKernel, program);

        // writeBinary(program, nameKernel, info.name);

        logger.info("Registered kernel " + nameKernel + " on device " + info.nickName);
    }

    /*
     * Memory allocation and arguments
     */
    <T> T withAllocationError(Supplier<T> s) {
        try {
            return s.get();
        } catch (CLException e) {
            if (e.getStatus() == CL.CL_MEM_OBJECT_ALLOCATION_FAILURE) {
                throw new Error("Got memory allocation failure, you should reserve memory", e);
            } else {
                throw new Error("Got exception", e);
            }
        }
    }

    FloatArrayArgument getArgument(float[] a) {
        return getArgumentGeneric(a, floatArrayArguments);
    }

    DoubleArrayArgument getArgument(double[] a) {
        return getArgumentGeneric(a, doubleArrayArguments);
    }

    BufferArgument getArgument(Buffer a) {
        return getArgumentGeneric(a, bufferArguments);
    }

    PointerArgument getArgument(Pointer a) {
        return getArgumentGeneric(a, pointerArguments);
    }

    IntArrayArgument getArgument(int[] a) {
        return getArgumentGeneric(a, intArrayArguments);
    }

    ByteArrayArgument getArgument(byte[] a) {
        return getArgumentGeneric(a, byteArrayArguments);
    }

    /*
     * Setting/querying the state of the device
     */
    static Device getDevice(cl_context context) {
        return devices.get(context);
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
    }

    boolean asynchReads() {
        return cashmere.isAsynchReads();
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

    /*
     * Handling events
     */

    cl_event getWriteEvent(float[] a) {
        return getWriteEventGeneric(a, writeEventsFloats);
    }

    cl_event getWriteEvent(double[] a) {
        return getWriteEventGeneric(a, writeEventsDoubles);
    }

    cl_event getWriteEvent(int[] a) {
        return getWriteEventGeneric(a, writeEventsInts);
    }

    cl_event getWriteEvent(byte[] a) {
        return getWriteEventGeneric(a, writeEventsBytes);
    }

    cl_event getWriteEvent(Buffer a) {
        return getWriteEventGeneric(a, writeEventsBuffers);
    }

    cl_event getWriteEvent(Pointer a) {
        return getWriteEventGeneric(a, writeEventsPointers);
    }

    void addExecuteEvent(float[] a, cl_event event) {
        processExecuteEvent(event, getExecuteEvents(a, executeEventsFloats));
    }

    void addExecuteEvent(double[] a, cl_event event) {
        processExecuteEvent(event, getExecuteEvents(a, executeEventsDoubles));
    }

    void addExecuteEvent(int[] a, cl_event event) {
        processExecuteEvent(event, getExecuteEvents(a, executeEventsInts));
    }

    void addExecuteEvent(byte[] a, cl_event event) {
        processExecuteEvent(event, getExecuteEvents(a, executeEventsBytes));
    }

    void addExecuteEvent(Buffer a, cl_event event) {
        processExecuteEvent(event, getExecuteEvents(a, executeEventsBuffers));
    }

    void addExecuteEvent(Pointer a, cl_event event) {
        processExecuteEvent(event, getExecuteEvents(a, executeEventsPointers));
    }

    void removeExecuteEvent(float[] a, cl_event event) {
        removeExecuteEvent(event, getExecuteEvents(a, executeEventsFloats));
    }

    void removeExecuteEvent(double[] a, cl_event event) {
        removeExecuteEvent(event, getExecuteEvents(a, executeEventsDoubles));
    }

    void removeExecuteEvent(int[] a, cl_event event) {
        removeExecuteEvent(event, getExecuteEvents(a, executeEventsInts));
    }

    void removeExecuteEvent(byte[] a, cl_event event) {
        removeExecuteEvent(event, getExecuteEvents(a, executeEventsBytes));
    }

    void removeExecuteEvent(Buffer a, cl_event event) {
        removeExecuteEvent(event, getExecuteEvents(a, executeEventsBuffers));
    }

    void removeExecuteEvent(Pointer a, cl_event event) {
        removeExecuteEvent(event, getExecuteEvents(a, executeEventsPointers));
    }

    void cleanWriteEvents(ArrayList<cl_event> events) {
        for (cl_event event : events) {
            cleanWriteEvent(event, writeEventsFloats, writeEventsFloatsInversed);
            cleanWriteEvent(event, writeEventsDoubles, writeEventsDoublesInversed);
            cleanWriteEvent(event, writeEventsInts, writeEventsIntsInversed);
            cleanWriteEvent(event, writeEventsBytes, writeEventsBytesInversed);
            cleanWriteEvent(event, writeEventsBuffers, writeEventsBuffersInversed);
            cleanWriteEvent(event, writeEventsPointers, writeEventsPointersInversed);
        }
    }

    /*
     * Debugging
     */

    void showExecuteEvents() {
        if (eventLogger.isDebugEnabled()) {
            showEvents(executeEventsBuffers, "Buffer");
            showEvents(executeEventsPointers, "Pointer");
            showEvents(executeEventsFloats, "Floats");
            showEvents(executeEventsDoubles, "Doubles");
            showEvents(executeEventsInts, "Ints");
            showEvents(executeEventsBytes, "Bytes");
        }
    }

    /*
     * Private methods
     */

    /*
     * Initialization of the device
     */

    private DeviceInfo getDeviceInfo(cl_device_id device) {
        long size[] = new long[1];
        clGetDeviceInfo(device, CL_DEVICE_NAME, 0, null, size);
        byte buffer[] = new byte[(int) size[0]];
        clGetDeviceInfo(device, CL_DEVICE_NAME, buffer.length, Pointer.to(buffer), null);
        String openCLDeviceName = new String(buffer, 0, buffer.length - 1).trim();

        if (OPENCL_TO_MCL_DEVICE_INFO.containsKey(openCLDeviceName)) {
            DeviceInfo deviceInfo = OPENCL_TO_MCL_DEVICE_INFO.get(openCLDeviceName);
            logger.info("Found MCL device: " + deviceInfo.name + " (" + openCLDeviceName + ")");
            return deviceInfo;
        } else {
            logger.warn("Found OpenCL device: " + openCLDeviceName);
            logger.warn("This is an unkown MCL device, please add it to MCL");
            return new DeviceInfo("unknown", 1, "Unknown", 1 * GB);
        }
    }

    private String getName(cl_kernel kernel) {
        long size[] = new long[1];
        clGetKernelInfo(kernel, CL_KERNEL_FUNCTION_NAME, 0, null, size);
        byte buffer[] = new byte[(int) size[0]];
        clGetKernelInfo(kernel, CL_KERNEL_FUNCTION_NAME, buffer.length, Pointer.to(buffer), null);
        return new String(buffer, 0, buffer.length - 1);
    }

    private void measureTimeOffset() {
        float f[] = { 0.0f };
        Pointer fPointer = Pointer.to(f);
        cl_mem memObject = clCreateBuffer(context, CL_MEM_READ_WRITE, Sizeof.cl_float, null, null);
        cl_event event = new cl_event();
        long startHost = System.nanoTime();
        clEnqueueWriteBuffer(writeQueue, memObject, CL_TRUE, 0, Sizeof.cl_float, fPointer, 0, null, event);
        clReleaseMemObject(memObject);
        long startDevice = Cashmere.getValue(event, CL_PROFILING_COMMAND_QUEUED);

        this.offsetHostDevice = startHost - startDevice;
    }

    private cl_kernel getKernelProgram(cl_program program) {
        cl_kernel[] kernelArray = new cl_kernel[1];
        clCreateKernelsInProgram(program, 1, kernelArray, null);
        cl_kernel kernel = kernelArray[0];
        return kernel;
    }

    /*
     * Managing arguments/memory on the device
     */

    private <K, V extends ArrayArgument> void performCopy(Map<K, V> map, Map<K, cl_event> writeEvents,
            Map<cl_event, K> writeEventsInversed, K k, Function<ArrayList<cl_event>, V> makeNewArgument, Supplier<Integer> size) {

        boolean madeNewArgument;
        ArrayList<cl_event> writeBufferEvents = null;
        synchronized (map) {
            if (!map.containsKey(k)) {
                writeBufferEvents = new ArrayList<cl_event>();
                map.put(k, makeNewArgument.apply(writeBufferEvents));
                madeNewArgument = true;

            } else {
                V v = map.get(k);
                v.referenceCount++;
                if (memlogger.isDebugEnabled()) {
                    memlogger.debug("Reference count for {}: {}", v, v.referenceCount);
                }
                madeNewArgument = false;
            }
        }

        if (madeNewArgument) {
            if (memlogger.isDebugEnabled()) {
                synchronized (this) {
                    nrBytesAllocated += size.get();
                    memlogger.debug(String.format("Allocated: %6s, total: %s: %s", toStringBytes(size.get()),
                            toStringBytes(nrBytesAllocated), map.get(k)));
                }

            }
            synchronized (writeEvents) {
                if (writeBufferEvents.size() == 1) {
                    if (eventLogger.isDebugEnabled()) {
                        eventLogger.debug("Copy Buffer: event = " + writeBufferEvents.get(0));
                        eventLogger.debug("storing last event in Device.writeEvents<type>");
                        eventLogger.debug("storing last event in Device.writeEventsInversed<type>");
                    }
                    cl_event event = writeBufferEvents.get(0);
                    writeEvents.put(k, event);
                    writeEventsInversed.put(event, k);
                } else {
                    throw new Error("Should not happen");
                }
            }
        }
    }

    private <K, V extends ArrayArgument> boolean performAvailable(K k, Map<K, V> map) {
        synchronized (map) {
            return map.get(k) != null;
        }
    }

    private <K, V extends ArrayArgument> void performGet(K k, Map<K, V> map, Map<K, ArrayList<cl_event>> executeEvents) {
        V v;
        synchronized (map) {
            v = map.get(k);
        }
        ArrayList<cl_event> execEvents = getExecuteEvents(k, executeEvents);
        ArrayList<cl_event> readBufferEvents = getReadBufferEvents();
        v.scheduleReads(execEvents, readBufferEvents, false);
        releaseEvents(readBufferEvents);
        releaseEvents(execEvents);
        // we just remove it immediately
        synchronized (executeEvents) {
            executeEvents.remove(k);
        }
    }

    private void performPointerGet(Pointer from, Pointer to, long size) {
        PointerArgument a;
        synchronized (pointerArguments) {
            a = pointerArguments.get(from);
        }
        ArrayList<cl_event> execEvents = getExecuteEvents(from, executeEventsPointers);
        ArrayList<cl_event> readBufferEvents = getReadBufferEvents();
        a.scheduleReads(to, size, execEvents, readBufferEvents, false);
        synchronized (executeEventsPointers) {
            executeEventsPointers.remove(from);
        }
        releaseEvents(readBufferEvents);
        releaseEvents(execEvents);
    }

    private <K, V extends ArrayArgument> int performClean(K k, Map<K, V> map, int size, Supplier<cl_event> cleanEvents) {
        V v;
        synchronized (map) {
            v = map.get(k);
        }
        if (v == null) {
            return -1;
        }

        boolean performClean;

        synchronized (v) {
            v.referenceCount--;
            if (v.referenceCount == 0) {
                memlogger.debug("  about to clean");
                v.clean();
                memlogger.debug("  did a clean");
                performClean = true;
            } else {
                if (memlogger.isDebugEnabled()) {
                    memlogger.debug("referenceCount for {}: {}", v, v.referenceCount);
                }
                performClean = false;
            }
        }

        if (performClean) {
            synchronized (map) {
                map.remove(k);
            }
            cleanEvents.get();

            if (memlogger.isDebugEnabled()) {
                synchronized (this) {
                    nrBytesAllocated -= size;
                    memlogger.debug(String.format("Deallocated: %6s, total: %s %s", toStringBytes(size),
                            toStringBytes(nrBytesAllocated), v));
                }
            }
        }

        return v.referenceCount;
    }

    private <K, V extends ArrayArgument> V getArgumentGeneric(K k, Map<K, V> map) {
        synchronized (map) {
            return map.get(k);
        }
    }

    /*
     * Handling events
     */

    private void releaseEvents(ArrayList<cl_event> events) {
        for (cl_event event : events) {
            Event.clean(event);
        }
        events.clear();
    }

    private <K> cl_event removeEvent(K k, Map<K, ArrayList<cl_event>> executeEvents, Map<K, cl_event> writeEvents,
            Map<cl_event, K> writeEventsInversed) {

        synchronized (executeEvents) {
            executeEvents.remove(k);
        }
        synchronized (writeEvents) {
            cl_event event = writeEvents.remove(k);
            if (event != null) {
                K kStored = writeEventsInversed.get(event);
                if (eventLogger.isDebugEnabled()) {
                    eventLogger.debug("removing {} from Device.writeEvents<type>", event);
                }
                if (kStored == k) {
                    writeEventsInversed.remove(event);
                    eventLogger.debug("removing {} from Device.writeEventsInversed<type>", event);
                    eventLogger.debug("about to clean {}", event);
                    Event.clean(event);
                }
            }
            return event;
        }
    }

    private <K> ArrayList<cl_event> getExecuteEvents(K k, Map<K, ArrayList<cl_event>> map) {
        ArrayList<cl_event> events;
        synchronized (map) {
            events = map.get(k);
            if (events == null) {
                events = new ArrayList<cl_event>();
                map.put(k, events);
            }
        }
        return events;
    }

    private <K> cl_event getWriteEventGeneric(K k, Map<K, cl_event> writeEvents) {
        synchronized (writeEvents) {
            return writeEvents.get(k);
        }
    }

    private synchronized ArrayList<cl_event> getReadBufferEvents() {
        String thread = Thread.currentThread().getName();
        ArrayList<cl_event> readBufferEvents = readBufferEventsMap.get(thread);
        if (readBufferEvents == null) {
            readBufferEvents = new ArrayList<cl_event>();
            readBufferEventsMap.put(thread, readBufferEvents);
        }
        return readBufferEvents;
    }

    private void processExecuteEvent(cl_event event, ArrayList<cl_event> events) {
        synchronized (events) {
            events.add(event);
        }
        if (eventLogger.isDebugEnabled()) {
            eventLogger.debug("storing {} in Device.executeEvents<type>", event);
        }
    }

    private void removeExecuteEvent(cl_event event, ArrayList<cl_event> events) {
        synchronized (events) {
            events.remove(event);
        }
        if (eventLogger.isDebugEnabled()) {
            eventLogger.debug("removing {} from Device.executeEvents<type>", event);
        }
    }

    private <T> void cleanWriteEvent(cl_event event, Map<T, cl_event> writeEvents, Map<cl_event, T> writeEventsInversed) {
        synchronized (writeEvents) {
            T t = writeEventsInversed.get(event);
            if (t != null) {
                cl_event storedEvent = writeEvents.get(t);
                if (storedEvent == event) {
                    writeEvents.remove(t);
                    writeEventsInversed.remove(event);
                    if (eventLogger.isDebugEnabled()) {
                        eventLogger.debug("removing {} from Device.writeEvents<type>", event);
                        eventLogger.debug("removing {} from Device.writeEventsInversed<type>", event);
                        eventLogger.debug("about to clean {}", event);
                    }
                    Event.clean(event);
                }
            }
        }
    }

    /*
     * Debugging
     */
    private void writeBinary(cl_program program, String nameKernel, String nameDevice) {
        // int nrDevices = 1;
        // long[] sizes = new long[nrDevices];
        // clGetProgramInfo(program, CL_PROGRAM_BINARY_SIZES, nrDevices * Sizeof.size_t, Pointer.to(sizes), null);
        // byte[][] buffers = new byte[nrDevices][];
        // Pointer[] pointers = new Pointer[nrDevices];
        // for (int i = 0; i < nrDevices; i++) {
        //     buffers[i] = new byte[(int) sizes[i] + 1];
        //     pointers[i] = Pointer.to(buffers[i]);
        // }
        // Pointer p = Pointer.to(pointers);
        // clGetProgramInfo(program, CL_PROGRAM_BINARIES, nrDevices * Sizeof.POINTER, p, null);
        // String binary = new String(buffers[0], 0, buffers[0].length - 1).trim();
        // try {
        //     PrintStream out = new PrintStream(new File(nameDevice + "_" + nameKernel + ".ptx"));
        //     out.println(binary);
        //     out.close();
        // } catch (IOException e) {
        //     System.err.println(e.getMessage());
        // }
    }

    private <K> void showEvents(Map<K, ArrayList<cl_event>> executeEvents, String type) {
        int size = executeEvents.size();
        if (size > 0) {
            eventLogger.debug("executeEvents{} has {} elements", type, size);
            Collection<ArrayList<cl_event>> values = executeEvents.values();
            for (ArrayList<cl_event> events : values) {
                eventLogger.debug("  key 1:");
                for (cl_event event : events) {
                    eventLogger.debug("    {}", event);
                    // Event.showEvent("execute", event);
                    // can segfault if the even has been released
                }
            }
        }
    }
}
