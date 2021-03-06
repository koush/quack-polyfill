package com.koushikdutta.quack.polyfill

import com.google.gson.*
import com.koushikdutta.quack.JavaScriptObject
import com.koushikdutta.quack.QuackContext
import com.koushikdutta.quack.QuackJsonObject
import java.lang.reflect.Type
import java.util.*

internal val gson = GsonBuilder()
        .serializeNulls()
        .setLongSerializationPolicy(LongSerializationPolicy.DEFAULT)
        .registerTypeAdapter(Date::class.java, JsonDeserializer<Date> { json: JsonElement, _: Type?, _: JsonDeserializationContext? -> Date(json.asJsonPrimitive.asLong) })
        .registerTypeAdapter(Date::class.java, JsonSerializer<Date> { date: Date, type: Type?, _: JsonSerializationContext? -> JsonPrimitive(date.time) })
        .create()

internal fun <T> JavaScriptObject.jsonCoerce(clazz: Class<T>): T {
    return gson.fromJson<T>(stringify(), clazz)
}

internal fun <T> jsonCoerce(clazz: Class<T>, value: T) : QuackJsonObject {
    return QuackJsonObject(gson.toJson(value, clazz))
}

// move to quack extensions.kt?
fun <T> QuackContext.putJavaToJsonCoersion(clazz: Class<T>) {
    putJavaToJavaScriptCoercion(clazz) { _, o ->
        jsonCoerce(clazz, o)
    }
}
