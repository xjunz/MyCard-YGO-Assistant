package xjunz.tool.mycard.ktx

import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.RecyclerView

fun RecyclerView.scrollPositionToCenter(position: Int, animate: Boolean) {
    val itemView = findViewHolderForAdapterPosition(position)?.itemView
    if (itemView == null) {
        if (animate) {
            smoothScrollToPosition(position)
        } else {
            scrollToPosition(position)
        }
        return
    }
    val delta = itemView.left - (width / 2 - itemView.width / 2)
    if (animate) {
        smoothScrollBy(delta, 0, FastOutSlowInInterpolator())
    } else {
        scrollBy(delta, 0)
    }
}