package com.koushikdutta.quack.polyfill

import com.koushikdutta.quack.JavaScriptObject
import com.koushikdutta.quack.QuackContext
import com.koushikdutta.quack.polyfill.net.NetModule
import com.koushikdutta.quack.polyfill.require.EvalScript
import com.koushikdutta.quack.polyfill.require.Modules
import com.koushikdutta.quack.polyfill.require.ReadFile
import com.koushikdutta.quack.polyfill.require.installModules
import com.koushikdutta.quack.polyfill.xmlhttprequest.XMLHttpRequest
import com.koushikdutta.scratch.*
import com.koushikdutta.scratch.async.async
import com.koushikdutta.scratch.event.AsyncServerRunnable
import com.koushikdutta.scratch.event.Cancellable
import com.koushikdutta.scratch.http.AsyncHttpResponse
import com.koushikdutta.scratch.http.Headers
import com.koushikdutta.scratch.http.OK
import com.koushikdutta.scratch.http.ResponseLine
import com.koushikdutta.scratch.http.body.BinaryBody
import com.koushikdutta.scratch.http.body.Utf8StringBody
import com.koushikdutta.scratch.http.client.AsyncHttpClient
import com.koushikdutta.scratch.http.server.AsyncHttpRouter
import com.koushikdutta.scratch.http.server.AsyncHttpServer
import com.koushikdutta.scratch.http.server.get
import org.junit.Test
import java.io.File
import java.io.PrintStream
import kotlin.random.Random


class PolyfillTests {
    companion object {
        init {
            // for non-android jvm
            try {
                System.load(File("../quack/quack-jni/build/lib/main/debug/libquack-jni.dylib").getCanonicalPath());
            }
            catch (e: Error) {
                try {
                    System.load(File("../../quack/quack-jni/build/lib/main/debug/libquack-jni.dylib").getCanonicalPath());
                }
                catch (e: Error) {
                    throw AssertionError("jni load failed")
                }
            }
        }

        private fun QuackContext.loadModules(): Modules {
            val baseDir = File(".", "src/js")

            return installModules(ReadFile {
                val file = baseDir.resolve(it)
                if (!file.exists() || !file.isFile)
                    null
                else
                    StreamUtility.readFile(file)
            }, EvalScript { script, filename ->
                val file = baseDir.resolve(filename).canonicalPath
//                println("evaluating $file")
                evaluate(script, file) as JavaScriptObject
            })
        }
    }


    interface XCallback {
        fun onX(x: XMLHttpRequest);
    }

    class Console(var quack: QuackContext, var out: PrintStream, var err: PrintStream) {
        fun getLog(vararg objects: Any?): String {
            val b = StringBuilder()
            for (o in objects) {
                if (o == null) b.append("null") else b.append(o.toString())
            }
            return b.toString()
        }

        fun log(vararg objects: Any?) {
            out.println(getLog(*objects))
        }

        fun error(vararg objects: Any?) {
            err.println(getLog(*objects))
        }

        fun warn(vararg objects: Any?) {
            err.println(getLog(*objects))
        }

        fun debug(vararg objects: Any?) {
            err.println(getLog(*objects))
        }

        fun info(vararg objects: Any?) {
            err.println(getLog(*objects))
        }

        fun assert(vararg objects: Any?) {
            err.println(getLog(*objects))
        }
    }

    @Test
    fun testSocket() {
        val quackLoop = QuackEventLoop()
        val modules = quackLoop.installDefaultModules(quackLoop.quack.loadModules())

        var data = ""
        quackLoop.loop.async {
            val serverSocket = listen()
            serverSocket.acceptAsync {
                makeJunkRead().copy(::write)
                close()
            }

            try {
                val socket = (modules["net"] as NetModule).createConnection(serverSocket.localPort).proxyInterface(Stream::class.java)
                data = socket.createAsyncRead(quackLoop).digest()
                quackLoop.loop.stop()
            }
            catch (throwable: Throwable) {
                println(throwable)
            }
        }

        quackLoop.loop.run()
        assert(data == "1a1640ee9890e4539525aa8cdcb5d8f8")
    }

    @Test
    fun testHttp() {
        val quackLoop = QuackEventLoop()
        val modules = quackLoop.installDefaultModules(quackLoop.quack.loadModules())
        val server = AsyncHttpServer {
            AsyncHttpResponse.OK(body = Utf8StringBody("hello world"))
        }

        var data = ""
        quackLoop.loop.async {
            val serverSocket = listen()
            val port = serverSocket.localPort
            server.listen(serverSocket)

            val cb: XCallback = object : XCallback {
                override fun onX(x: XMLHttpRequest) {
                    data = x.responseText!!
                    if (x.readyState == 4)
                        quackLoop.loop.stop()
                }
            }

            val scriptString = "(function(cb) { var x = new XMLHttpRequest(); x.open('GET', 'http://localhost:$port'); x.onreadystatechange = () => cb(x); x.send(); })"
            quackLoop.quack.evaluateForJavaScriptObject(scriptString).call(quackLoop.quack.coerceJavaToJavaScript(XCallback::class.java, cb));

        }

        quackLoop.loop.postDelayed(5000) {
            throw AssertionError("timeout")
        }
        quackLoop.loop.run()

        assert(data == "hello world")
    }

    @Test
    fun testStreams() {
        val quackLoop = QuackEventLoop()
        val modules = quackLoop.installDefaultModules(quackLoop.quack.loadModules())

        val readableClass = modules.require("stream").get("Readable") as JavaScriptObject

        val junkRead = makeJunkRead()
        val customStreamClass = mixinExtend(quackLoop.quack, readableClass, ReadableStream::class.java, Readable::class.java, "CustomStream") { stream, arguments ->
            object : BaseReadable {
                override val quackLoop = quackLoop
                override val stream = stream
                override var pauser: Yielder? = null
                override suspend fun getAsyncRead(): AsyncRead = junkRead

                override fun _destroy(err: Any?, callback: JavaScriptObject?) {
                }
            }
        }

        val stream = customStreamClass.constructCoerced(Stream::class.java)

        var digest = ""
        quackLoop.loop.async {
            digest = stream.createAsyncRead(quackLoop).digest()
            quackLoop.loop.stop()
        }

        quackLoop.loop.run()
        assert(digest == "1a1640ee9890e4539525aa8cdcb5d8f8")
    }

    fun makeJunkRead(): AsyncRead {
        var readCount = 0
        val random = Random(55555)
        return read@{
            if (readCount > 50)
                return@read false

            readCount++

            it.putBytes(random.nextBytes(10000))

            true
        }
    }

    @Test
    fun testEvents() {
        val quackLoop = QuackEventLoop()
        val modules = quackLoop.installDefaultModules(quackLoop.quack.loadModules())
        val events = modules.require("events")
        val eventEmitter = events.constructCoerced(EventEmitter::class.java)
        var eventData = ""
        eventEmitter.on("test") {
            eventData = it[0].toString()
        }
        eventEmitter.emit("test", "hello world")

        assert(eventData == "hello world")
    }

    @Test
    fun testSetTimeout() {
        val quackLoop = QuackEventLoop()
        val jo = quackLoop.quack.evaluateForJavaScriptObject("(function(loop) { setTimeout(() => loop.stop(), 0) })")
        jo.call(quackLoop.loop);
        quackLoop.loop.run();
    }

    @Test
    fun testSetImmediate() {
        val quackLoop = QuackEventLoop()
        val jo = quackLoop.quack.evaluateForJavaScriptObject("(function(loop) { setImmediate(() => loop.stop()) })")
        jo.call(quackLoop.loop);
        quackLoop.loop.run();
    }

    @Test
    fun testSetInterval() {
        val quackLoop = QuackEventLoop()
        val jo = quackLoop.quack.evaluateForJavaScriptObject("(function(loop) { var count = 0; setInterval(() => { if (count++ == 3) loop.stop() }, 500) })")
        jo.call(quackLoop.loop);
        quackLoop.loop.run();
    }
}
