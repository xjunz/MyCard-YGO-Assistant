package xjunz.tool.mycard

import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("xjunz.tool.mycard", appContext.packageName)
    }


    @Test
    fun checkRippleCascade() {
        Handler(Looper.getMainLooper()).post {
            val count = 3
            val rippleFractions = FloatArray(count) { -it.toFloat() / count }
            ValueAnimator.ofFloat(0F, 1F).apply {
                var lf = 0F
                addUpdateListener {
                    val f = it.animatedFraction
                    val df = f - lf
                    for (i in rippleFractions.indices) {
                        rippleFractions[i] = (rippleFractions[i] + df) % 1
                        Log.d("xjunz", "$i: ${rippleFractions[i]}")
                    }
                    lf = if (f == 1F) 0F else f
                }
                duration = 200
                repeatMode = ValueAnimator.RESTART
                repeatCount = ValueAnimator.INFINITE
            }.start()
        }
    }
}
