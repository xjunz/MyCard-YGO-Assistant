package xjunz.tool.mycard.util

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * @author xjunz 2023/05/23
 */
object TimeParser {

    private val parser: SimpleDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
    }

    private val formatter: SimpleDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }

    fun parseTime(raw: String): Long {
        return runCatching { parser.parse(raw)!!.time }.getOrDefault(-1)
    }

    fun reformatTime(raw: String): String? {
        val parsed = parseTime(raw)
        if (parsed == -1L) return null
        return formatter.format(parsed + 1000 * 60 * 60 * 8)
    }
}