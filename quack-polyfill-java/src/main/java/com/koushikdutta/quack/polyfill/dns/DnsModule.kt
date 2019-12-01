package com.koushikdutta.quack.polyfill.dns

import com.koushikdutta.quack.JavaScriptObject
import com.koushikdutta.quack.polyfill.*
import com.koushikdutta.quack.polyfill.ArgParser
import com.koushikdutta.quack.polyfill.jsonCoerce
import com.koushikdutta.quack.polyfill.require.Modules
import com.koushikdutta.scratch.async
import com.koushikdutta.scratch.event.Inet4Address
import com.koushikdutta.scratch.event.Inet6Address
import com.koushikdutta.scratch.event.getByName

class LookupOptions {
    var family: Int = 0
    var hints: Int = 0
    var all: Boolean = false
    var verbatim: Boolean = true
}

class DnsModule(val quackLoop: QuackEventLoop, val modules: Modules) {
    fun lookup(hostname: String, vararg arguments: Any?) {
        val parser = ArgParser(quackLoop.quack, *arguments)

        val options = parser.Object()?.jsonCoerce(LookupOptions::class.java) ?: LookupOptions()
        val callback = parser.Function()!!

        quackLoop.loop.async {
            try {
                if (options.all) {
                    val addresses = quackLoop.quack.evaluateForJavaScriptObject("[]")
                    getAllByName(hostname).forEach { addresses.callProperty("push", it.hostAddress) }
                    callback.callSafely(quackLoop, null, addresses, 0)
                }
                else {
                    val addr = getByName(hostname)
                    val family: Int
                    if (addr is Inet4Address)
                        family = 4
                    else if (addr is Inet6Address)
                        family = 6
                    else
                        family = 0
                    callback.callSafely(quackLoop, null, addr.hostAddress, family)
                }
            }
            catch (throwable: Throwable) {
                callback.callSafely(quackLoop, quackLoop.quack.newError(throwable))
            }
        }
    }
}