package com.koushikdutta.quack.polyfill.crypto

import com.koushikdutta.quack.JavaScriptObject
import com.koushikdutta.quack.QuackMethodObject
import com.koushikdutta.quack.QuackProperty
import com.koushikdutta.quack.polyfill.*
import com.koushikdutta.quack.polyfill.require.Modules
import com.koushikdutta.scratch.buffers.ByteBuffer
import java.security.MessageDigest
import kotlin.random.Random

class Hash(val bufferClass: JavaScriptObject, val messageDigest: MessageDigest) {
    fun update(data: Any, inputEncoding: String?): Hash {
        if (inputEncoding != null)
            throw Exception("unimplemented crypto.hash inputEncoding $inputEncoding")

        val buffer: ByteBuffer
        if (data is String)
            buffer = ByteBuffer.wrap(data.toByteArray())
        else
            buffer = (data as JavaScriptObject).toByteBuffer()
        messageDigest.update(buffer)
        return this
    }

    fun digest(encoding: String?): Any {
        val bytes = messageDigest.digest()
        val buffer = ByteBuffer.wrap(bytes)
        if (encoding == null)
            return bufferClass.callProperty("from", buffer)
        if (encoding == "latin1" || encoding == "binary")
            return String(bytes, Charsets.ISO_8859_1)
        else if (encoding == "hex")
            return bytes.joinToString("") { "%02x".format(it) }.toLowerCase()
        throw IllegalArgumentException("Unknown encoding $encoding")
    }
}

class RandomBytes(quackLoop: QuackEventLoop, modules: Modules) {

}

class CryptoModule {
    companion object {
        fun mixin(quackLoop: QuackEventLoop, modules: Modules) {
            val crypto = modules.require("crypto")
            val createHash = crypto["createHash"] as JavaScriptObject
            val bufferClass: JavaScriptObject = modules.require("buffer").get("Buffer") as JavaScriptObject

            val randomBytes = object : QuackMethodObject {
                override fun callMethod(thiz: Any?, vararg args: Any?): Any? {
                    val parser = ArgParser(quackLoop.quack, *args)
                    val size = parser.Int()!!
                    val callback = parser.Function()
                    val bytes = Random.nextBytes(size)
                    val buffer = bufferClass.callProperty("from", ByteBuffer.wrap(bytes)) as JavaScriptObject
                    if (callback == null)
                        return buffer

                    callback.postCallSafely(quackLoop, buffer)
                    return null
                }
            }

            modules["randombytes"] = randomBytes
            crypto["randomBytes"] = randomBytes

            crypto["createHash"] = object : QuackMethodObject {
                override fun callMethod(thiz: Any?, vararg args: Any?): Any {
                    try {
                        val name = args[0] as String
                        if (name.toLowerCase() == "md4")
                            return Hash(bufferClass, MD4())
                        return Hash(bufferClass, MessageDigest.getInstance(name))
                    }
                    catch (throwable: Throwable) {
                        return createHash.callMethod(thiz, *args)
                    }
                }
            }
        }
    }
}