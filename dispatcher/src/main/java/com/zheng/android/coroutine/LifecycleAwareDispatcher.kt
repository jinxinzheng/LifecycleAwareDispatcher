package com.zheng.android.coroutine

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.OnLifecycleEvent
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Delay
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.MainCoroutineDispatcher
import java.lang.Runnable
import java.lang.reflect.Constructor
import kotlin.coroutines.CoroutineContext

/**
 * @author zheng on 2/27/19
 */

/**
 * copy from [kotlinx.coroutines.android.asHandler]
 */
fun Looper.asHandler(async: Boolean): Handler {
    // Async support was added in API 16.
    if (!async || Build.VERSION.SDK_INT < 16) {
        return Handler(this)
    }

    if (Build.VERSION.SDK_INT >= 28) {
        return Handler.createAsync(this)
    }

    val constructor: Constructor<Handler>
    try {
        constructor = Handler::class.java.getDeclaredConstructor(Looper::class.java,
            Handler.Callback::class.java, Boolean::class.javaPrimitiveType)
    } catch (ignored: NoSuchMethodException) {
        // Hidden constructor absent. Fall back to non-async constructor.
        return Handler(this)
    }
    return constructor.newInstance(this, null, true)
}

/**
 * Creates a LifecycleAwareDispatcher.
 *
 * Example:
 * in fragment/activity
 * ```
 * launch(lifecycleAwareDisaptcher()) {
 *   ...
 * }
 * ```
 */
fun LifecycleOwner.lifecycleAwareDispatcher(
    handler: Handler = Looper.getMainLooper().asHandler(true)
): MainCoroutineDispatcher = LifecycleAwareDispatcher(this, handler)

/**
 * Lifecycle aware dispatcher. It is copied from the [kotlinx.coroutines.android.HandlerContext] and adds lifecycle
 * event handling, and only supports dispatching using a handler.
 *
 * When the coroutine resumes, it will not dispatch until the state of the lifecycle owner is at least
 * [Lifecycle.State.STARTED]. This is useful for coroutines running on Android main thread, where it is ensured that
 * the coroutine does not resume in a stopped lifecycle state.
 */
@UseExperimental(InternalCoroutinesApi::class)
class LifecycleAwareDispatcher(
    private val owner: LifecycleOwner,
    private val handler: Handler
) : MainCoroutineDispatcher(), Delay {

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            handler.post(block)
        } else {
            owner.lifecycle.addObserver(object : LifecycleObserver {
                @OnLifecycleEvent(Lifecycle.Event.ON_START)
                fun onStarted() {
                    owner.lifecycle.removeObserver(this)
                    handler.post(block)
                }
            })
        }
    }

    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        val block = Runnable {
            resumeAfterDelay(continuation)
        }
        handler.postDelayed(block, timeMillis.coerceAtMost(MAX_DELAY))
        continuation.invokeOnCancellation { handler.removeCallbacks(block) }
    }

    @UseExperimental(ExperimentalCoroutinesApi::class)
    private fun resumeAfterDelay(continuation: CancellableContinuation<Unit>) {
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            with(continuation) { resumeUndispatched(Unit) }
        } else {
            owner.lifecycle.addObserver(object : LifecycleObserver {
                @OnLifecycleEvent(Lifecycle.Event.ON_START)
                fun onStarted() {
                    owner.lifecycle.removeObserver(this)
                    handler.post {
                        with(continuation) { resumeUndispatched(Unit) }
                    }
                }
            })
        }
    }

    override fun invokeOnTimeout(timeMillis: Long, block: Runnable): DisposableHandle {
        handler.postDelayed(block, timeMillis.coerceAtMost(MAX_DELAY))
        return object : DisposableHandle {
            override fun dispose() {
                handler.removeCallbacks(block)
            }
        }
    }

    @ExperimentalCoroutinesApi
    override val immediate: MainCoroutineDispatcher
        get() = throw UnsupportedOperationException()

    companion object {

        private const val MAX_DELAY = Long.MAX_VALUE / 2 // cannot delay for too long on Android
    }
}
