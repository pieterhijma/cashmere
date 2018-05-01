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
 * A general Cashmere exception.
 */
public class CashmereException extends Exception {

    private static final long serialVersionUID = 8847994110046436104L;

    /**
     * Creates a new <code>CashmereException</code> exception with the specified message.
     *
     * @param message
     *            the message for this exception
     */
    public CashmereException(String message) {
        super(message);
    }
}
