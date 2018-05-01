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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * A wrapper of a {@link ByteBuffer}. The benefit of this <code>Buffer</code> against <code>ByteBuffers</code> is that it can be
 * used in HashMaps. The rest of the Cashmere library assumes the use of <code>Buffer</code> instead of {@link ByteBuffer}. Many
 * of the methods are directly applied on the underlying {@link ByteBuffer}.
 */
public class Buffer {

    ByteBuffer byteBuffer;

    /**
     * Creates a new <code>Buffer</code> with a direct {@link ByteBuffer}.
     *
     * @param size
     *            the size of the <code>Buffer</code> in bytes
     */
    public Buffer(int size) {
        byteBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
    }

    /**
     * Creates a new <code>Buffer</code> from a {@link ByteBuffer}.
     *
     * @param byteBuffer
     *            a {@link ByteBuffer}
     */
    public Buffer(ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
        this.byteBuffer.position(0);
    }

    /**
     * Returns this buffer's <code>capacity</code>.
     *
     * @return the capacity of this buffer in bytes
     */
    public int capacity() {
        return byteBuffer.capacity();
    }

    /**
     * Returns whether the buffer is direct.
     *
     * @return true if and only if this buffer is direct
     */
    public boolean isDirect() {
        return byteBuffer.isDirect();
    }

    /**
     * Sets this buffer's position. If the mark is defined and larger than the new position then it is discarded.
     *
     * @param newPosition
     *            The new position value; must be non-negative and no larger than the current limit an <code>int</code> value
     * @return this buffer
     * @throws IllegalArgumentException
     *             if the preconditions on newPosition do not hold
     */
    public Buffer position(int newPosition) {
        byteBuffer.position(newPosition);
        return this;
    }

    /**
     * Convenience method to get a float on position <code>position</code>.
     *
     * @param position
     *            the position in terms of the number of floats in the <code>Buffer</code>
     * @return the floating point value on position <code>position</code>
     */
    public float getFloat(int position) {
        return byteBuffer.getFloat(position * 4);
    }

    /**
     * Return the underlying {@link ByteBuffer}.
     *
     * @return this buffer's {@link ByteBuffer}
     */
    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }

    /**
     * Creates a view of this byte buffer as a float buffer.
     *
     * @return a new <code>FloatBuffer</code>
     */
    public FloatBuffer asFloatBuffer() {
        return byteBuffer.asFloatBuffer();
    }
}
