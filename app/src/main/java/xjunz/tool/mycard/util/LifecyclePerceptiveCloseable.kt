package xjunz.tool.mycard.util

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.io.Closeable

/**
 * A [Closeable], which would auto close itself when its host is destroyed.
 */
abstract class LifecyclePerceptiveCloseable(private val lifecycle: Lifecycle? = null) :
    AutoCloseable, Closeable {

    private val observer by lazy {
        object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_DESTROY) {
                    lifecycle?.removeObserver(this)
                    close()
                }
            }
        }
    }

    init {
        lifecycle?.addObserver(observer)
    }

}