package com.koushikdutta.quack.polyfill.fs

import com.koushikdutta.quack.JavaScriptObject
import com.koushikdutta.quack.QuackProperty
import com.koushikdutta.quack.polyfill.*
import com.koushikdutta.scratch.buffers.ByteBuffer
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.file.OpenOption
import java.nio.file.Paths
import java.util.*


class Stats {
    @get:QuackProperty(name = "size")
    var size: Int? = null
}

class Constants {
    @get:QuackProperty(name = "O_RDONLY")
    val O_RDONLY = 1 shl StandardOpenOption.READ.ordinal
    @get:QuackProperty(name = "O_WRONLY")
    val O_WRONLY = 1 shl StandardOpenOption.WRITE.ordinal
    @get:QuackProperty(name = "O_RDWR")
    val O_RDWR = O_RDONLY or O_WRONLY
    @get:QuackProperty(name = "O_CREAT")
    val O_CREAT = 1 shl StandardOpenOption.CREATE.ordinal
    @get:QuackProperty(name = "O_EXCL")
    val O_EXCL = 1 shl StandardOpenOption.CREATE_NEW.ordinal
    @get:QuackProperty(name = "O_TRUNC")
    val O_TRUNC = 1 shl StandardOpenOption.TRUNCATE_EXISTING.ordinal
    @get:QuackProperty(name = "O_APPEND")
    val O_APPEND = 1 shl StandardOpenOption.APPEND.ordinal
    @get:QuackProperty(name = "O_SYNC")
    val O_SYNC = 1 shl StandardOpenOption.SYNC.ordinal
    @get:QuackProperty(name = "O_DSYNC")
    val O_DSYNC = 1 shl StandardOpenOption.DSYNC.ordinal
}

class FsModule(val quackLoop: QuackEventLoop) {
    @get:QuackProperty(name = "constants")
    val constants = Constants()

    var fdCount = 100
    val fds = mutableMapOf<Int, FileChannel>()
    fun open(path: String, vararg arguments: Any?): Int? {
        val parse = ArgParser(quackLoop.quack, *arguments)
        val flagsInt = parse.Int()
        val flags = parse.String() ?: "r"
        val mode = parse.Int()
        val callback: JavaScriptObject? = parse("function")

        val flagSet = mutableSetOf<StandardOpenOption>()

        if (flagsInt != null) {
            for (option in StandardOpenOption.values()) {
                val flagVal = 1 shl option.ordinal
                if (flagVal and flagsInt == flagVal)
                    flagSet.add(option)
            }
        }
        else {
            if (flags == "a") {
                flagSet.add(StandardOpenOption.APPEND)
            } else if (flags == "ax") {
                flagSet.add(StandardOpenOption.APPEND)
                flagSet.add(StandardOpenOption.CREATE_NEW)
            } else if (flags == "a+") {
                flagSet.add(StandardOpenOption.APPEND)
                flagSet.add(StandardOpenOption.CREATE)
            } else if (flags == "as") {
                flagSet.add(StandardOpenOption.APPEND)
                flagSet.add(StandardOpenOption.SYNC)
            } else if (flags == "as+") {
                flagSet.add(StandardOpenOption.APPEND)
                flagSet.add(StandardOpenOption.READ)
                flagSet.add(StandardOpenOption.SYNC)
            } else if (flags == "r") {
                flagSet.add(StandardOpenOption.READ)
            } else if (flags == "r+") {
                flagSet.add(StandardOpenOption.READ)
                flagSet.add(StandardOpenOption.WRITE)
            } else if (flags == "rs+") {
                flagSet.add(StandardOpenOption.READ)
                flagSet.add(StandardOpenOption.WRITE)
                flagSet.add(StandardOpenOption.SYNC)
            } else if (flags == "w") {
                flagSet.add(StandardOpenOption.WRITE)
                flagSet.add(StandardOpenOption.CREATE)
                flagSet.add(StandardOpenOption.TRUNCATE_EXISTING)
            } else if (flags == "wx") {
                flagSet.add(StandardOpenOption.WRITE)
                flagSet.add(StandardOpenOption.CREATE_NEW)
            } else if (flags == "w+") {
                flagSet.add(StandardOpenOption.READ)
                flagSet.add(StandardOpenOption.WRITE)
                flagSet.add(StandardOpenOption.CREATE)
                flagSet.add(StandardOpenOption.TRUNCATE_EXISTING)
            } else if (flags == "wx+") {
                flagSet.add(StandardOpenOption.READ)
                flagSet.add(StandardOpenOption.WRITE)
                flagSet.add(StandardOpenOption.CREATE_NEW)
            }
        }

        return doIOOperation(callback) {
            if (flagSet.contains(StandardOpenOption.CREATE_NEW) && File(path).exists())
                throw IOException("file exists")

            val mode: String
            if (flagSet.contains(StandardOpenOption.WRITE)) {
                if (flagSet.contains(StandardOpenOption.DSYNC))
                    mode = "rws"
                else if (flagSet.contains(StandardOpenOption.SYNC))
                    mode = "rwd"
                else
                    mode = "rw"
            }
            else {
                mode = "r"
            }

            val randomAccess = RandomAccessFile(path, mode)
            val fc = randomAccess.channel

            if (flagSet.contains(StandardOpenOption.TRUNCATE_EXISTING)) {
                fc.truncate(0)
            }
            if (flagSet.contains(StandardOpenOption.APPEND)) {
                fc.position(fc.size())
            }

//            val fc = FileChannel.open(Paths.get(path), *flagSet.toTypedArray())
            val file = fdCount++
            fds[file] = fc

            file
        }
    }

    private fun <T> doIOOperation(callback: JavaScriptObject?, block: () -> T): T? {
        if (callback == null)
            return block()

        quackLoop.ioWorkers.submit {
            try {
                callback.postCallSafely(quackLoop, null, block())
            }
            catch (throwable: Throwable) {
                callback.postCallSafely(quackLoop, quackLoop.quack.newError(throwable), null)
            }
        }
        return null
    }

    fun openSync(path: String, vararg arguments: Any?): Int? {
        return open(path, *arguments)
    }

    fun read(fd: Int, buffer: ByteBuffer, offset: Int, length: Int, position: Long?, callback: JavaScriptObject?): Int? {
        val block: () -> Int = {
            val read: Int
            val channel = fds[fd]!!
            buffer.offsetPosition(offset)
            buffer.lengthLimit(length)
            if (position != null) {
                val curPos = channel.position()
                read = channel.read(buffer, position)
                channel.position(curPos)
            } else {
                read = channel.read(buffer)
            }
            if (read < 0)
                throw IOException("read failed")
            read
        }

        if (callback == null)
            return block()

        quackLoop.ioWorkers.submit {
            try {
                val read = block()
                callback.postCallSafely(quackLoop, null, read, buffer)
            }
            catch (throwable: Throwable) {
                callback.postCallSafely(quackLoop, throwable, null, null)
            }
        }
        return null
    }

    fun readSync(fd: Int, buffer: ByteBuffer, offset: Int, length: Int, position: Long?): Int? {
        return read(fd, buffer, offset, length, position, null)
    }

    fun write(fd: Int, buffer: ByteBuffer, vararg arguments: Any?): Int? {
        val parse = ArgParser(quackLoop.quack, *arguments)
        val offset = parse.Int()
        val length = parse.Int()
        val position = parse.Long()
        val callback: JavaScriptObject? = parse("function")

        return doIOOperation(callback) {
            val written: Int?
            val channel = fds[fd]!!
            if (offset != null) {
                buffer.offsetPosition(offset)
                if (length != null)
                    buffer.lengthLimit(length)
            }
            if (position != null) {
                val curPos = channel.position()
                written = channel.write(buffer, position)
                channel.position(curPos)
            } else {
                written = channel.write(buffer)
            }
            written
        }
    }

    fun writeSync(fd: Int, buffer: ByteBuffer, vararg arguments: Any?): Int? {
        return write(fd, buffer, *arguments)
    }

    private fun fstatInternal(channel: FileChannel): Stats? {
        var stats = Stats()
        stats.size = channel.size().toInt()
        return stats
    }

    fun fstat(fd: Int, vararg arguments: Any?): Stats? {
        val parse = ArgParser(quackLoop.quack, *arguments)
        val options: JavaScriptObject? = parse("object")
        val callback: JavaScriptObject? = parse("function")

        return doIOOperation(callback) {
            fstatInternal(fds[fd]!!)
        }
    }

    fun fstatSync(fd: Int, vararg arguments: Any?): Stats? {
        return fstat(fd, *arguments)
    }

    fun stat(path: String, vararg arguments: Any?): Stats? {
        val parse = ArgParser(quackLoop.quack, *arguments)
        val options: JavaScriptObject? = parse("object")
        val callback: JavaScriptObject? = parse("function")

        return doIOOperation(callback) {
            val channel = RandomAccessFile(path, "r").channel
//            val channel = FileChannel.open(Paths.get(path), StandardOpenOption.READ)
            val stats = fstatInternal(channel)
            channel.close()
            stats
        }
    }

    fun statSync(path: String, vararg arguments: Any?): Stats? {
        return stat(path, *arguments)
    }

    fun ftruncate(fd: Int, vararg arguments: Any?): Unit? {
        val parse = ArgParser(quackLoop.quack, *arguments)
        val len = parse.Long() ?: 0
        val callback = parse<JavaScriptObject>("function")

        return doIOOperation(callback) {
            val channel = fds[fd]!!
            channel.truncate(len)
            null
        }
    }

    fun ftruncateSync(fd: Int, vararg arguments: Any?) {
        ftruncate(fd, *arguments)
    }

    fun unlink(path: String, callback: JavaScriptObject?): Unit? = doIOOperation(callback) {
        File(path).delete()
        null
    }

    fun unlinkSync(path: String) {
        unlink(path, null)
    }

    fun rmdir(path: String, vararg arguments: Any?): Unit? {
        val parse = ArgParser(quackLoop.quack, *arguments)
        val options = parse<JavaScriptObject>("object")
        val callback = parse<JavaScriptObject>("function")

        return doIOOperation(callback) {
            File(path).delete()
            null
        }
    }

    fun rmdirSync(path: String, vararg arguments: Any?) {
        rmdir(path, *arguments)
    }

    class MkdirOptions {
        var recursive: Boolean = false
    }

    fun mkdir(path: String, vararg arguments: Any?): Unit? {
        val parse = ArgParser(quackLoop.quack, *arguments)
        val options = parse<JavaScriptObject>("object")?.jsonCoerce(MkdirOptions::class.java) ?: MkdirOptions()
        val mode = parse.Int()
        val callback = parse<JavaScriptObject>("function")

        return doIOOperation(callback) {
            if (options.recursive)
                File(path).mkdirs()
            else
                File(path).mkdir()
            null
        }
    }

    fun mkdirSync(path: String, vararg arguments: Any?) {
        mkdir(path, *arguments)
    }

    fun close(fd: Int, callback: JavaScriptObject?): Unit? = doIOOperation(callback) {
        val channel = fds[fd]!!
        try {
            channel.close()
        }
        catch (throwable: Throwable) {
        }
    }

    fun closeSync(fd: Int) {
        close(fd, null)
    }
}

fun ByteBuffer.offsetPosition(offset: Int) {
    position(position() + offset)
}

fun ByteBuffer.lengthLimit(length: Int) {
    limit(position() + length)
}