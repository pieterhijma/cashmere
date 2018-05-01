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

/**
 * Cashmere is a library on top of <a href="https://github.com/NLeSC/Constellation">Constellation</a> that schedules
 * <a href="https://github.com/JungleComputing/mcl">Many-Core Levels (MCL)</a> kernels efficiently.
 *
 * While Constellation is responsible for scheduling <code>Activities</code> onto nodes in the cluster, Cashmere operates on the
 * level of one node. It is responsible for scheduling many-core kernels efficiently to many-core devices in the node, such as
 * GPUs. The main class for a Cashmere program is {@link ibis.cashmere.constellation.Cashmere} that only has static methods. For a
 * typical setup of Cashmere we refer to the {@link ibis.cashmere.constellation.Cashmere} class.
 * 
 * @see ibis.cashmere.constellation.Cashmere
 * @see <a href="https://github.com/NLeSC/Constellation">Constellation</a>
 * @see <a href="https://github.com/JungleComputing/mcl">Many-Core Levels (MCL)</a>
 */
package ibis.cashmere.constellation;
