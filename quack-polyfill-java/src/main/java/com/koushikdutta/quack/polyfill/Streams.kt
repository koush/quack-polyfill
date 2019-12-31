package com.koushikdutta.quack.polyfill

import com.koushikdutta.quack.JavaScriptObject
import com.koushikdutta.quack.QuackContext
import com.koushikdutta.scratch.*
import com.koushikdutta.scratch.buffers.ByteBuffer
import com.koushikdutta.scratch.buffers.ByteBufferList
import com.koushikdutta.scratch.event.AsyncServerRunnable
import com.koushikdutta.scratch.event.Cancellable
import java.lang.Exception


interface Stream: EventEmitter {
    fun pause()
    fun resume()
    fun read(): ByteBuffer?
}

fun Stream.createAsyncRead(ctx: QuackContext): AsyncRead {
    val yielder = Cooperator()
    var more = true
    on("readable") {
        yielder.resume()
    }

    on("end") {
        more = false
        yielder.resume()
    }

    on("error") {
        more = false
        yielder.resume()
    }

    return asyncIterator<ByteBuffer> {
        while (more) {
            val buffer = read()
            if (buffer == null) {
                // stream could not fullfill a read request, so yield until the next
                // readable event.
                yielder.yield()
                continue
            }

            println("streaming ${buffer.remaining()}")
            yield(buffer)
        }
    }
    .createAsyncRead()
}

interface ReadableStream : EventEmitter {
    fun push(buffer: ByteBuffer): Boolean
    fun destroy(error: JavaScriptObject?)
}

interface WritableStream : EventEmitter

interface DuplexStream: ReadableStream, WritableStream

interface Destroyable {
    fun _destroy(err: Any?, callback: JavaScriptObject?)
}

interface Readable: Destroyable {
    fun _read(len: Int?)
}

interface Writable: Destroyable {
    fun _write(chunk: JavaScriptObject, encoding: String?, callback: JavaScriptObject?)
    fun _final(callback: JavaScriptObject?)
}

interface Duplex : Readable, Writable

interface BaseReadable : Readable {
    val quackLoop: QuackEventLoop
    val stream: ReadableStream
    suspend fun getAsyncRead(): AsyncRead
    fun post(runnable: AsyncServerRunnable): Cancellable
    var pauser: Cooperator?

    override fun _read(len: Int?) {
        quackLoop.loop.async {
            try {
                // prevent the read loop from being started twice.
                // subsequent calls to read will just resume data pumping.
                if (pauser != null) {
                    pauser!!.resume()
                    return@async
                }
                var needPause = false
                pauser = Cooperator()
                val buffer = ByteBufferList()
                while (getAsyncRead()(buffer)) {
                    this@BaseReadable.post {
                        // queue the data up, so when the source drains, all the data
                        // is aggregated into a single buffer. push will be only called once.
                        if (buffer.isEmpty)
                            return@post

                        val copy = ByteBufferList()
                        buffer.read(copy)

                        quackLoop.loop.async {
                            val more = stream.push(copy.readByteBuffer())

                            this@BaseReadable.post {
                                needPause = needPause || !more
                            }
                        }
                    }
                    if (needPause) {
                        pauser!!.yield()
                        needPause = false
                    }
                }
            }
            catch (e: Exception) {
                post()
                stream.destroy(quackLoop.quack.newError(e))
            }
            stream.postEmitSafely(quackLoop, "end")
            stream.postEmitSafely(quackLoop, "close")
        }
    }
}

interface BaseWritable : Writable {
    val quackLoop: QuackEventLoop
    var finalCallback: JavaScriptObject?

    suspend fun getAsyncWrite(): AsyncWrite

    override fun _write(chunk: JavaScriptObject, encoding: String?, callback: JavaScriptObject?) {
        quackLoop.loop.async {
            try {
                val buffer = ByteBufferList(chunk.get("buffer") as ByteBuffer)
                while (buffer.hasRemaining()) {
                    getAsyncWrite()(buffer)
                }
                post()
                callback?.callSafely(quackLoop, null)
                finalCallback?.callSafely(quackLoop, null)
            }
            catch (e: Exception) {
                post()
                val err = quackLoop.quack.newError(e)
                callback?.callSafely(quackLoop, err)
                finalCallback?.callSafely(quackLoop, err)
            }
        }
    }

    override fun _final(callback: JavaScriptObject?) {
        finalCallback = callback
    }
}

interface BaseDuplex : Duplex, BaseReadable, BaseWritable
