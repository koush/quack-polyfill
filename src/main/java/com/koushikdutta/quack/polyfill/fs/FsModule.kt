package com.koushikdutta.quack.polyfill.fs

import com.koushikdutta.quack.JavaScriptObject
import com.koushikdutta.quack.QuackProperty
import com.koushikdutta.quack.polyfill.*
import com.koushikdutta.quack.polyfill.ArgParser
import com.koushikdutta.quack.polyfill.require.Modules
import com.koushikdutta.scratch.buffers.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.OpenOption
import java.nio.file.Paths
import java.nio.file.StandardOpenOption


class Stats {

}

class Constants {
    @get:QuackProperty(name = "READONLY")
    val READONLY = 0
    @get:QuackProperty(name = "READWRITE")
    val READWRITE = 1
}

class FsModule(val quackLoop: QuackEventLoop, modules: Modules) {
    @get:QuackProperty(name = "constants")
    val constants = Constants()

    fun statSync(file: String, options: JavaScriptObject?): Stats? {
//        throw Exception()
        return Stats()
    }

    var fdCount = 100
    val fds = mutableMapOf<Int, FileChannel>()
    fun open(path: String, vararg arguments: Any?): Int? {
        val parse = ArgParser(quackLoop.quack, *arguments)
        val flags = parse(String::class.java) ?: "r"
        val mode = parse(Int::class.java)
        val callback = parse(JavaScriptObject::class.java)

        val flagSet = mutableSetOf<OpenOption>()
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

        var file: Int?
        var rethrow: Throwable?
        try {
            val fc = FileChannel.open(Paths.get(path), *flagSet.toTypedArray())
            file = fdCount++
            fds[file] = fc
            rethrow = null
        } catch (throwable: Throwable) {
            file = null
            rethrow = throwable
        }

        return doCallback(rethrow, callback, file)
    }

    private fun <T> doCallback(rethrow: Throwable?, callback:JavaScriptObject?, vararg arguments: Any?): T? {
        if (callback == null) {
            if (rethrow != null)
                throw rethrow
            return arguments[0] as T?
        }

        val err: JavaScriptObject?
        if (rethrow != null)
            err = quackLoop.quack.newError(rethrow)
        else
            err = null
        callback.callImmediateSafely(quackLoop, err, *arguments)
        return null
    }

    fun openSync(path: String, vararg arguments: Any?): Int? {
        return open(path, *arguments)
    }

    fun read(fd: Int, buffer: ByteBuffer, offset: Int, length: Int, position: Int?, callback: JavaScriptObject?): Int? {
        var read: Int?
        try {
            val channel = fds[fd]!!
            read = channel.read()
        }
        catch (throwable: Throwable) {

        }
    }
}