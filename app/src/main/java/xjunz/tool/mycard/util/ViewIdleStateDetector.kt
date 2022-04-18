package xjunz.tool.mycard.util

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import kotlin.math.abs

object ViewIdleStateDetector {

    private const val FAST_SCROLL_CHECK_PERIOD = 66L
    private const val FAST_SCROLL_MIN_DISTANCE = 0.12F * FAST_SCROLL_CHECK_PERIOD

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            val callback = msg.obj as ViewIdleStateDetector.Callback
            if (callback.shouldStop()) return
            val currentTop = callback.target.top
            // being fast scrolled, keep on
            if (abs(msg.arg1 - currentTop) >= FAST_SCROLL_MIN_DISTANCE) {
                val next = Message.obtain(msg)
                next.arg1 = currentTop
                sendMessageDelayed(next, FAST_SCROLL_CHECK_PERIOD)
            }
            // idle state detected
            else callback.onIdle()
        }
    }

    /**
     * Cancel an on-going detection with specific [id]. Do nothing if there is no such [id].
     */
    fun cancel(id: Int) {
        handler.removeMessages(id)
    }

    interface Callback {
        /**
         * The target view to be detected.
         */
        val target: View

        /**
         * Called when a idle state is detected.
         */
        fun onIdle()

        /**
         * Tell whether the detector should stop even when a idle state is not yet detected.
         */
        fun shouldStop(): Boolean
    }

    /**
     * Start a detection with a specific [id] and a [callback]. Make sure the [target][Callback.target]
     * is laid out at the moment.
     */
    fun detect(id: Int, callback: Callback) {
        cancel(id)
        if (!callback.target.isLaidOut) return
        val msg = Message.obtain()
        msg.what = id
        msg.arg1 = callback.target.top
        msg.obj = callback
        handler.sendMessageDelayed(msg, FAST_SCROLL_CHECK_PERIOD)
    }

    fun clearAll() {
        handler.removeCallbacksAndMessages(null)
    }

}