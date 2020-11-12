package com.koushikdutta.quack.polyfill.tls

import com.koushikdutta.quack.JavaScriptObject
import com.koushikdutta.quack.QuackProperty
import com.koushikdutta.quack.polyfill.*
import com.koushikdutta.quack.polyfill.net.ConnectSocketOptions
import com.koushikdutta.quack.polyfill.net.NetModule
import com.koushikdutta.quack.polyfill.net.Socket
import com.koushikdutta.quack.polyfill.net.SocketImpl
import com.koushikdutta.quack.polyfill.require.Modules
import com.koushikdutta.scratch.AsyncRead
import com.koushikdutta.scratch.AsyncWrite
import com.koushikdutta.scratch.event.AsyncNetworkSocket
import com.koushikdutta.scratch.tls.*
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

interface TlsSocket: Socket

class ConnectTlsSocketOptions: ConnectSocketOptions() {
    var rejectUnauthorized = true
}

class TlsSocketImpl(netModule: NetModule, quackLoop: QuackEventLoop, stream: DuplexStream, val tlsOptions: ConnectTlsSocketOptions?) : SocketImpl(netModule, quackLoop, stream, tlsOptions), TlsSocket {
    var tlsSocket: AsyncTlsSocket? = null
    override suspend fun connectInternal(connectHost: String, port: Int): AsyncNetworkSocket {

        if (tlsOptions?.rejectUnauthorized == false) {
            val tlsOptions = AsyncTlsOptions(TlsModule.trustAll, null)
            tlsSocket = quackLoop.loop.connectTls(connectHost, port, TlsModule.insecureContext, tlsOptions)
        }
        else {
            tlsSocket = quackLoop.loop.connectTls(connectHost, port)
        }
        return tlsSocket!!.socket as AsyncNetworkSocket
    }

    override suspend fun destroyInternal() {
        super.destroyInternal()
        tlsSocket?.close()
        tlsSocket = null
    }

    override suspend fun getAsyncRead(): AsyncRead {
        super.getAsyncRead()
        return tlsSocket!!
    }

    override suspend fun getAsyncWrite(): AsyncWrite {
        super.getAsyncWrite()
        return tlsSocket!!
    }
}

class TlsModule(val quackLoop: QuackEventLoop, val modules: Modules) {
    @get:QuackProperty(name = "TLSSocket")
    val socketClass: JavaScriptObject

    val duplexClass: JavaScriptObject
    val ctx = quackLoop.quack
    init {
        duplexClass = modules.require("stream").get("Duplex") as JavaScriptObject

        socketClass = mixinExtend(ctx, duplexClass, DuplexStream::class.java, TlsSocket::class.java, "TLSSocket") { stream, arguments ->
            val parser = ArgParser(quackLoop.quack, *arguments)
            val options = parser.Coerce(ConnectTlsSocketOptions::class.java)

            val netModule = modules["net"] as NetModule
            TlsSocketImpl(netModule, quackLoop, stream, options)
        }
    }

    private fun newSocket(options: JavaScriptObject?): JavaScriptObject {
        return socketClass.construct(options)
    }

    fun connect(vararg arguments: Any?): JavaScriptObject {
        val parser = ArgParser(quackLoop.quack, *arguments)
        val options = parser.Object()
        if (options != null) {
            val socket = newSocket(options)
            val mixin = socket.getMixin(TlsSocketImpl::class.java)
            mixin.connect(options, parser.Function())
            return socket
        }

        val port = parser.Int()!!
        val host = parser.String()
        val socket = newSocket(null)
        val mixin = socket.getMixin(TlsSocketImpl::class.java)
        mixin.connect(port, host, parser.Function())
        return socket
    }

    companion object {
        val insecureContext = SSLContext.getInstance("TLS")
        val trustAll = object : com.koushikdutta.scratch.tls.HostnameVerifier {
            override fun verify(engine: SSLEngine): Boolean {
                return true
            }
        }

        init {
            insecureContext.init(null, arrayOf<TrustManager>(object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> {
                    return arrayOf()
                }

                override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
            }), null)
        }
    }
}