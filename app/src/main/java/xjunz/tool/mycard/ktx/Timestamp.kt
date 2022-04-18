package xjunz.tool.mycard.ktx

import xjunz.tool.mycard.R
import java.text.SimpleDateFormat
import java.util.*

/**
 * @author xjunz 2022/3/3
 */
fun Long.formatToDate(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(this))
}

fun Long.formatDurationMinSec(): String {
    val min = this / 60_000
    val second = this % 60_000 / 1_000
    if (min == 0L) return R.string.format_sec.format(second)
    return R.string.format_min_sec.format(min, second)
}