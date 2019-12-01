package com.koushikdutta.quack.polyfill

import com.koushikdutta.quack.JavaScriptObject
import com.koushikdutta.quack.QuackContext


private val types = mutableMapOf<Class<*>, String>(
        Pair(Int::class.java, "number"),
        Pair(String::class.java, "string")
)


private fun Any.`typeof`(): String {
    if (this is Int)
        return "number"
    if (this is String)
        return "string"

    if (this !is JavaScriptObject)
        throw AssertionError("Unknown typeof")

    val jo = this as JavaScriptObject
    return jo.`typeof`()
}

internal class ArgParser(val quack: QuackContext, vararg val arguments: Any?) {
    var index = 0
    operator fun <T> invoke(type: String): T? {
        if (index >= arguments.size)
            return null

        val arg = arguments[index]
        if (arg == null) {
            index++
            return null
        }

        if (type == "number" && arg !is Number)
            return null
        else if (type == "string" && arg !is String)
            return null
        else if (arg.`typeof`() != type)
            return null

        index++
        return arg as T
    }
}

internal fun ArgParser.Int(): Int? {
    return this<Number?>("number")?.toInt()
}

internal fun ArgParser.String(): String? {
    return this("string")
}

internal fun ArgParser.Function(): JavaScriptObject? {
    return this("function")
}

internal fun ArgParser.Object(): JavaScriptObject? {
    return this("object")
}

internal fun <T> ArgParser.Coerce(clazz: Class<T>): T? {
    val jo = Object()
    if (jo == null)
        return null
    return jo.jsonCoerce(clazz)
}