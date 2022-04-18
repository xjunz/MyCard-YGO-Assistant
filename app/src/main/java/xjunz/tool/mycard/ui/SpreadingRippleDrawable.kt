package xjunz.tool.mycard.ui

import android.animation.ValueAnimator
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator

/**
 * A drawable with a effect of spreading ripples, which is basically used as a background for an
 * interactive component, like buttons, indicating that this target requires the user's attention.
 */
class SpreadingRippleDrawable : Drawable() {

    var rippleSpreadDuration = 3_600L

    /**
     * The total count of ripples to draw.
     */
    var rippleCount = 3

    var rippleColor = Color.RED
        set(value) {
            paint.color = value
            field = value
        }

    /**
     * The inset applied to the drawing bounds, positive value to shrink and negative value to expand,
     * may not expand exceeding its parent bounds.
     */
    var inset = 0

    /**
     * The radius of the preserved circle in center of ripple that will keep drawing a solid
     * [ripple color][rippleColor], from which the ripple will start spreading.
     */
    var rippleCenterRadius = 0

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = rippleColor
        style = Paint.Style.FILL
    }

    private var maxRadius = 0F

    private val rippleFractions = FloatArray(rippleCount)

    private val animator = ValueAnimator.ofFloat(0F, 1F).apply {
        var prev = 0F
        addUpdateListener {
            val f = it.animatedFraction
            val delta = if (f < prev) 1 - prev else f - prev
            for (i in rippleFractions.indices) {
                rippleFractions[i] = (rippleFractions[i] + delta) % 1
            }
            prev = f
            if (fadeMultiplier > 0) {
                fadeMultiplier = (fadeMultiplier - delta).coerceAtLeast(0F)
                // when it is totally faded out, cancel the animator
                if (fadeMultiplier == 0F) cancel()
            }
            invalidateSelf()
        }
        interpolator = LinearInterpolator()
        duration = rippleSpreadDuration
        repeatMode = ValueAnimator.RESTART
        repeatCount = ValueAnimator.INFINITE
    }

    private val drawingRect = RectF()

    override fun draw(canvas: Canvas) {
        val drawableRadius = maxRadius - rippleCenterRadius
        var hasDrawn = false
        for (i in 0..rippleCount) {
            val f = if (i == rippleCount) 0F else rippleFractions[i]
            if (f >= 0) {
                if (f == 0F && i != rippleCount) continue
                if (i == rippleCount && !hasDrawn) return
                drawingRect.left = (1 - f) * drawableRadius
                drawingRect.right = maxRadius + rippleCenterRadius + f * drawableRadius
                drawingRect.top = drawingRect.left
                drawingRect.bottom = drawingRect.right
                if (f == 0F) paint.alpha = 0xFF
                else paint.alpha = ((1 - f) * initialAlpha).toInt()
                if (fadeMultiplier >= 0) paint.alpha = (paint.alpha * fadeMultiplier).toInt()
                drawingRect.offset(inset.toFloat(), inset.toFloat())
                canvas.drawOval(drawingRect, paint)
                hasDrawn = true
            }
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        maxRadius = bounds.width().coerceAtMost(bounds.height()) / 2F - inset
    }

    override fun isStateful() = false

    private var initialAlpha = 0xFF

    override fun setAlpha(alpha: Int) {
        initialAlpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated(
        "Deprecated in Java",
        ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat")
    )
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    fun start() {
        if (!animator.isStarted) {
            for (i in rippleFractions.indices) {
                rippleFractions[i] = -i.toFloat() / rippleCount
            }
            fadeMultiplier = -1F
            animator.start()
        }
    }

    fun pause() {
        animator.pause()
    }

    fun resume() {
        animator.resume()
    }

    fun clear() {
        if (animator.isStarted) {
            animator.cancel()
            for (i in rippleFractions.indices) {
                rippleFractions[i] = 0F
            }
            invalidateSelf()
        }
    }

    private var fadeMultiplier = -1F

    fun fadeOut() {
        fadeMultiplier = 1F
    }
}