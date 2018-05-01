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
import java.util.Collections;
import java.util.List;

abstract class ManyCoreUnit {

    protected Device device;

    protected String name;
    protected String threadName;

    private List<Launch> launches;

    // a ManyCoreUnit can only be created from a subclass
    protected ManyCoreUnit(String name, String threadName, Device device) {
        this.launches = Collections.synchronizedList(new ArrayList<Launch>());
        this.name = name;
        this.device = device;
        this.threadName = threadName;
    }

    /**
     * Get the <code>Device</code> associated with this instance.
     *
     * @return the {@link Device}
     */
    public Device getDevice() {
        return device;
    }

    /**
     * Get the name of this instance.
     *
     * @return the name of this instance.
     */
    public String getName() {
        return name;
    }

    /**
     * Get the name of the thread.
     *
     * @return the name of the thread
     */
    public String getThread() {
        return threadName;
    }

    public abstract Launch createLaunch();

    public abstract Launch createLaunch(String t);
}
