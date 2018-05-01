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

/**
 * A <code>Kernel</code> represents an MCL kernel. From this kernel one can create more than one {@link KernelLaunch} instances
 * with which the kernel can be launched.
 */
public class Kernel extends ManyCoreUnit {

    // A kernel cannot be created from outside the package.  A kernel can be obtained using Cashmere.getKernel().
    Kernel(String name, String threadName, Device device) {
        super(name, threadName, device);

    }

    /**
     * Create a <code>KernelLaunch</code> for this <code>Kernel</code>.
     *
     * @return a new {@link KernelLaunch}
     */
    @Override
    public KernelLaunch createLaunch() {
        return createLaunch(threadName);
    }

    @Override
    /**
     * Create a <code>KernelLaunch</code> for this <code>Kernel</code> with a specific name for a thread.
     *
     * @param threadName
     *            the name for the thread
     * @return a new {@link KernelLaunch}
     */
    public KernelLaunch createLaunch(String threadName) {
        KernelLaunch kernelLaunch = new KernelLaunch(name, threadName, device);
        return kernelLaunch;
    }
}
