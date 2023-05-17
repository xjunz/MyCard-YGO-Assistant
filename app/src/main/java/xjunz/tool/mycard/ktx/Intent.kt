package xjunz.tool.mycard.ktx

import android.content.Intent
import android.os.Build

/**
 * @author xjunz 2023/05/08
 */
@Suppress("DEPRECATION")
inline fun <reified T : java.io.Serializable> Intent.requireSerializableExtra(extra: String): T {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getSerializableExtra(extra, T::class.java)!!
    } else {
        getSerializableExtra(extra) as T
    }
}