package com.koushikdutta.quack.polyfill.job

import com.koushikdutta.quack.JavaScriptObject
import com.koushikdutta.quack.polyfill.QuackEventLoop
import com.koushikdutta.quack.polyfill.callSafely
import com.koushikdutta.scratch.event.Cancellable

fun QuackEventLoop.installJobScheduler() {
    val script = """
        (function() {
            const timeouts = {};

            function installTimeouts(global, schedule, cancel) {
                var timeoutCount = 1;

                function createTimeoutCallback(cb, args) {
                    return function() {
                        cb.apply(null, args);
                    }
                }

                global.setTimeout = function setTimeout(cb, delay) {
                    const timeout = timeoutCount++;
                    schedule(timeout, delay || 0);
                    const args = Array.prototype.slice.call(arguments, 0, 2);
                    timeouts[timeout] = createTimeoutCallback(cb, args);
                    return timeout;
                }

                global.setImmediate = function setImmediate(cb) {
                    const timeout = timeoutCount++;
                    schedule(timeout, 0);
                    const args = Array.prototype.slice.call(arguments, 0, 1);
                    timeouts[timeout] = createTimeoutCallback(cb, args);
                    return timeout;
                }

                global.setInterval = function setInterval(cb, interval) {
                    const timeout = timeoutCount++;
                    const args = Array.prototype.slice.call(arguments, 0, 2);
                    const callback = createTimeoutCallback(cb, args);

                    function intervalCallback() {
                        reschedule();
                        callback();
                    }
                    function reschedule() {
                        schedule(timeout, interval || 0);
                        timeouts[timeout] = intervalCallback;
                    }

                    reschedule();
                    return timeout;
                }

                var clearQueue = [];
                var clearTimeoutId;
                // clearTimeout, et al, may be called spuriosly, so throttle the invocations.
                function clear(timeout) {
                    delete timeouts[timeout];
                    if (!clearTimeoutId) {
                        clearTimeoutId = setTimeout(function() {
                            clearTimeoutId = undefined;
                            var cq = clearQueue;
                            clearQueue = [];
                            cancel.apply(null, cq);
                        }, 1000);
                    }
                    clearQueue.push(timeout);
                }

                global.clearTimeout = global.clearImmediate = global.clearInterval = clear;
            }

            function timeoutCallback(timeout) {
                const callback = timeouts[timeout];
                delete timeouts[timeout];
                if (callback)
                    callback();
            }

            return {
                installTimeouts,
                timeoutCallback,
            };
        })
    """.trimIndent()

    val self = this
    val loop = loop
    val ctx = quack
    val global = ctx.globalObject

    val module = ctx.evaluateForJavaScriptObject(script).call() as JavaScriptObject
    val installTimeouts = ctx.coerceJavaScriptToJava(InstallTimeouts::class.java, module.get("installTimeouts") as JavaScriptObject) as InstallTimeouts
    val timeoutCallback = (module.get("timeoutCallback") as JavaScriptObject)

    val timeouts = mutableMapOf<Any, Cancellable>()

    val clear = object : ClearTimeouts {
        override fun invoke(vararg clearQueue: Any?) {
            for (timeout in clearQueue) {
                val cancel = timeouts.remove(timeout)
                if (cancel != null)
                    cancel.cancel()
            }
        }
    }

    installTimeouts(global, object : ScheduleTimeout {
        override fun invoke(timeout: Int, delay: Int) {
            clear(timeout)
            val cancel: Cancellable
            if (delay <= 0)
                cancel = loop.post { timeoutCallback.callSafely(self, timeout) }
            else
                cancel = loop.postDelayed(delay.toLong()) { timeoutCallback.callSafely(self, timeout) }
            timeouts[timeout] = cancel
        }
    }, clear)

    ctx.setJobExecutor { runnable: Runnable ->
        loop.post {
            runnable.run()
        }
    }
}

interface ScheduleTimeout {
    operator fun invoke(timeout: Int, delay: Int)
}

interface ClearTimeouts {
    operator fun invoke(vararg clearQueue: Any?)
}

interface InstallTimeouts {
    operator fun invoke(global: JavaScriptObject, schedule: ScheduleTimeout, clear: ClearTimeouts)
}