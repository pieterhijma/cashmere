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

import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_event;

class FloatArray2DArgument extends FloatArrayArgument {

    private float[][] fs2D;

    FloatArray2DArgument(cl_context context, cl_command_queue writeQueue, cl_command_queue readQueue,
            ArrayList<cl_event> writeBufferEvents, float[][] fs, Direction d) {
        super(context, writeQueue, readQueue, writeBufferEvents, new float[fs.length * fs[0].length], d);
        this.fs2D = fs;
        transform();
    }

    @Override
    protected void transform() {
        flatten();
    }

    @Override
    protected void transformBack() {
        unFlatten();
    }

    private void flatten() {
        for (int i = 0; i < fs2D.length; i++) {
            for (int j = 0; j < fs2D[0].length; j++) {
                fs[i * fs2D[0].length + j] = fs2D[i][j];
            }
        }
    }

    private void unFlatten() {
        for (int i = 0; i < fs2D.length; i++) {
            for (int j = 0; j < fs2D[0].length; j++) {
                fs2D[i][j] = fs[i * fs2D[0].length + j];
            }
        }
    }

    @Override
    void scheduleReads(ArrayList<cl_event> waitListEvents, ArrayList<cl_event> readBufferEvents, boolean async) {
        super.scheduleReads(waitListEvents, readBufferEvents, async);
        fs2D = null;
    }
}
