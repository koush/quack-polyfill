package com.koushikdutta.quack.polyfill.xmlhttprequest

import com.koushikdutta.quack.JavaScriptObject
import com.koushikdutta.quack.QuackContext
import com.koushikdutta.quack.QuackObject
import com.koushikdutta.quack.QuackProperty
import com.koushikdutta.scratch.async.async
import com.koushikdutta.scratch.buffers.ByteBufferList
import com.koushikdutta.scratch.event.AsyncServerRunnable
import com.koushikdutta.scratch.event.invoke
import com.koushikdutta.scratch.http.AsyncHttpRequest
import com.koushikdutta.scratch.http.Headers
import com.koushikdutta.scratch.http.body.Utf8StringBody
import com.koushikdutta.scratch.http.client.AsyncHttpClient
import com.koushikdutta.scratch.http.client.buildUpon
import com.koushikdutta.scratch.http.client.execute
import com.koushikdutta.scratch.http.client.followRedirects
import com.koushikdutta.scratch.parser.readAllBuffer
import com.koushikdutta.scratch.parser.readAllString
import com.koushikdutta.scratch.uri.URI
import java.nio.ByteBuffer

private fun safeRun(runnable: AsyncServerRunnable) {
    try {
        runnable()
    }
    catch (throwable: Throwable) {
        println(throwable)
    }
}

interface ErrorCallback {
    fun onError(error: JavaScriptObject)
}

class XMLHttpRequest(private val constructor: XMLHttpRequestConstructor, val context: QuackContext, val client: AsyncHttpClient) {
    @get:QuackProperty(name = "readyState")
    var readyState = 0
        private set

    @get:QuackProperty(name = "response")
    var response: Any? = null
        private set

    @get:QuackProperty(name = "responseText")
    var responseText: String? = null
        private set

    @get:QuackProperty(name = "responseType")
    @set:QuackProperty(name = "responseType")
    var responseType = "text"
        set(value) {
            if (value == "text" || value == "arraybuffer" || value == "json" || value == "moz-chunked-arraybuffer")
                field = value
        }

    @get:QuackProperty(name = "status")
    var status = 0
        private set

    @get:QuackProperty(name = "statusText")
    var statusText: String? = null
        private set

    @get:QuackProperty(name = "responseURL")
    var responseURL: String? = null
        private set

    @get:QuackProperty(name = "timeout")
    @set:QuackProperty(name = "timeout")
    var timeout = 0

    @get:QuackProperty(name = "onerror")
    @set:QuackProperty(name = "onerror")
    var onError: ErrorCallback? = null

    @get:QuackProperty(name = "onreadystatechange")
    @set:QuackProperty(name = "onreadystatechange")
    var onReadyStateChanged: Runnable? = null

    @get:QuackProperty(name = "onabort")
    @set:QuackProperty(name = "onabort")
    var onAbort: Runnable? = null

    @get:QuackProperty(name = "ontimeout")
    @set:QuackProperty(name = "ontimeout")
    var onTimeout: Runnable? = null

    @get:QuackProperty(name = "onprogress")
    @set:QuackProperty(name = "onprogress")
    var onProgress: Runnable? = null

    private val headers = Headers()
    fun setRequestHeader(key: String, value: String) {
        headers[key] = value
    }

    private var responseHeaders = Headers()

    fun send(requestData: Any?) {
        val body = if (requestData != null)
            Utf8StringBody(requestData.toString())
        else
            null
        val request = AsyncHttpRequest(URI.create(url!!), method!!, headers = headers, body = body)
        client.eventLoop.async {
            try {
                constructor.openRequests++
                val httpResponse = client.buildUpon().followRedirects().build()(request)
                responseHeaders = httpResponse.headers

                status = httpResponse.code
                statusText = httpResponse.message
                responseURL = request.uri.toString()

                if (responseType == "moz-chunked-arraybuffer") {
                    val buffer = ByteBufferList()

                    readyState = 3
                    while (httpResponse.body != null && httpResponse.body!!(buffer)) {
                        notifyProgress(buffer.readDirectByteBuffer())
                    }

                    response = ByteBuffer.allocate(0)
                    readyState = 4
                    notifyReadyStateChanged()
                }
                else {
                    if (responseType == "text")
                        responseText = readAllString(httpResponse.body!!)
                    else if (responseType == "json")
                        response = makeJson(readAllString(httpResponse.body!!))
                    else if (responseType == "arraybuffer")
                        response = readAllBuffer(httpResponse.body!!).readDirectByteBuffer()

                    readyState = 4
                    notifyReadyStateChanged()
                }
            }
            catch (e: Exception) {
                readyState = 4
                notifyError(e)
            }
            finally {
                constructor.openRequests--
                onError = null
                onReadyStateChanged = null
                onProgress = null
                onTimeout = null
            }
        }
    }

    fun getAllResponseHeaders(): String {
        val builder = StringBuilder()
        for (header in responseHeaders) {
            builder.append("${header.name}: ${header.value}\r\n")
        }
        builder.append("\r\n")
        return builder.toString()
    }

    private var method: String? = null
    private var url: String? = null

    fun abort() {
        onAbort?.run()
    }

    fun open(method: String, url: String, async: Boolean?, password: String?) {
        this.method = method;
        this.url = url
        readyState = 1
        notifyReadyStateChanged()
    }

    private fun notifyReadyStateChanged() = safeRun {
        onReadyStateChanged?.run()
    }


    private fun makeJson(json: String): JavaScriptObject {
        return context.evaluateForJavaScriptObject("(function(json) { return JSON.parse(json); })").call(json) as JavaScriptObject
    }
    private fun notifyError(exception: Exception) = safeRun {
        onError?.onError(context.newError(exception))
    }

    private fun notifyProgress(buffer: ByteBuffer) = safeRun {
        try {
            this.response = buffer
            onProgress?.run()
        }
        finally {
            this.response = null
        }
    }

    class XMLHttpRequestConstructor(val context: QuackContext, val client: AsyncHttpClient) : QuackObject {
        var openRequests = 0
        override fun construct(vararg args: Any): Any {
            return XMLHttpRequest(this, context, client)
        }
    }
}