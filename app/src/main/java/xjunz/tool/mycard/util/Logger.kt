package xjunz.tool.mycard.util

import android.util.Log

/**
 * @author xjunz 2022/2/23
 */
fun printLog(any: Any?, priority: Int = Log.INFO) {
    Log.println(priority, "xjunz-mycard-debug", any.toString())
}

fun errorLog(any: Any?) {
    printLog(any, Log.ERROR)
}

fun debugLog(any: Any?) {
    printLog(any, Log.DEBUG)
}