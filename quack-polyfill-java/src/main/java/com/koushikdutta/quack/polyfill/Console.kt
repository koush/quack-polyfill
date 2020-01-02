package com.koushikdutta.quack.polyfill

import com.koushikdutta.quack.JavaScriptObject
import com.koushikdutta.quack.QuackContext
import com.koushikdutta.quack.QuackMethodObject
import java.io.PrintStream

class Console(var quack: QuackContext, var out: PrintStream, var err: PrintStream) {
    val jo: JavaScriptObject = quack.evaluateForJavaScriptObject("({})")

    init {
        quack.globalObject.set("console", jo)

        val o = object : QuackMethodObject {
            override fun callMethod(thiz: Any?, vararg args: Any?): Any? {
                out.println(getLog(*args))
                return null
            }
        }
        val e = object : QuackMethodObject {
            override fun callMethod(thiz: Any?, vararg args: Any?): Any? {
                err.println(getLog(*args))
                return null
            }
        }

        // expectation is that "console" is an object.
        jo.set("log", o)
        jo.set("error", e)
        jo.set("warn", e)
        jo.set("debug", e)
        jo.set("info", e)
        jo.set("assert", e)
    }

    fun getLog(vararg objects: Any?): String {
        val b = StringBuilder()
        for (o in objects) {
            if (o == null) b.append("null") else b.append(o.toString())
        }
        return b.toString()
    }
}
