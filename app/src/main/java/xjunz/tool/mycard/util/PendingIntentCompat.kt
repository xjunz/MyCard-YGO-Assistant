package xjunz.tool.mycard.util

import android.app.PendingIntent
import android.os.Build

/**
 * @author xjunz 2022/3/14
 */
object PendingIntentCompat {

    /**
     * Automatically add [PendingIntent.FLAG_MUTABLE] to [flags] not containing [PendingIntent.FLAG_IMMUTABLE]
     * on Android S and above.
     */
    fun makeFlags(flags: Int): Int {
        if (flags and PendingIntent.FLAG_IMMUTABLE == 0
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            return flags or PendingIntent.FLAG_MUTABLE
        }
        return flags
    }
}