package xjunz.tool.mycard.ui

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.forEach
import androidx.core.view.isVisible
import com.google.android.material.R
import com.google.android.material.shape.MaterialShapeDrawable
import xjunz.tool.mycard.ktx.asStateList
import xjunz.tool.mycard.ktx.dp
import xjunz.tool.mycard.ktx.dpFloat
import xjunz.tool.mycard.ktx.resColor
import xjunz.tool.mycard.ktx.resolveAttribute

/**
 *  A simple implementation of a flow-like layout, which stacks its child horizontally util a child
 *  is about to exceed its width and layouts this child to the start of the next 'floor' and so on.
 *  This implementation is somehow rough, which needs more improvements and compatibility work.
 */
class FlowLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0, defStyleRes: Int = 0
) : ViewGroup(context, attrs, defStyleAttr, defStyleRes) {

    private companion object {
        val CHILD_MARGIN_END = 4.dp
        val CHILD_MARGIN_TOP = 2.dp
        val CHILD_PADDING_HORIZONTAL = 8.dp
        val CHILD_PADDING_VERTICAL = 2.dp
    }

    private var childHeight = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        measureChildren(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        var remaining = width
        var floor = 0
        forEach {
            //TODO: Deal with different child heights
            if (floor == 0 && it.isVisible) {
                childHeight = it.measuredHeight
                floor = 1
            }
            val childWidth = if (it.isVisible) it.measuredWidth else 0
            //TODO: What if even the first child's width is larger than the measured width?
            if (childWidth > remaining) {
                remaining = width
                floor++
            }
            remaining -= childWidth + CHILD_MARGIN_END
        }
        setMeasuredDimension(
            getDefaultSize(width, widthMeasureSpec),
            getDefaultSize(
                floor * childHeight + (floor - 1).coerceAtLeast(0) * CHILD_MARGIN_TOP,
                heightMeasureSpec
            )
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var remaining = r - l
        var floor = 0
        forEach {
            if (!it.isVisible) return@forEach
            if (it.measuredWidth > remaining) {
                remaining = r - l
                floor++
            }
            val left = r - l - remaining
            val top = floor * (childHeight + CHILD_MARGIN_TOP)
            it.layout(left, top, left + it.measuredWidth, top + childHeight)
            remaining -= it.measuredWidth + CHILD_MARGIN_END
        }
    }

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams {
        return MarginLayoutParams(context, attrs)
    }

    private val primaryColor by lazy {
        ColorUtils.setAlphaComponent(
            context.resolveAttribute(R.attr.colorPrimary).resColor, (.8 * 0xFF).toInt()
        ).asStateList
    }

    private val textBackground by lazy {
        MaterialShapeDrawable().apply {
            fillColor = primaryColor
            setCornerSize(16.dpFloat)
        }
    }

    private fun generateView(): TextView {
        val view = TextView(context)
        view.setTextAppearance(R.style.TextAppearance_Material3_LabelSmall)
        view.setPadding(
            CHILD_PADDING_HORIZONTAL, CHILD_PADDING_VERTICAL,
            CHILD_PADDING_HORIZONTAL, CHILD_PADDING_VERTICAL
        )
        view.background = textBackground.mutate()
        view.setTextColor(context.resolveAttribute(R.attr.colorOnPrimary).resColor)
        return view
    }

    /**
     * Stack a [TextView] into the layout.
     *
     * @param index the index of the view, the default value is 0.
     */
    fun addText(index: Int = 0): TextView {
        return generateView().also { addView(it, index) }
    }

}