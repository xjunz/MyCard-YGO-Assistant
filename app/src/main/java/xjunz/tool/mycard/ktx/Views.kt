package xjunz.tool.mycard.ktx

import android.animation.ObjectAnimator
import android.animation.TimeInterpolator
import android.graphics.drawable.Drawable
import android.text.InputFilter
import android.transition.ChangeBounds
import android.transition.Transition
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.ArrayRes
import androidx.appcompat.widget.TooltipCompat
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import xjunz.tool.mycard.util.Motions

/**
 * @author xjunz 2022/2/10
 */
inline fun <T : View> T.applySystemInsets(crossinline block: (v: T, insets: Insets) -> Unit) {
    setOnApplyWindowInsetsListener { _, windowInsets ->
        val sysInsets = WindowInsetsCompat.toWindowInsetsCompat(windowInsets)
        block(this, sysInsets.getInsets(WindowInsetsCompat.Type.systemBars()))
        return@setOnApplyWindowInsetsListener windowInsets
    }
}

inline val EditText.textString get() = text.toString()

fun EditText.setMaxLength(max: Int) {
    filters += InputFilter.LengthFilter(max)
}

fun TextView.setDrawables(
    start: Drawable? = null,
    top: Drawable? = null,
    end: Drawable? = null,
    bott: Drawable? = null
) {
    setCompoundDrawablesRelativeWithIntrinsicBounds(start, top, end, bott)
}

/**
 *  Set the tooltip as well as the content description.
 */
fun View.setTooltipCompat(tooltip: CharSequence) {
    TooltipCompat.setTooltipText(this, tooltip)
    contentDescription = tooltip
}


fun AutoCompleteTextView.setEntries(
    @ArrayRes strArrayRes: Int,
    setFirstAsText: Boolean = false,
    onItemClicked: ((position: Int) -> Unit)? = null
) {
    setEntries(strArrayRes.resArray.toList(), setFirstAsText, onItemClicked)
}

fun <T : Any> AutoCompleteTextView.setEntries(
    array: Array<T>,
    setFirstAsText: Boolean = false,
    onItemClicked: ((position: Int) -> Unit)? = null
) {
    setEntries(array.toList(), setFirstAsText, onItemClicked)
}

fun AutoCompleteTextView.setEntries(
    data: List<Any>,
    setFirstAsText: Boolean = false,
    onItemClicked: ((position: Int) -> Unit)? = null
) {
    setAdapter(
        ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, data)
    )
    if (onItemClicked != null) {
        setOnItemClickListener { _, _, position, _ ->
            onItemClicked(position)
        }
    }
    if (setFirstAsText) {
        threshold = Int.MAX_VALUE
        setText(data[0].toString())
    }
}

fun View.beginDelayedTransition(
    transition: Transition = ChangeBounds(),
    interpolator: TimeInterpolator = Motions.EASING_EMPHASIZED,
    target: View? = null
) {
    this as ViewGroup
    if (target != null) {
        transition.addTarget(target)
    }
    TransitionManager.beginDelayedTransition(
        this, transition.setInterpolator(interpolator)
    )
}

fun View.shake() {
    ObjectAnimator.ofFloat(
        this, View.TRANSLATION_X,
        0F, 20F, -20F, 15F, -15F, 10F, -10F, 5F, -5F, 0F
    ).start()
}