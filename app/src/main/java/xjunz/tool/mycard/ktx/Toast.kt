package xjunz.tool.mycard.ktx

import android.widget.Toast
import xjunz.tool.mycard.R
import xjunz.tool.mycard.app
import java.lang.ref.WeakReference

/**
 * @author xjunz 2022/3/1
 */
private var previousToast: WeakReference<Toast>? = null

fun toast(any: Any?, length: Int = Toast.LENGTH_SHORT) {
    previousToast?.get()?.cancel()
    if (any is Int) {
        Toast.makeText(app, any, length).also { previousToast = WeakReference(it) }.show()
    } else {
        Toast.makeText(app, any.toString(), length).also { previousToast = WeakReference(it) }.show()
    }
}

fun longToast(any: Any?) = toast(any, Toast.LENGTH_LONG)

fun errorToast(t: Throwable?) = longToast(R.string.format_unexpected_error.format(t?.message))