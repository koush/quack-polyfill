package com.koushikdutta.quack.polyfill

import com.koushikdutta.quack.JavaScriptObject
import com.koushikdutta.quack.QuackContext

private fun Any.`typeof`(): String {
    if (this is Number)
        return "number"
    if (this is String)
        return "string"

    if (this !is JavaScriptObject)
        throw IllegalArgumentException("Unknown typeof $this")

    return `typeof`()
}

class ArgParser(val quack: QuackContext, vararg val arguments: Any?) {
    var index = 0
    operator fun <T> invoke(type: String): T? {
        if (index >= arguments.size)
            return null

        val arg = arguments[index]
        if (arg == null) {
            index++
            return null
        }

        // most node apis seem to coerce strings to ints when necessary
        if (type == "number" && arg is String && arg.toString().toIntOrNull() != null) {
            index++
            return arg.toString().toIntOrNull() as T
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

fun ArgParser.Int(): Int? {
    return this<Number?>("number")?.toInt()
}

fun ArgParser.Long(): Long? {
    return this<Number?>("number")?.toLong()
}

fun ArgParser.String(): String? {
    return this("string")
}

fun ArgParser.Function(): JavaScriptObject? {
    return this("function")
}

fun ArgParser.Object(): JavaScriptObject? {
    return this("object")
}

fun <T> ArgParser.Coerce(clazz: Class<T>): T? = Object()?.jsonCoerce(clazz)
