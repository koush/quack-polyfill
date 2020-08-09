package com.koushikdutta.quack.polyfill.os

import com.koushikdutta.quack.JavaScriptObject
import com.koushikdutta.quack.polyfill.QuackEventLoop
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.util.*

class OSModule(val quackLoop: QuackEventLoop) {
    var tmpdir = System.getProperty("java.io.tmpdir")
    fun tmpdir(): String {
        return tmpdir
    }

    fun networkInterfaces(): JavaScriptObject {
        val ret = quackLoop.quack.evaluateForJavaScriptObject("({})")
        for (ni in NetworkInterface.getNetworkInterfaces()) {
            val name = ni.name
            var array = ret[name] as JavaScriptObject?
            if (array == null) {
                array = quackLoop.quack.evaluateForJavaScriptObject("([])")
                ret[name] = array
            }
            for (addr in ni.interfaceAddresses) {
                val nia = quackLoop.quack.evaluateForJavaScriptObject("({})")
                nia["address"] = addr.address.hostAddress
                nia["internal"] = ni.isLoopback || ni.isVirtual
                val bitset = BitSet()
                val networkPrefixLength = addr.networkPrefixLength
                val bytes =
                if (addr.address.address.size == 4) {
                    nia["family"] = "IPv4"
                    for (i in 0 until 32) {
                        bitset[i] = i < networkPrefixLength
                    }
                    ByteBuffer.allocate(4).put(bitset.toByteArray()).array()
                }
                else {
                    nia["family"] = "IPv6"
                    for (i in 0 until 128) {
                        bitset[i] = i < networkPrefixLength
                    }
                    ByteBuffer.allocate(16).put(bitset.toByteArray()).array()
                }
                nia["netmask"] = InetAddress.getByAddress(bytes).hostAddress
                array!!.callProperty("push", nia)
            }
        }
        return ret
    }
}
