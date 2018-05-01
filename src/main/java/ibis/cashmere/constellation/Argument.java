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

import static ibis.constellation.util.MemorySizes.toStringBytes;
import static org.jocl.CL.CL_FALSE;
import static org.jocl.CL.CL_MEM_READ_WRITE;
import static org.jocl.CL.CL_TRUE;
import static org.jocl.CL.clCreateBuffer;
import static org.jocl.CL.clEnqueueReadBuffer;
import static org.jocl.CL.clEnqueueWriteBuffer;
import static org.jocl.CL.clReleaseMemObject;
import static org.jocl.CL.clWaitForEvents;

import java.util.ArrayList;
import java.util.Arrays;

import org.jocl.Pointer;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_event;
import org.jocl.cl_mem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ibis.util.ThreadPool;

/**
 * A class used for indicating directions of arguments to kernels.
 */
public class Argument {

    /*
     * Public members
     *
     * This is the only thing that is public.  All other methods and members are package or protected.
     */

    /**
     * The <code>Direction</code> enumeration contains constants for arguments.
     */
    public static enum Direction {
        /**
         * The argument is used for input and is only read by the kernel.
         */
        IN,
        /**
         * The argument is used for output and is only written by the kernel.
         */
        OUT,
        /**
         * The argument is used for input and for output, it read and written by the kernel.
         */
        INOUT,
    };

    /*
     * members for subclasses
     */
    protected Pointer pointer;
    protected Direction direction;
    protected cl_mem memObject;
    private boolean readScheduled;

    /*
     * private members
     */
    private static final Logger logger = LoggerFactory.getLogger("ibis.cashmere.constellation");

    private static final Logger eventLogger = Event.logger;

    private static final Logger memLogger = LoggerFactory.getLogger("ibis.cashmere.constellation.Argument/memory");

    private static cl_event null_event = new cl_event(); // to test events against.

    // keeping track of the amount allocated
    private static long allocatedBytes = 0;
    private long size;

    /*
     * Constructors
     */

    // The constructor should only be called from subclasses
    protected Argument(Direction d) {
        this.pointer = null;
        this.direction = d;
    }

    protected Argument(Pointer p, Direction d, boolean allocated) {
        this.pointer = p;
        this.direction = d;
    }

    /*
     * Package methods
     */

    void scheduleReads(ArrayList<cl_event> a, ArrayList<cl_event> b, boolean async) {
    }

    void clean() {
        if (memObject != null) {
            if (logger.isInfoEnabled()) {
                logger.info("Releasing " + memObject);
            }
            if (memLogger.isDebugEnabled()) {
                memLogger.debug("about to release");
            }
            clReleaseMemObject(memObject);
            if (memLogger.isDebugEnabled()) {
                memLogger.debug("released");
            }
            synchronized (Argument.class) {
                allocatedBytes -= size;
            }
            if (memLogger.isDebugEnabled()) {
                memLogger.debug(String.format("deallocated: %4s, total: %s", toStringBytes(size), toStringBytes(allocatedBytes)));
            }
            memObject = null;
        }
    }

    Pointer getPointer() {
        return pointer;
    }

    void createBuffer(cl_context context, long size, Pointer hostPtr) {
        // long flags = direction == Direction.IN ? CL_MEM_READ_ONLY
        // : direction == Direction.INOUT ? CL_MEM_READ_WRITE
        // : CL_MEM_WRITE_ONLY;
        // TODO: change the API: There is a mismatch between our APIs.
        // Argument.Direction.IN/OUT in our API is about copying before/after,
        // while READ/WRITE is about whether the buffers are read/written,
        // which are two separate things. Quick fix for now: make everything
        // READ_WRITE.
        long flags = CL_MEM_READ_WRITE;// | CL_MEM_HOST_NO_ACCESS;

        Device device = Device.getDevice(context);
        memObject = device.withAllocationError(() -> clCreateBuffer(context, flags, size, null, null));
        this.size = size;
        synchronized (Argument.class) {
            allocatedBytes += size;
        }
        if (memLogger.isDebugEnabled()) {
            memLogger.debug(String.format("allocated: %6s, total: %s", toStringBytes(size), toStringBytes(allocatedBytes)));
        }

        if (memObject == null) {
            throw new Error("Could not allocate device memory");
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Done allocating memory of size " + size + ", result = " + memObject);
        }
        pointer = Pointer.to(memObject);
    }

    cl_event writeBufferNoCreateBuffer(cl_context context, cl_command_queue q, final cl_event[] waitEvents, long size,
            Pointer hostPtr) {

        cl_event event = new cl_event();
        int nEvents = 0;
        if (waitEvents != null) {
            nEvents = waitEvents.length;
        }
        if (nEvents > 0) {
            if (logger.isDebugEnabled()) {
                logger.debug("WriteBuffer: events to wait for: " + Arrays.toString(waitEvents));
            }
            if (logger.isTraceEnabled()) {
                ThreadPool.createNew(new Thread() {
                    @Override
                    public void run() {
                        clWaitForEvents(waitEvents.length, waitEvents);
                        logger.trace("Test wait successful: " + Arrays.toString(waitEvents));
                    }
                }, "test event waiter");
            }
        }

        Device device = Device.getDevice(context);

        Event.retainEvents(waitEvents);

        final int nEventsFinal = nEvents;
        device.withAllocationError(() -> clEnqueueWriteBuffer(q, memObject, CL_FALSE, 0, size, hostPtr, nEventsFinal,
                (nEventsFinal == 0) ? null : waitEvents, event));
        if (eventLogger.isDebugEnabled()) {
            Event.nrEvents.incrementAndGet();
            eventLogger.debug("performing a writeBuffer with new event: {}, depends on {} (retained)", event, waitEvents);
        }

        if (logger.isInfoEnabled()) {
            logger.info("Done enqueue write event " + event);
        }
        // Event.showEvents(waitEvents);
        // Event.showEvent(event);
        return event;
    }

    boolean readScheduled() {
        return readScheduled;
    }

    /*
     * Methods for subclasses
     */

    protected cl_event writeBuffer(cl_context context, cl_command_queue q, long size, Pointer hostPtr) {
        createBuffer(context, size, hostPtr);

        return writeBufferNoCreateBuffer(context, q, null, size, hostPtr);
    }

    protected cl_event readBuffer(cl_context context, cl_command_queue q, ArrayList<cl_event> waitEvents, long size,
            Pointer hostPtr, boolean asynch) {

        if (eventLogger.isDebugEnabled()) {
            eventLogger.debug("Doing a readbuffer");
        }

        readScheduled = true;
        cl_event event = new cl_event();
        cl_event[] events = null;
        int nEvents = 0;
        if (waitEvents != null) {
            nEvents = waitEvents.size();
            events = new cl_event[nEvents];
            events = waitEvents.toArray(events);
            Event.retainEvents(events);
            if (logger.isDebugEnabled()) {
                logger.debug("readBuffer: events to wait for: " + Arrays.toString(events));
            }
        }

        Device device = Device.getDevice(context);
        final int nEventsFinal = nEvents;
        final cl_event[] eventsFinal = events;
        device.withAllocationError(() -> clEnqueueReadBuffer(q, memObject, asynch ? CL_FALSE : CL_TRUE, 0, size, hostPtr,
                nEventsFinal, (nEventsFinal == 0) ? null : eventsFinal, event));
        if (eventLogger.isDebugEnabled()) {
            Event.nrEvents.incrementAndGet();
            eventLogger.debug("performing a readBuffer with new event: {}, depends on {} (retained)", event, eventsFinal);
        }
        if (event.equals(null_event)) {
            // No initialized event returned.
            return null;
        }
        // Event.showEvents(waitEvents);
        // Event.showEvent(event);
        return event;
    }
}
