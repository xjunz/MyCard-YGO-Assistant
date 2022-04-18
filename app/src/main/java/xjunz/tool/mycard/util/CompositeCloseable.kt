package xjunz.tool.mycard.util

import android.util.ArraySet
import androidx.lifecycle.Lifecycle
import java.io.Closeable

/**
 * A wrapper of a set of [closeable][Closeable]s, providing an interface to close all. Use [lazilyCompose]
 * to load and compose a closeable lazily.
 *
 * @author xjunz 2022/3/2
 */
class CompositeCloseable(lifecycle: Lifecycle? = null) : LifecyclePerceptiveCloseable(lifecycle) {

    fun <T : Closeable> compose(closeable: T): T {
        closeables.add(closeable)
        return closeable
    }

    /**
     * Compose any target with a custom [closing][Closeable.close] [block].
     */
    inline fun <T> composeAny(t: T, crossinline block: T.() -> Unit): T {
        compose(Closeable { block.invoke(t) })
        return t
    }

    inline fun <T : Closeable> lazilyCompose(crossinline initializer: () -> T) = lazy {
        compose(initializer.invoke())
    }

    private val closeables by lazy {
        ArraySet<Closeable>(2)
    }

    override fun close() {
        closeables.forEach {
            it.close()
        }
    }
}