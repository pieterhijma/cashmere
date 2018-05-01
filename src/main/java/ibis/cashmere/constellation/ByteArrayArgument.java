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

import java.util.ArrayList;

import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_event;

class ByteArrayArgument extends ArrayArgument {

    private byte[] bs;

    ByteArrayArgument(cl_context context, cl_command_queue writeQueue, cl_command_queue readQueue,
            ArrayList<cl_event> writeBufferEvents, byte[] bs, Direction d) {
        super(d, context, readQueue);

        this.bs = bs;
        transform();
        Pointer bsPointer = Pointer.to(bs);

        if (d == Direction.IN || d == Direction.INOUT) {
            cl_event event = writeBuffer(context, writeQueue, bs.length * Sizeof.cl_char, bsPointer);
            writeBufferEvents.add(event);
        } else {
            createBuffer(context, bs.length * Sizeof.cl_char, bsPointer);
        }
    }

    @Override
    void scheduleReads(ArrayList<cl_event> waitListEvents, ArrayList<cl_event> readBufferEvents, boolean async) {

        if (direction == Direction.OUT || direction == Direction.INOUT) {
            cl_event event = readBuffer(context, readQueue, waitListEvents, bs.length * Sizeof.cl_char, Pointer.to(bs), async);
            if (event != null) {
                readBufferEvents.add(event);
            }
        }
    }

    @Override
    void clean() {
        super.clean();
        bs = null;
    }
}
