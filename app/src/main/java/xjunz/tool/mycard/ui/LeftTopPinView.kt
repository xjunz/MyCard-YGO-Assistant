package xjunz.tool.mycard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.google.android.material.R
import xjunz.tool.mycard.ktx.dpFloat
import xjunz.tool.mycard.ktx.getDrawableCompat
import xjunz.tool.mycard.ktx.resColor
import xjunz.tool.mycard.ktx.resolveAttribute
import kotlin.math.sqrt

/**
 * @author xjunz 2022/3/21
 */
class LeftTopPinView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0, defStyleRes: Int = 0
) : View(context, attrs, defStyleAttr, defStyleRes) {

    companion object {
        val LEFT_TOP_CORNER = 16.dpFloat
    }

    private val backColor by lazy {
        ColorUtils.compositeColors(
            ColorUtils.setAlphaComponent(
                context.resolveAttribute(R.attr.colorPrimary).resColor,
                (.92 * 0xFF).toInt()
            ),
            context.resolveAttribute(R.attr.colorSurface).resColor
        )
    }

    private val paint by lazy {
        Paint().apply {
            isAntiAlias = true
            color = backColor
            style = Paint.Style.FILL
        }
    }

    private val path by lazy {
        Path().apply {
            addArc(
                0F, 0F, LEFT_TOP_CORNER * 2, LEFT_TOP_CORNER * 2,
                180F, 90F
            )
            lineTo(width.toFloat(), 0F)
            addArc(
                -width.toFloat(), -height.toFloat(), width.toFloat(), height.toFloat(),
                0F, 90F
            )
            lineTo(0F, LEFT_TOP_CORNER)
        }
    }

    private val pinDrawable by lazy {
        context.getDrawableCompat(xjunz.tool.mycard.R.drawable.ic_baseline_push_pin_24)!!.also {
            val drawingWidth = (width / sqrt(2F)).toInt()
            val drawableWidth = (drawingWidth * .85).toInt()
            val l = (drawingWidth - drawableWidth) / 2
            val r = l + drawableWidth
            it.setBounds(l, l, r, r)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(path, paint)
        canvas.save()
        canvas.translate(3.dpFloat, 4.dpFloat)
        canvas.rotate(45F, width / 2 / sqrt(2F), height / 2 / sqrt(2F))
        pinDrawable.draw(canvas)
    }

}