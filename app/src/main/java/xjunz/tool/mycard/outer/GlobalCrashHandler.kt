package xjunz.tool.mycard.outer

import android.app.ApplicationErrorReport.CrashInfo
import android.util.PrintStreamPrinter
import xjunz.tool.mycard.App
import xjunz.tool.mycard.app
import xjunz.tool.mycard.ktx.formatToDate
import xjunz.tool.mycard.ktx.launchActivity
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import kotlin.system.exitProcess

/**
 * @author xjunz 2022/3/16
 */
object GlobalCrashHandler : Thread.UncaughtExceptionHandler {

    fun init() {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    private const val CRASH_DIR_PATH = "crash"

    override fun uncaughtException(t: Thread, e: Throwable) {
        e.printStackTrace()
        val date: String = System.currentTimeMillis().formatToDate()
        val crashInfoDir = File(app.cacheDir, CRASH_DIR_PATH)
        val crashInfoFile = File(crashInfoDir, "$date.txt")
        if ((crashInfoDir.exists() || crashInfoDir.mkdirs()) && crashInfoFile.createNewFile()) {
            FileOutputStream(crashInfoFile, true).use {
                val printer = PrintStreamPrinter(PrintStream(it, true))
                printer.println(date)
                App.dumpBasicEnvInfo(printer)
                CrashInfo(e).dump(printer, "")
            }
            app.launchActivity(CrashReportActivity::class.java) {
                putExtra(CrashReportActivity.EXTRA_LOG_FILE_PATH, crashInfoFile.path)
            }
        }
        exitProcess(1)
    }
}