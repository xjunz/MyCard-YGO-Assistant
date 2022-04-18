package xjunz.tool.mycard.ktx

import android.content.res.ColorStateList
import androidx.annotation.ArrayRes
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import xjunz.tool.mycard.app

/**
 * @author xjunz 2022/2/24
 */
inline val @receiver:StringRes Int.resText get() = app.getText(this)

inline val @receiver:StringRes Int.resStr get() = app.getString(this)

inline val @receiver:ArrayRes Int.resArray: Array<String>
    get() = app.resources.getStringArray(this)

@get:ColorInt
inline val @receiver:ColorRes Int.resColor
    get() = app.getColor(this)

inline val @receiver:ColorInt Int.asStateList get() = ColorStateList.valueOf(this)

fun @receiver:StringRes Int.format(vararg any: Any?) = resStr.format(*any)

inline val Number.dp get() = (app.resources.displayMetrics.density * toFloat()).pxSize

inline val Number.dpFloat get() = app.resources.displayMetrics.density * toFloat()

inline val Float.pxSize get() = (if (this >= 0) this + .5f else this - .5f).toInt()