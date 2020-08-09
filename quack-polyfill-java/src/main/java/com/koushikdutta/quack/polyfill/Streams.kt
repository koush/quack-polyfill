package com.koushikdutta.quack.polyfill

import com.koushikdutta.quack.JavaScriptObject
import com.koushikdutta.scratch.*
import com.koushikdutta.scratch.async.async
import com.koushikdutta.scratch.buffers.ByteBuffer
import com.koushikdutta.scratch.buffers.ByteBufferList


interface Stream: EventEmitter {
    fun pause()
    fun resume()
    fun read(size: Int = 16384): ByteBuffer?
    fun destroy(error: JavaScriptObject? = null)
}

fun Stream.createAsyncRead(quackEventLoop: QuackEventLoop, size: Int = 16384): AsyncRead {
    val yielder = Yielder()
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
            quackEventLoop.loop.await()
            val buffer = read(size)
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
    .createAsyncReadFromByteBuffers()
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
    var pauser: Yielder?

    override fun _read(len: Int?) {
        quackLoop.loop.async {
            try {
                // prevent the read loop from being started twice.
                // subsequent calls to read will just resume data pumping.
                if (pauser != null) {
                    pauser!!.resume()
                    return@async
                }
                pauser = Yielder()
                val buffer = ByteBufferList()
                while (getAsyncRead()(buffer)) {
                    // queue the data up, so when the source drains, all the data
                    // is aggregated into a single buffer. push will be only called once.
                    if (buffer.isEmpty)
                        continue

                    // get off the network loop.
                    quackLoop.loop.await()
                    val more = stream.push(buffer.readDirectByteBuffer())

                    if (!more)
                        pauser!!.yield()
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
