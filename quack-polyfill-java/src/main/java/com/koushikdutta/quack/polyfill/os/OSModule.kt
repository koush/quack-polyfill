package com.koushikdutta.quack.polyfill.os

import com.koushikdutta.quack.QuackJsonObject
import com.koushikdutta.quack.polyfill.QuackEventLoop
import com.koushikdutta.quack.polyfill.gson
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.util.*
import kotlin.collections.ArrayList

class OSModule(val quackLoop: QuackEventLoop) {
    var tmpdir = System.getProperty("java.io.tmpdir")
    fun tmpdir(): String {
        return tmpdir!!
    }

    fun networkInterfaces(): QuackJsonObject {
        val ret: MutableMap<String, Any>  = mutableMapOf()
        for (ni in NetworkInterface.getNetworkInterfaces()) {
            val name = ni.name
            var array = ret[name] as ArrayList<Any>?
            if (array == null) {
                array = ArrayList()
                ret[name] = array
            }
            for (addr in ni.interfaceAddresses) {
                val nia: MutableMap<String, Any>  = mutableMapOf()
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
                array.add(nia)
            }
        }
        return QuackJsonObject(gson.toJson(ret))
    }
}
