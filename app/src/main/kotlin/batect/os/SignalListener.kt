/*
   Copyright 2017-2019 Charles Korn.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package batect.os

import jnr.constants.platform.Signal
import jnr.posix.POSIX

class SignalListener(private val posix: POSIX) {
    private val handlers = mutableMapOf<Int, MutableList<SignalHandler>>()

    fun start(signal: Signal, handler: SignalHandler): AutoCloseable {
        val handlersForSignal = handlers.getOrPut(signal.value(), { mutableListOf() })
        handlersForSignal.add(handler)

        val originalHandler = posix.signal(signal, ::handleSignal)

        return AutoCloseable {
            posix.signal(signal, originalHandler)
            handlers.getValue(signal.value()).remove(handler)
        }
    }

    private fun handleSignal(signal: Int) {
        handlers.getValue(signal).last().invoke()
    }
}

typealias SignalHandler = () -> Unit
