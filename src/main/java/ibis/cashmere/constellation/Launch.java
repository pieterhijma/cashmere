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

import static org.jocl.CL.CL_PROFILING_COMMAND_END;
import static org.jocl.CL.CL_PROFILING_COMMAND_START;
import static org.jocl.CL.clWaitForEvents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.Set;

import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The abstract base class for {@link KernelLaunch} and {@link LibFuncLaunch} that contains shared code.
 */
public abstract class Launch {

    protected static final Logger logger = LoggerFactory.getLogger("ibis.cashmere.constellation.Launch");
    protected static final Logger eventLogger = Event.logger;

    protected String name;
    protected String threadName;
    protected Device device;

    protected int nrArgs;
    protected ArrayList<Argument> argsToClean;

    protected cl_context context;
    protected cl_command_queue writeQueue;
    protected cl_command_queue executeQueue;
    protected cl_command_queue readQueue;

    protected ArrayList<cl_event> writeBufferEvents;
    protected ArrayList<cl_event> executeEvents;
    protected ArrayList<cl_event> readBufferEvents;

    protected boolean launched;
    protected boolean finished;

    private static final int NR_LAUNCHES_TO_RETAIN = 2;

    private Set<float[]> noCopyFloats;
    private Set<double[]> noCopyDoubles;
    private Set<int[]> noCopyInts;
    private Set<byte[]> noCopyBytes;
    private Set<Buffer> noCopyBuffers;
    private Set<Pointer> noCopyPointers;

    private static ThreadLocal<Deque<Launch>> launches = ThreadLocal.<Deque<Launch>> withInitial(() -> new LinkedList<Launch>());

    // A Launch can only be created from a subclass
    protected Launch(String name, String threadName, Device device) {
        this.name = name;
        this.threadName = threadName;
        this.device = device;

        this.nrArgs = 0;
        this.argsToClean = new ArrayList<Argument>();

        this.context = device.context;
        this.writeQueue = device.writeQueue;
        this.executeQueue = device.executeQueue;
        this.readQueue = device.readQueue;

        this.writeBufferEvents = new ArrayList<cl_event>();
        this.executeEvents = new ArrayList<cl_event>();
        this.readBufferEvents = new ArrayList<cl_event>();

        this.launched = false;
        this.finished = false;

        this.noCopyFloats = new HashSet<float[]>();
        this.noCopyDoubles = new HashSet<double[]>();
        this.noCopyInts = new HashSet<int[]>();
        this.noCopyBytes = new HashSet<byte[]>();
        this.noCopyBuffers = new HashSet<Buffer>();
        this.noCopyPointers = Collections.newSetFromMap(new IdentityHashMap<Pointer, Boolean>());
    }

    /*
     * Public code, generic to Kernel and LibFunc launches
     */
    /**
     * Set an argument for this launch. The value will be copied to the device before the kernel launches.
     *
     * @param i
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgument(int i, Argument.Direction d) {
        IntArgument arg = new IntArgument(i, d);
        setArgument(Sizeof.cl_int, arg);
        argsToClean.add(arg);
    }

    /**
     * Set an argument for this launch. The value will be copied to the device before the kernel launches.
     *
     * @param f
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgument(float f, Argument.Direction d) {
        FloatArgument arg = new FloatArgument(f, d);
        setArgument(Sizeof.cl_float, arg);
        argsToClean.add(arg);
    }

    /**
     * Set an argument for this launch. The value will be copied to the device before the kernel launches.
     *
     * @param f
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgument(double f, Argument.Direction d) {
        DoubleArgument arg = new DoubleArgument(f, d);
        setArgument(Sizeof.cl_double, arg);
        argsToClean.add(arg);
    }

    /**
     * Set an argument for this launch. The value will be copied to the device before the kernel launches.
     *
     * @param a
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgument(float[][] a, Argument.Direction d) {
        FloatArray2DArgument arg = new FloatArray2DArgument(context, writeQueue, readQueue, writeBufferEvents, a, d);
        setArgument(Sizeof.cl_mem, arg);
        argsToClean.add(arg);
    }

    /**
     * Set an argument for this launch. The value will be copied to the device before the kernel launches.
     *
     * @param a
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgument(float[] a, Argument.Direction d) {
        FloatArrayArgument arg = new FloatArrayArgument(context, writeQueue, readQueue, writeBufferEvents, a, d);
        setArgument(Sizeof.cl_mem, arg);
        argsToClean.add(arg);
    }

    /**
     * Set an argument for this launch. The value will be copied to the device before the kernel launches.
     *
     * @param a
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgument(double[] a, Argument.Direction d) {
        DoubleArrayArgument arg = new DoubleArrayArgument(context, writeQueue, readQueue, writeBufferEvents, a, d);
        setArgument(Sizeof.cl_mem, arg);
        argsToClean.add(arg);
    }

    /**
     * Set an argument for this launch. The value will be copied to the device before the kernel launches.
     *
     * @param buffer
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgument(Buffer buffer, Argument.Direction d) {
        BufferArgument arg = new BufferArgument(context, writeQueue, readQueue, writeBufferEvents, buffer, d);
        setArgument(Sizeof.cl_mem, arg);
        argsToClean.add(arg);
    }

    /**
     * Set an argument for this launch. This method will throw an UnsupportedOperationException because <code>Pointer</code>
     * <code>p</code> is a pointer that points to device memory. Hence, it cannot be copied. The "noCopy" variant should be used.
     * This variant is available in the Cashmere library because the MCL compiler may generate this variant.
     *
     * @param p
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgument(Pointer p, Argument.Direction d) {
        throw new UnsupportedOperationException("Cannot set pointer argument and expect to copy");
    }

    /**
     * Set an argument for this launch. The value will be copied to the device before the kernel launches.
     *
     * @param a
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgument(int[] a, Argument.Direction d) {
        IntArrayArgument arg = new IntArrayArgument(context, writeQueue, readQueue, writeBufferEvents, a, d);
        setArgument(Sizeof.cl_mem, arg);
        argsToClean.add(arg);
    }

    /**
     * Set an argument for this launch. The value will be copied to the device before the kernel launches.
     *
     * @param a
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgument(byte[] a, Argument.Direction d) {
        ByteArrayArgument arg = new ByteArrayArgument(context, writeQueue, readQueue, writeBufferEvents, a, d);
        setArgument(Sizeof.cl_mem, arg);
        argsToClean.add(arg);
    }

    /**
     * Set an argument for this launch. The user is reponsible for copying the data to the device using
     * {@link Device#copy(float[],Argument.Direction)}.
     *
     * @param a
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgumentNoCopy(float[] a, Argument.Direction d) {
        FloatArrayArgument arg = device.getArgument(a);
        setArgument(Sizeof.cl_mem, arg);

        noCopyFloats.add(a);
        // make the execute dependent on this copy.
        addWriteEvent(device.getWriteEvent(a));
    }

    /**
     * Set an argument for this launch. The user is reponsible for copying the data to the device using
     * {@link Device#copy(double[],Argument.Direction)}.
     *
     * @param a
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgumentNoCopy(double[] a, Argument.Direction d) {
        DoubleArrayArgument arg = device.getArgument(a);
        setArgument(Sizeof.cl_mem, arg);

        noCopyDoubles.add(a);
        // make the execute dependent on this copy.
        addWriteEvent(device.getWriteEvent(a));
    }

    /**
     * Set an argument for this launch. The user is reponsible for copying the data to the device using
     * {@link Device#copy(Buffer,Argument.Direction)}.
     *
     * @param a
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgumentNoCopy(Buffer a, Argument.Direction d) {
        BufferArgument arg = device.getArgument(a);
        setArgument(Sizeof.cl_mem, arg);

        noCopyBuffers.add(a);
        // make the execute dependent on this copy.
        addWriteEvent(device.getWriteEvent(a));
    }

    /**
     * Set an argument for this launch. The user is reponsible for copying the data to the device using
     * {@link Device#copy(Buffer,Pointer)}.
     *
     * @param a
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgumentNoCopy(Pointer a, Argument.Direction d) {
        PointerArgument arg = device.getArgument(a);
        setArgument(Sizeof.cl_mem, arg);

        noCopyPointers.add(a);
        // make the execute dependent on this copy.
        addWriteEvent(device.getWriteEvent(a));
    }

    /**
     * Set an argument for this launch. The user is reponsible for copying the data to the device using
     * {@link Device#copy(int[],Argument.Direction)}.
     *
     * @param a
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgumentNoCopy(int[] a, Argument.Direction d) {
        IntArrayArgument arg = device.getArgument(a);
        setArgument(Sizeof.cl_mem, arg);

        noCopyInts.add(a);
        // make the execute dependent on this copy.
        addWriteEvent(device.getWriteEvent(a));
    }

    /**
     * Set an argument for this launch. The user is reponsible for copying the data to the device using
     * {@link Device#copy(byte[],Argument.Direction)}.
     *
     * @param a
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgumentNoCopy(byte[] a, Argument.Direction d) {
        ByteArrayArgument arg = device.getArgument(a);
        setArgument(Sizeof.cl_mem, arg);

        noCopyBytes.add(a);
        // make the execute dependent on this copy.
        addWriteEvent(device.getWriteEvent(a));
    }

    /**
     * Get the name of the device.
     *
     * @return the name of the device.
     */
    public String getDeviceName() {
        return device.getName();
    }

    /*
     * Methods for the rest of the package
     */

    String getThread() {
        return threadName;
    }

    /*
     * Methods for subclasses
     */

    protected void registerExecuteEventToDevice(cl_event event) {
        for (float[] fs : noCopyFloats) {
            device.addExecuteEvent(fs, event);
        }
        for (double[] ds : noCopyDoubles) {
            device.addExecuteEvent(ds, event);
        }
        for (int[] is : noCopyInts) {
            device.addExecuteEvent(is, event);
        }
        for (byte[] bs : noCopyBytes) {
            device.addExecuteEvent(bs, event);
        }
        for (Buffer bs : noCopyBuffers) {
            device.addExecuteEvent(bs, event);
        }
        for (Pointer p : noCopyPointers) {
            device.addExecuteEvent(p, event);
        }
    }

    protected void registerWithThread() {
        // register this launch with the thread. The thread will
        // clean all launch up to NR_LAUNCHES_TO_RETAIN to make sure all
        // the execute events are gone.

        cleanLaunches(NR_LAUNCHES_TO_RETAIN);
        launches.get().offerLast(this);
    }

    protected void clean() {
        removeExecuteEventsFromDevice(executeEvents);
        clean("execute", executeEvents);
        device.cleanWriteEvents(writeBufferEvents);
        clean("writeBuffer", writeBufferEvents);
        clean("readBuffer", readBufferEvents);
        clearNoCopies();
    }

    protected void finish() {
        if (!finished) {
            if (executeEvents.size() != 0) {
                if (device.asynchReads()) {
                    scheduleReadsDirectBuffers();
                }

                waitForExecEvents();

                if (device.asynchReads()) {
                    cleanAsynchronousArguments();
                } else {
                    cleanArguments();
                }

                finished = true;
                device.setNotBusy();
            } else {
                throw new Error("launch not called yet");
            }
        }
    }

    protected abstract void setArgument(int size, Argument arg);

    /*
     * Methods with private access
     */

    private static void cleanLaunches(int nrLaunchesToRetain) {
        int nrLaunchesToClean = Math.max(launches.get().size() - nrLaunchesToRetain, 0);
        for (int i = 0; i < nrLaunchesToClean; i++) {
            Launch l = launches.get().pollFirst();
            l.clean();
        }
    }

    private void clearNoCopies() {
        noCopyFloats.clear();
        noCopyDoubles.clear();
        noCopyInts.clear();
        noCopyBytes.clear();
        noCopyBuffers.clear();
        noCopyPointers.clear();
    }

    private void removeExecuteEventsFromDevice(ArrayList<cl_event> executeEvents) {
        for (cl_event event : executeEvents) {
            removeExecuteEventFromDevice(event);
        }
    }

    private void removeExecuteEventFromDevice(cl_event event) {
        for (float[] fs : noCopyFloats) {
            device.removeExecuteEvent(fs, event);
        }
        for (double[] ds : noCopyDoubles) {
            device.removeExecuteEvent(ds, event);
        }
        for (int[] is : noCopyInts) {
            device.removeExecuteEvent(is, event);
        }
        for (byte[] bs : noCopyBytes) {
            device.removeExecuteEvent(bs, event);
        }
        for (Buffer bs : noCopyBuffers) {
            device.removeExecuteEvent(bs, event);
        }
        for (Pointer p : noCopyPointers) {
            device.removeExecuteEvent(p, event);
        }
    }

    private void clean(String type, ArrayList<cl_event> events) {
        for (cl_event event : events) {
            if (eventLogger.isDebugEnabled()) {
                eventLogger.debug("About to clean event {}", event);
                eventLogger.debug("Removing {} from Launch.{}Events", event, type);
                Event.showEvent(type, event);
            }
            Event.clean(event);
        }
        events.clear();
    }

    private void scheduleReadsDirectBuffers() {
        // Schedule reads
        // Problem is: apparently, as soon as reads are scheduled,
        // further writes are apparently delayed until after this
        // read is done. Ugly hack: just sleep a bit before
        // scheduling the read.
        //
        // Is this still a problem? Removed it.
        //
        // try {
        // Thread.sleep(50);
        // } catch(Throwable e) {
        // // ignore
        // }
        for (Argument a : argsToClean) {
            if (a instanceof BufferArgument) {
                BufferArgument b = (BufferArgument) a;
                if (b.isDirect()) {
                    a.scheduleReads(executeEvents, readBufferEvents, true);
                }
            }
        }
    }

    private cl_event[] waitForExecEvents() {
        cl_event[] exevnts = executeEvents.toArray(new cl_event[executeEvents.size()]);
        if (logger.isDebugEnabled()) {
            logger.debug("finish: events to wait for: " + Arrays.toString(exevnts));
        }
        if (eventLogger.isDebugEnabled()) {
            eventLogger.debug("Waiting for executes: {}", Arrays.toString(exevnts));
        }
        clWaitForEvents(exevnts.length, exevnts);
        if (logger.isDebugEnabled()) {
            logger.debug("finish: waiting for events done");
        }
        return exevnts;
    }

    private void addExecuteEventToTimer(cl_event event) {
        long start = Cashmere.getValue(event, CL_PROFILING_COMMAND_START);
        long end = Cashmere.getValue(event, CL_PROFILING_COMMAND_END);
        if (start != 0 && end > start) {
            // Sometimes, end == 0 or start == 0. Don't know why.
            double time = (end - start) / 1e9;
            Cashmere.addTimeForKernel(name, device, time);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("finish: timer stuff done");
        }
    }

    private void cleanAsynchronousArguments() {
        cl_event[] readBufferEventsArray = readBufferEvents.toArray(new cl_event[readBufferEvents.size()]);

        if (readBufferEventsArray.length > 0) {
            if (logger.isDebugEnabled()) {
                logger.debug("finish: read buffer events to wait for: " + Arrays.toString(readBufferEventsArray));
            }
            clWaitForEvents(readBufferEventsArray.length, readBufferEventsArray);
        }
        for (Argument a : argsToClean) {
            if (a.readScheduled()) {
                a.clean();
            }
        }
    }

    private void cleanArguments() {
        for (Argument a : argsToClean) {
            if (!a.readScheduled()) {
                a.scheduleReads(null, readBufferEvents, false);
            }
            a.clean();
        }
    }

    private void addWriteEvent(cl_event event) {
        if (event != null) {
            if (eventLogger.isDebugEnabled()) {
                eventLogger.debug("Storing {} in Launch.writeBufferEvents", event);
            }
            writeBufferEvents.add(event);
        }
    }
}
