package com.koushikdutta.quack.polyfill.os

import com.koushikdutta.quack.polyfill.QuackEventLoop
import com.koushikdutta.quack.polyfill.require.Modules

class OSModule(val quackLoop: QuackEventLoop, modules: Modules) {
    fun tmpdir(): String {
        return System.getProperty("java.io.tmpdir")
    }
}
