package com.koushikdutta.quack.polyfill

import com.koushikdutta.quack.JavaScriptObject
import com.koushikdutta.quack.QuackContext
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
import com.koushikdutta.scratch.buffers.ByteBuffer
import com.koushikdutta.scratch.event.AsyncEventLoop
import com.koushikdutta.scratch.http.client.AsyncHttpClient
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
        return ThreadPoolExecutor(1, maximumPoolSize, 10L,
                TimeUnit.SECONDS, LinkedBlockingQueue(), tf)
    }

    val computeWorkers = newSynchronousWorker("quack-compute", 4)
    // serialize io ops
    val ioWorkers = newSynchronousWorker("quack-io", 1)

    init {
        installJobScheduler()

        quack.putJavaScriptToJavaCoercion(ByteBuffer::class.java) { clazz, o ->
            val jo = o as JavaScriptObject
            jo.toByteBuffer()
        }
    }

    fun installDefaultModules(modules: Modules): Modules {
        modules["dgram"] = DgramModule(this, modules)
        modules["net"] = NetModule(this, modules)
        modules["tls"] = TlsModule(this, modules)
        modules["fs"] = FsModule(this)
        modules["os"] = OSModule(this)
        modules["crypto"] = CryptoModule(this, modules)
        modules["dns"] = DnsModule(this, modules)
        val client = AsyncHttpClient(loop)
        quack.globalObject.set("XMLHttpRequest", XMLHttpRequest.Constructor(quack, client))
        return modules
    }
}

fun JavaScriptObject.toByteBuffer(): ByteBuffer {
    val byteOffset = get("byteOffset") as Int
    val length = get("length") as Int
    val buffer = get("buffer") as ByteBuffer
    buffer.position(byteOffset)
    buffer.limit(byteOffset + length)
    return buffer
}
