package com.koushikdutta.quack.polyfill.dns

import com.koushikdutta.quack.QuackJsonObject
import com.koushikdutta.quack.polyfill.*
import com.koushikdutta.quack.polyfill.require.Modules
import com.koushikdutta.scratch.async.async
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
                    val addresses = ArrayList<Any>()
                    getAllByName(hostname).forEach { addresses.add(it.hostAddress) }
                    callback.postCallSafely(quackLoop, null, QuackJsonObject(gson.toJson(addresses)), 0)
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
                    callback.postCallSafely(quackLoop, null, addr.hostAddress, family)
                }
            }
            catch (throwable: Throwable) {
                callback.postCallSafely(quackLoop, quackLoop.quack.newError(throwable))
            }
        }
    }
}