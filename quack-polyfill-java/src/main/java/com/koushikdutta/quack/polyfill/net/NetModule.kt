package com.koushikdutta.quack.polyfill.net

import com.koushikdutta.quack.JavaScriptObject
import com.koushikdutta.quack.QuackProperty
import com.koushikdutta.quack.polyfill.*
import com.koushikdutta.quack.polyfill.require.Modules
import com.koushikdutta.scratch.*
import com.koushikdutta.scratch.async.async
import com.koushikdutta.scratch.event.*


open class CreateSocketOptions {
}

open class CreateServerOptions {
}

open class ConnectSocketOptions: CreateSocketOptions() {
    var port: Int? = null
    var host: String = "localhost"
}

open class SocketAddress(val port: Int, inetAddress: InetAddress) {
    val address: String = inetAddress.hostAddress
    val family: String? = getInetAddressFamily(inetAddress)
}

interface Socket : Duplex {
    fun address(): SocketAddress?

    @get:QuackProperty(name = "bufferSize")
    val bufferSize: Int
    @get:QuackProperty(name = "bytesRead")
    val bytesRead: Int
    @get:QuackProperty(name = "bytesWritten")
    val bytesWritten: Int

    fun connect(vararg arguments: Any?)

    @get:QuackProperty(name = "connecting")
    val connecting: Boolean
    @get:QuackProperty(name = "localAddress")
    val localAddress: String?
    @get:QuackProperty(name = "localPort")
    val localPort: Int?
    @get:QuackProperty(name = "pending")
    val pending: Boolean
    @get:QuackProperty(name = "remoteAddress")
    val remoteAddress: String?
    @get:QuackProperty(name = "remotePort")
    val remotePort: Int?
    @get:QuackProperty(name = "remoteFamily")
    val remoteFamily: String?

    fun setTimeout(timeout: Int, callback: JavaScriptObject?)
    fun setNoDelay(noDelay: Boolean)
    fun setKeepAlive(enable: Boolean, initialDelay: Int)
}

interface Server {
    fun close(cb: JavaScriptObject?)
    fun address(): SocketAddress?

    @get:QuackProperty(name = "listening")
    val listening: Boolean

    @get:QuackProperty(name = "maxConnections")
    @set:QuackProperty(name = "maxConnections")
    var maxConnections: Int?

    fun listen(vararg arguments: Any?)

    fun ref()
    fun unref()
}

internal fun getInetAddressFamily(inetAddress: InetAddress?): String? {
    if (inetAddress == null)
        return null
    if (inetAddress is Inet4Address)
        return "IPv4"
    if (inetAddress is Inet6Address)
        return "IPv6"
    return null
}

class ServerListenOptions(val port: Int?, val host: String?)

class ServerImpl(val netModule: NetModule, val quackLoop: QuackEventLoop, val emitter: EventEmitter, options: CreateServerOptions?): Server {
    var server: AsyncNetworkServerSocket? = null

    override fun close(cb: JavaScriptObject?) {
        quackLoop.netLoop.async {
            server?.close()
            server = null
            cb?.postCallSafely(quackLoop)
        }
    }

    private fun listenInternal(options: ServerListenOptions, callback: JavaScriptObject?) {
        listenInternal(options.port, options.host, callback)
    }

    private fun listenInternal(port: Int?, host: String?, callback: JavaScriptObject?) {
        quackLoop.netLoop.async {
            try {
                if (server != null)
                    throw IllegalStateException("ERR_SERVER_ALREADY_LISTEN")
                server = listen(port ?: 0, InetAddress.getByName(host))
                if (callback != null)
                    emitter.on("listening", callback)
                emitter.postEmitSafely(quackLoop, "listening")

                server!!.acceptAsync {
                    quackLoop.loop.post()
                    val socket = netModule.socketClass.construct()
                    val mixin = socket.getMixin(SocketImpl::class.java)
                    mixin.socket = this
                    emitter.postEmitSafely(quackLoop, "connection", socket)
                }
            }
            catch (throwable: Throwable) {
                emitter.postEmitError(quackLoop, throwable)
            }
        }
    }

    override fun listen(vararg arguments: Any?) {
        val parser = ArgParser(quackLoop.quack, *arguments)
        val options = parser.Object()
        if (options != null) {
            listenInternal(quackLoop.quack.coerceJavaScriptToJava(ServerListenOptions::class.java, options) as ServerListenOptions, parser.Function())
            return
        }

        listenInternal(parser.Int(), parser.String(), parser.Function())
    }

    override fun address(): SocketAddress? {
        if (server == null)
            return null
        return SocketAddress(server!!.localPort, server!!.localAddress)
    }

    override val listening: Boolean
        get() = server != null
    override var maxConnections: Int? = null

    override fun ref() {
    }

    override fun unref() {
    }
}

open class SocketImpl(val netModule: NetModule, override val quackLoop: QuackEventLoop, override val stream: DuplexStream, val options: CreateSocketOptions?): BaseDuplex, Socket {
    var socket: AsyncNetworkSocket? = null
    var created = false

    override fun address(): SocketAddress? {
        if (socket == null)
            return null
        return SocketAddress(socket!!.localPort, socket!!.localAddress)
    }

    override var pauser: Yielder? = null
    override val bufferSize: Int
        get() = 0
    override val bytesRead: Int
        get() = 0
    override val bytesWritten: Int
        get() = 0

    override fun connect(vararg arguments: Any?) {
        val parser = ArgParser(quackLoop.quack, *arguments)
        val options = parser.Object()?.jsonCoerce(ConnectSocketOptions::class.java)
        if (options != null) {
            connectInternal(options, parser.Function())
            return
        }

        val port = parser.Int()!!
        connectInternal(port, parser.String(), parser.Function())
    }

    private fun connectInternal(options: ConnectSocketOptions, connectListener: JavaScriptObject?) {
        connect(options.port!!, options.host, connectListener)
    }

    open suspend fun connectInternal(connectHost: String, port: Int): AsyncNetworkSocket {
        val ret = quackLoop.netLoop.connect(connectHost, port)
        ret.socket.receiveBufferSize = 1024 * 1024
        return ret
    }

    private fun connectInternal(port: Int, host: String?, connectListener: JavaScriptObject?) {
        quackLoop.netLoop.async {
            if (created) {
                stream.postEmitError(quackLoop, IllegalStateException("socket already created"))
                return@async
            }
            created = true
            connecting = true
            val connectHost = host ?: "localhost"
            try {
                if (connectListener != null)
                    stream.once("connect", connectListener)
                socket = connectInternal(connectHost, port)
                netModule.openSockets++
                connecting = false
                pending = false
                stream.postEmitSafely(quackLoop, "connect")
                readYielder.resume()
                writeYielder.resume()
            }
            catch (throwable: Throwable) {
                connecting = false
                stream.postEmitError(quackLoop, throwable)
            }
        }
    }

    override var connecting: Boolean = false
        internal set
    override val localAddress: String?
        get() = socket?.localAddress.toString()
    override val localPort: Int?
        get() = socket?.localPort
    override var pending: Boolean = true
        internal set
    override val remoteAddress: String
        get() = socket?.remoteAddress.toString()
    override val remotePort: Int?
        get() = socket?.remoteAddress?.port
    override val remoteFamily: String?
        get() = getInetAddressFamily(socket?.remoteAddress?.address)

    override fun setTimeout(timeout: Int, callback: JavaScriptObject?) {
    }

    override fun setNoDelay(noDelay: Boolean) {
    }

    override fun setKeepAlive(enable: Boolean, initialDelay: Int) {
    }

    protected open suspend fun destroyInternal() {
        socket?.close()
        if (socket != null) {
            netModule.openSockets--
            socket = null
        }
    }

    override fun _destroy(err: Any?, callback: JavaScriptObject?) {
        quackLoop.netLoop.async {
            destroyInternal()
            callback?.postCallSafely(quackLoop)
        }
    }

    val readYielder = Yielder()
    override suspend fun getAsyncRead(): AsyncRead {
//        quackLoop.netLoop.await()
        // throttle reads to fill receive buffer.
        quackLoop.netLoop.sleep(50)

        if (socket == null)
            readYielder.yield()
        return socket!!
    }

    val writeYielder = Yielder()
    override suspend fun getAsyncWrite(): AsyncWrite {
        quackLoop.netLoop.await()
        // throttle probably doesnt work since back pressure
        // in node will also throttle
//        quackLoop.netLoop.sleep(50)

        if (socket == null)
            writeYielder.yield()
        return socket!!
    }

    override var finalCallback: JavaScriptObject? = null
}

open class NetModule(val quackLoop: QuackEventLoop, modules: Modules) {
    @get:QuackProperty(name = "Socket")
    val socketClass: JavaScriptObject

    @get:QuackProperty(name = "Server")
    val serverClass: JavaScriptObject

    val duplexClass: JavaScriptObject
    val ctx = quackLoop.quack
    var openSockets = 0

    init {
        ctx.putJavaToJavaScriptCoercion(SocketAddress::class.java) { _, o ->
            jsonCoerce(SocketAddress::class.java, o)
        }

        duplexClass = modules.require("stream").get("Duplex") as JavaScriptObject

        val eventEmitterClass = modules.require("events")

        socketClass = mixinExtend(ctx, duplexClass, DuplexStream::class.java, Socket::class.java, "Socket", {
            // pass the socket arguments up to the duplex constructor
            it[0]
        }) { stream, arguments ->
            val parser = ArgParser(quackLoop.quack, *arguments)
            val options = parser.Coerce(CreateSocketOptions::class.java)

            SocketImpl(this, quackLoop, stream, options)
        }

        serverClass = mixinExtend(ctx, eventEmitterClass, EventEmitter::class.java, Server::class.java, "Server") { eventEmitter, arguments ->
            val parser = ArgParser(quackLoop.quack, *arguments)
            val options = parser.Coerce(CreateServerOptions::class.java)
            val connectionListener = parser.Function()
            if (connectionListener != null)
                eventEmitter.on("connection", connectionListener)

            ServerImpl(this, quackLoop, eventEmitter, options)
        }
    }

    private fun newSocket(options: JavaScriptObject?): JavaScriptObject {
         return socketClass.construct(options)
    }

    fun connect(vararg arguments: Any?): JavaScriptObject {
        return createConnection(*arguments)
    }

    fun createConnection(vararg arguments: Any?): JavaScriptObject {
        val parser = ArgParser(quackLoop.quack, *arguments)
        val options = parser.Object()
        if (options != null) {
            val socket = newSocket(options)
            val mixin = socket.getMixin(SocketImpl::class.java)
            mixin.connect(options, parser.Function())
            return socket
        }

        val port = parser.Int()!!
        val host = parser.String()
        val socket = newSocket(null)
        val mixin = socket.getMixin(SocketImpl::class.java)
        mixin.connect(port, host, parser.Function())
        return socket
    }

    fun createServer(vararg arguments: Any?): JavaScriptObject {
        return serverClass.construct(*arguments)
    }

    fun isIP(input: String): Boolean {
        return isIPv4(input) || isIPv6(input)
    }

    fun isIPv4(input: String): Boolean {
        return try {
            AsyncEventLoop.parseInet4Address(input)
            true
        }
        catch (throwable: Throwable) {
            false
        }
    }

    fun isIPv6(input: String): Boolean {
        return try {
            AsyncEventLoop.parseInet6Address(input)
            true
        }
        catch (throwable: Throwable) {
            false
        }
    }
}
