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

import ibis.constellation.util.ByteBufferCache;

/**
 * A wrapper for {@link ByteBufferCache}.
 */
public class BufferCache {

    /**
     * Make the specified <code>Buffer</code> available for use, that is, append it to the list of available buffers.
     *
     * @param b
     *            a <code>Buffer</code> value
     */
    public static void makeAvailableBuffer(Buffer b) {
        ByteBufferCache.makeAvailableByteBuffer(b.byteBuffer);
    }

    /**
     * Obtains a <code>Buffer</code> of the specified size. If one cannot be found in the cache, a new one is allocated. If it
     * needs to be clear(ed), the needsClearing flag should be set to true.
     *
     * @param sizeBuffer
     *            the size of the <code>Buffer</code> to be obtained
     * @param needsClearing
     *            whether the <code>Buffer</code> needs to be cleared
     * @return the obtained <code>Buffer</code>
     */
    public static Buffer getBuffer(int sizeBuffer, boolean needsClearing) {
        return new Buffer(ByteBufferCache.getByteBuffer(sizeBuffer, needsClearing));
    }

    /**
     * Initializes the buffer cache with the specified number of buffers of the specified size.
     *
     * @param sizeBuffer
     *            the size of the buffers
     * @param nrBuffers
     *            the number of buffers that need to be initialized
     */
    public static void initializeBuffers(int sizeBuffer, int nrBuffers) {
        ByteBufferCache.initializeByteBuffers(sizeBuffer, nrBuffers);
    }
}
