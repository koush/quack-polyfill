package com.koushikdutta.quack.polyfill.dgram

import com.koushikdutta.quack.JavaScriptObject
import com.koushikdutta.quack.polyfill.*
import com.koushikdutta.quack.polyfill.net.getInetAddressFamily
import com.koushikdutta.quack.polyfill.require.Modules
import com.koushikdutta.scratch.async.AsyncHandler
import com.koushikdutta.scratch.async.async
import com.koushikdutta.scratch.buffers.ByteBuffer
import com.koushikdutta.scratch.buffers.ByteBufferList
import com.koushikdutta.scratch.event.*

open class UdpAddress(val port: Int, inetAddress: InetAddress) {
    val address: String = inetAddress.toString().replace("/", "")
    val family: String? = getInetAddressFamily(inetAddress)
}
class UdpMessage(port: Int, inetAddress: InetAddress, val size: Int) : UdpAddress(port, inetAddress)

interface Udp {
    fun connect(port: Int, vararg arguments: Any?)
    fun close(runnable: Runnable?)
    fun bind(vararg arguments: Any?)
    fun setBroadcast(flag: Boolean)
    fun send(vararg arguments: Any?)
    fun address(): UdpAddress?
    fun unref(): Unit
}

class CreateDgramOptions {
    var type: String? = null
    var reuseAddr: Boolean = false
    var ipv6Only: Boolean = false
    var recvBufferSize: Int? = null
    var sendBufferSize: Int? = null
}


class UdpImpl internal constructor(val dgramModule: DgramModule, val quackLoop: QuackEventLoop, val bufferClass: JavaScriptObject, val emitter: EventEmitter, internal val options: CreateDgramOptions) : Udp {
    var dgram: AsyncDatagramSocket? = null
    val family = options.type!!
    val handler = AsyncHandler(quackLoop.netLoop)

    override fun connect(port: Int, vararg arguments: Any?) {
        val argParser = ArgParser(quackLoop.quack, *arguments)
        val address: String = argParser.String() ?: "127.0.0.1"
        val cb = argParser.Function()

        handler.post {
            try {
                if (dgram == null)
                    ensureSocketInternal(null, null)

                if (cb != null)
                    emitter.once("connect", cb)
                dgram!!.connect(InetSocketAddress(address, port))
                emitter.postEmitSafely(quackLoop, "connect")
            }
            catch (e: Exception) {
                emitter.postCallbackErrorElseEmit(quackLoop, e, cb)
            }
        }
    }

    override fun close(runnable: Runnable?) {
        quackLoop.netLoop.async {
            try {
                dgram?.close()
                if (dgram != null) {
                    dgramModule.openSockets--
                    dgram = null
                }
            }
            catch (e: Exception) {
            }
            try {
                emitter.postEmitSafely(quackLoop, "close")
                quackLoop.loop.post()
                runnable?.run()
            }
            catch (e: Exception) {
            }
        }
    }

    var udpAddress: UdpAddress? = null
    private suspend fun ensureSocketInternal(address: String?, port: Int?) {
        try {
            if (dgram != null)
                throw IllegalArgumentException("bind already called on udp")

            val bindAddress: String?
            if (address == null) {
                if (family == "udp4") {
                    bindAddress = "0.0.0.0";
                }
                else if (family == "udp6") {
                    bindAddress = "::";
                }
                else {
                    bindAddress = null
                }
            }
            else {
                bindAddress = address
            }

            val bindPort= port ?: 0

            val addr: InetAddress?
            if (bindAddress != null)
                addr = quackLoop.netLoop.getByName(bindAddress)
            else
                addr = null

            dgram = quackLoop.netLoop.createDatagram(bindPort, addr, options.reuseAddr)
            dgramModule.openSockets++
            udpAddress = UdpAddress(dgram!!.localPort, dgram!!.localAddress)
            if (broadcast != null)
                dgram!!.broadcast = broadcast!!

            emitter.emit("listening")

            messageLoop()
        }
        catch (e: Exception) {
            emitter.postEmitError(quackLoop, e)
        }
    }

    private fun messageLoop() = quackLoop.netLoop.async {
        val buffer = ByteBufferList()
        try {
            while (true) {
                val addr = dgram!!.receivePacket(buffer)
                val b = buffer.readDirectByteBuffer()
                val rinfo = UdpMessage(addr.port, addr.address, b.remaining())
                quackLoop.loop.post()
                emitter.postEmitSafely(quackLoop, "message", bufferClass.callProperty("from", b), jsonCoerce(UdpMessage::class.java, rinfo))
            }
        }
        catch (exception: Exception) {
            emitter.postEmitError(quackLoop, exception)
        }
    }

    private fun ensureSocket(address: String?, port: Int?) = handler.post {
        ensureSocketInternal(address, port)
    }

    override fun bind(vararg arguments: Any?) {
        val argParser = ArgParser(quackLoop.quack, *arguments)

        val options: JavaScriptObject? = argParser("object")
        if (options != null) {
            val cb: JavaScriptObject? = argParser("function")
            val port = options.get("port") as Number?
            val address = options.get("address") as String?
            if (cb != null)
                emitter.once("listening", cb)

            this.ensureSocket(address, port?.toInt())
        }
        else {
            val port = argParser.Int()
            val address = argParser.String()
            val cb: JavaScriptObject? = argParser("function")
            if (cb != null)
                emitter.once("listening", cb)

            this.ensureSocket(address, port)
        }
    }

    private var broadcast: Boolean? = null
    override fun setBroadcast(flag: Boolean) {
        broadcast = flag
        handler.post {
            if (dgram != null)
                dgram!!.broadcast = flag
        }
    }

    override fun send(vararg arguments: Any?) {
        val argParser = ArgParser(quackLoop.quack, *arguments)
        val buffer = argParser.Object()!!.get("buffer") as ByteBuffer
        val n1 = argParser.Int()

        val offset: Int?
        val length: Int?
        val port: Int?
        val address: String?
        if (n1 != null) {
            val n2 = argParser.Int()
            if (n2 != null) {
                offset = n1
                length = n2
                port = argParser.Int()
                address = argParser.String()
            }
            else {
                offset = null
                length = null
                port = n1
                address = argParser.String()
            }
        }
        else {
            offset = null
            length = null
            port = null
            address = argParser.String()
        }

        if (offset != null) {
            buffer.position(offset.toInt())
            buffer.limit(buffer.position() + length!!.toInt())
        }

        if ((port == null && address != null) || (port != null && address == null))
            throw IllegalArgumentException("expected both port and address on udp send")

        val cb = argParser.Function()

        handler.post {
            try {
                val b = ByteBufferList(buffer)

                if (dgram == null)
                    ensureSocketInternal(null, null)

                if (port == null && address == null)
                    dgram!!.write(b)
                else
                    dgram!!.sendPacket(InetSocketAddress(address!!, port!!.toInt()), b)

                cb?.postCallSafely(quackLoop)
            }
            catch (exception: Exception) {
                emitter.postCallbackErrorElseEmit(quackLoop, exception, cb)
            }
        }
    }

    override fun address(): UdpAddress? {
        return udpAddress
    }

    override fun unref() {
    }

}

class DgramModule(val quackLoop: QuackEventLoop, modules: Modules) {
    val udpClass: JavaScriptObject
    val bufferClass: JavaScriptObject
    var openSockets = 0

    init {
        quackLoop.quack.putJavaToJsonCoersion(UdpAddress::class.java)
        quackLoop.quack.putJavaToJsonCoersion(UdpMessage::class.java)

        val eventEmitterClass = modules.require("events")
        bufferClass = modules.require("buffer").get("Buffer") as JavaScriptObject

        udpClass = mixinExtend(quackLoop.quack, eventEmitterClass, EventEmitter::class.java, Udp::class.java, "Udp") { emitter, arguments ->
            UdpImpl(this, quackLoop, bufferClass, emitter, quackLoop.quack.coerceJavaScriptToJava(CreateDgramOptions::class.java, arguments[0]) as CreateDgramOptions)
        }
    }

    private fun createSocket(options: CreateDgramOptions, cb: JavaScriptObject?): JavaScriptObject {
        val ret = udpClass.construct(options)
        if (cb != null)
            ret.callProperty("on", "message", cb)
        return ret
    }

    fun createSocket(type: String, cb: JavaScriptObject?): JavaScriptObject {
        val options = CreateDgramOptions()
        options.type = type
        return createSocket(options, cb)
    }

    fun createSocket(options: JavaScriptObject, cb: JavaScriptObject?): JavaScriptObject {
        return createSocket(options.jsonCoerce(CreateDgramOptions::class.java), cb)
    }
}