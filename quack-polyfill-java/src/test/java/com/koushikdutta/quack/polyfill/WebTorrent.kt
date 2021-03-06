package com.koushikdutta.quack.polyfill

import com.koushikdutta.quack.JavaScriptObject
import com.koushikdutta.quack.QuackJavaScriptObject
import com.koushikdutta.quack.QuackProperty

interface Torrent : QuackJavaScriptObject, EventEmitter {
    fun deselect(start: Int, end: Int, priority: Boolean)
    fun resume()
    fun pause()
}

interface WebTorrent : EventEmitter {
    fun add(torrentId: String, options: JavaScriptObject, callback: TorrentCallback)
}

fun WebTorrent.add(torrentId: String, options: JavaScriptObject, callback: (torrent: Torrent) -> Unit) {
    add(torrentId, options, object : TorrentCallback {
        override fun invoke(torrent: Torrent) {
            callback(torrent)
        }
    })
}

fun <T> JavaScriptObject.forOf(clazz: Class<T>, callback: (value: T) -> Unit) {
    val cb = object : ValueCallback {
        override fun invoke(value: Any?) {
            callback(quackContext.coerceJavaScriptToJava(clazz, value) as T)
        }
    }

    quackContext.evaluateForJavaScriptObject("(function(thiz, cb) { for (let value of thiz) { cb(value); } })").call(this,
            quackContext.coerceJavaToJavaScript(ValueCallback::class.java, cb))
}

class TorrentReadStreamOptions {
    @get:QuackProperty(name = "start")
    @set:QuackProperty(name = "start")
    var start: Int = 0
    @get:QuackProperty(name = "end")
    @set:QuackProperty(name = "end")
    var end: Int = 0
}


interface TorrentFile : QuackJavaScriptObject {
    fun select()
    fun deselect()
    fun createReadStream(torrentReadStreamOptions: TorrentReadStreamOptions): Stream
    fun createReadStream(): Stream

    @get:QuackProperty(name = "name")
    val name: String

    @get:QuackProperty(name = "length")
    val length: Int
}

interface TorrentCallback {
    operator fun invoke(torrent: Torrent)
}

interface ValueCallback {
    operator fun invoke(value: Any?)
}

