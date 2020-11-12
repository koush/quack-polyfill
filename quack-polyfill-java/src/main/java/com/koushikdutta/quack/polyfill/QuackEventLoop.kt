package com.koushikdutta.quack.polyfill

import com.koushikdutta.quack.JavaScriptObject
import com.koushikdutta.quack.QuackContext
import com.koushikdutta.quack.QuackMethodObject
import com.koushikdutta.quack.polyfill.crypto.CryptoModule
import com.koushikdutta.quack.polyfill.dgram.DgramModule
import com.koushikdutta.quack.polyfill.dns.DnsModule
import com.koushikdutta.quack.polyfill.fs.FsModule
import com.koushikdutta.quack.polyfill.job.installJobScheduler
import com.koushikdutta.quack.polyfill.net.NetModule
import com.koushikdutta.quack.polyfill.os.OSModule
import com.koushikdutta.quack.polyfill.require.Modules
import com.koushikdutta.quack.polyfill.tls.TlsModule
import com.koushikdutta.quack.polyfill.xmlhttprequest.XMLHttpRequest
import com.koushikdutta.scratch.AsyncAffinity
import com.koushikdutta.scratch.AsyncSocket
import com.koushikdutta.scratch.Deferred
import com.koushikdutta.scratch.Promise
import com.koushikdutta.scratch.buffers.ByteBuffer
import com.koushikdutta.scratch.event.AsyncEventLoop
import com.koushikdutta.scratch.http.AsyncHttpRequest
import com.koushikdutta.scratch.http.client.AsyncHttpClient
import com.koushikdutta.scratch.http.client.AsyncHttpExecutor
import com.koushikdutta.scratch.http.client.executor.*
import com.koushikdutta.scratch.tls.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

class QuackEventLoop(val loop: AsyncEventLoop, val netLoop: AsyncEventLoop, val quack: QuackContext) {
    constructor() : this(AsyncEventLoop())
    constructor(loop: AsyncEventLoop) : this(loop, loop, QuackContext.create())
    constructor(loop: AsyncEventLoop, netLoop: AsyncEventLoop) : this(loop, netLoop, QuackContext.create())

    private class NamedThreadFactory internal constructor(namePrefix: String) : ThreadFactory {
        private val group: ThreadGroup
        private val threadNumber = AtomicInteger(1)
        private val namePrefix: String
        override fun newThread(r: Runnable): Thread {
            val t = Thread(group, r,
                    namePrefix + threadNumber.getAndIncrement(), 0)
            if (t.isDaemon) t.isDaemon = false
            if (t.priority != Thread.NORM_PRIORITY) {
                t.priority = Thread.NORM_PRIORITY
            }
            return t
        }

        init {
            val s = System.getSecurityManager()
            group = if (s != null) s.threadGroup else Thread.currentThread().threadGroup
            this.namePrefix = namePrefix
        }
    }

    private fun newSynchronousWorker(prefix: String, maximumPoolSize: Int): ExecutorService {
        val tf: ThreadFactory = NamedThreadFactory(prefix)
        return ThreadPoolExecutor(0, maximumPoolSize, 10L,
                TimeUnit.SECONDS, LinkedBlockingQueue(), tf)
    }

    val computeWorkers = newSynchronousWorker("quack-compute", 4)
    // serialize io ops
    val ioWorkers = newSynchronousWorker("quack-io", 1)

    init {
        Console(quack, System.out, System.err)
        installJobScheduler()

        quack.putJavaScriptToJavaCoercion(ByteBuffer::class.java) { clazz, o ->
            val jo = o as JavaScriptObject
            jo.toByteBuffer()
        }

        quack.putJavaScriptToJavaCoercion(Promise::class.java) { clazz, o ->
            val deferred = Deferred<Any?>()
            val jo = o as JavaScriptObject
            jo.callProperty("then", object : QuackMethodObject {
                override fun callMethod(thiz: Any?, vararg args: Any?): Any? {
                    deferred.resolve(quack.coerceJavaScriptToJava(null, args[0]))
                    return null
                }
            })

            jo.callProperty("catch", object : QuackMethodObject {
                override fun callMethod(thiz: Any?, vararg args: Any?): Any? {
                    try {
                        quack.throwObject(args[0])
                    }
                    catch (throwable: Throwable) {
                        deferred.reject(throwable)
                    }
                    return null
                }
            })

            deferred.promise
        }
    }

    fun installXHR(rejectUnauthorized: Boolean = true): AsyncHttpClient {
        val client = AsyncHttpClient(netLoop)
        if (!rejectUnauthorized) {
            client.schemeExecutor.useInsecureHttpsExecutor(client.eventLoop, resolver = createNetworkResolver(443, client.eventLoop))
        }
        quack.globalObject.set("XMLHttpRequest", XMLHttpRequest.XMLHttpRequestConstructor(quack, client))
        return client
    }

    fun installDefaultModules(modules: Modules): Modules {
        modules["dgram"] = DgramModule(this, modules)
        modules["net"] = NetModule(this, modules)
        modules["tls"] = TlsModule(this, modules)
        modules["fs"] = FsModule(this)
        modules["os"] = OSModule(this)
        CryptoModule.mixin(this, modules)
        modules["dns"] = DnsModule(this, modules)

        return modules
    }
}

class HttpsInsecureHostExecutor(affinity: AsyncAffinity, resolver: RequestSocketResolver):
        HostExecutor<AsyncTlsSocket>(affinity, 443, resolver) {

    override suspend fun upgrade(request: AsyncHttpRequest, socket: AsyncSocket): AsyncTlsSocket {
        val port = request.getPortOrDefault(443)
        val host = request.uri.host!!
        val options = AsyncTlsOptions(object : HostnameVerifier {
            override fun verify(engine: SSLEngine): Boolean {
                return true
            }
        }, null)
        return socket.connectTls(host, port, TlsModule.insecureContext, options)
    }


    override suspend fun createConnectExecutor(request: AsyncHttpRequest, connect: ResolvedSocketConnect<AsyncTlsSocket>): AsyncHttpExecutor {
        return AsyncHttpConnectSocketExecutor(affinity, connect)::invoke
    }
}

fun SchemeExecutor.useInsecureHttpsExecutor(affinity: AsyncAffinity,
                                            resolver: RequestSocketResolver): SchemeExecutor {
    val https = HttpsInsecureHostExecutor(affinity, resolver)

    register("https", https::invoke)
    register("wss", https::invoke)
    return this
}


fun JavaScriptObject.toByteBuffer(): ByteBuffer {
    val byteOffset = get("byteOffset") as Int
    val length = get("length") as Int
    val buffer = get("buffer") as ByteBuffer
    buffer.slice()
    buffer.position(byteOffset)
    buffer.limit(byteOffset + length)
    val sliced = buffer.slice()

    // since the incoming buffer is keeping the JavaScript ArrayBuffer from being garbage collected,
    // make sure the sliced buffer keeps it reachable by the GC until it goes out of scope.

    // note: i'm actually not sure this is necessary, might be OS dependent. older android
    // versions seemingly do not seem to handle this correctly.
    // presumably, the sliced buffer retains a reference to the source buffer.
    // however, it does map the ByteBuffer back to the exact JavaScriptObject (Buffer or Uint8Array)
    // if passed back into the runtime.
    quackContext.quackMapNative(sliced, buffer)

    return sliced
}
