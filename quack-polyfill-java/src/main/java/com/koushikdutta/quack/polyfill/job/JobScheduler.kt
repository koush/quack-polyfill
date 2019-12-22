package com.koushikdutta.quack.polyfill.job

import com.koushikdutta.quack.JavaScriptObject
import com.koushikdutta.quack.polyfill.QuackEventLoop
import com.koushikdutta.quack.polyfill.callSafely
import com.koushikdutta.scratch.event.Cancellable

fun QuackEventLoop.installJobScheduler() {
    val script = """
        (function() {
            const timeouts = {};

            function installTimeouts(global, scheduleTimeouts, cancel) {
                var timeoutCount = 1;

                var scheduleQueue = [];
                var delayQueue = [];
                var scheduleTimeoutId;

                function schedule(timeout, delay) {
                    if (typeof delay !== 'number')
                        delay = 0;
                    scheduleQueue.push(timeout);
                    delayQueue.push(delay);

                    if (scheduleTimeoutId)
                        return;

                    scheduleTimeoutId = timeoutCount++;
                    timeouts[scheduleTimeoutId] = createTimeoutCallback(function() {
                        scheduleTimeoutId = undefined;
                        var sq = scheduleQueue;
                        scheduleQueue = [];
                        var dq = delayQueue;
                        delayQueue = [];
                        scheduleTimeouts(sq, dq);
                    });
                    scheduleTimeouts([scheduleTimeoutId], [0]);
                }

                function createTimeoutCallback(cb, args) {
                    return function() {
                        cb.apply(null, args);
                    }
                }

                global.setTimeout = function setTimeout(cb, delay) {
                    const timeout = timeoutCount++;
                    schedule(timeout, delay);
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
                        schedule(timeout, interval);
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

    val scheduled = mutableMapOf<Any, Cancellable>()

    val clear = object : ClearTimeouts {
        override fun invoke(vararg clearQueue: Any?) {
            for (timeout in clearQueue) {
                val cancel = scheduled.remove(timeout)
                if (cancel != null)
                    cancel.cancel()
            }
        }
    }

    installTimeouts(global, object : ScheduleTimeouts {
        override fun invoke(timeouts: IntArray, delays: IntArray) {
            for (i in timeouts.indices) {
                val timeout = timeouts[i]
                val delay = delays[i];

                println(delay)
                clear(timeout)
                val cancel: Cancellable
                if (delay <= 0)
                    cancel = loop.post { timeoutCallback.callSafely(self, timeout) }
                else
                    cancel = loop.postDelayed(delay.toLong()) { timeoutCallback.callSafely(self, timeout) }
                scheduled[timeout] = cancel
            }
        }
    }, clear)

    ctx.setJobExecutor { runnable: Runnable ->
        loop.post {
            runnable.run()
        }
    }
}

interface ScheduleTimeouts {
    operator fun invoke(timeouts: IntArray, delays: IntArray)
}

interface ClearTimeouts {
    operator fun invoke(vararg clearQueue: Any?)
}

interface InstallTimeouts {
    operator fun invoke(global: JavaScriptObject, schedule: ScheduleTimeouts, clear: ClearTimeouts)
}