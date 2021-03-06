package xjunz.tool.mycard

import android.app.Application
import android.content.SharedPreferences
import android.os.Build
import android.util.Printer
import androidx.core.content.ContextCompat
import top.xjunz.returntransitionpatcher.ReturnTransitionPatcher
import xjunz.tool.mycard.info.PlayerInfoManager
import xjunz.tool.mycard.outer.GlobalCrashHandler
import java.io.File

/**
 * @author xjunz 2022/2/9
 */
lateinit var app: App private set

class App : Application() {

    companion object {
        fun dumpBasicEnvInfo(printer: Printer) = printer.run {
            println("versionCode: ${BuildConfig.VERSION_CODE}")
            println("versionName: ${BuildConfig.VERSION_NAME}")
            println("deviceName: ${Build.BRAND} ${Build.MODEL}")
            println("buildVersion: Android ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})")
        }
    }

    var hasUpdates: Boolean? = null

    inline val fileProviderAuthority get() = BuildConfig.APPLICATION_ID + ".file_provider"

    fun sharedPrefsOf(name: String): SharedPreferences {
        return getSharedPreferences(name, MODE_PRIVATE)
    }

    override fun onCreate() {
        super.onCreate()
        GlobalCrashHandler.init()
        ReturnTransitionPatcher.patchAll(this)
        app = this

        val dataDir = ContextCompat.getDataDir(this)
        val legacyConfig = File(dataDir, "/shared_prefs/config.xml")
        if (legacyConfig.exists()) {
            val legacyConfigs = sharedPrefsOf("config")
            PlayerInfoManager.compatFollowAll(
                legacyConfigs.getStringSet("push_whitelist", emptySet())!!
            )
            legacyConfig.delete()
        }
    }


}

