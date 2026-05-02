package xjunz.tool.mycard.game

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import xjunz.tool.mycard.Apis
import xjunz.tool.mycard.R
import xjunz.tool.mycard.app
import xjunz.tool.mycard.ktx.errorToast
import xjunz.tool.mycard.ktx.longToast
import xjunz.tool.mycard.ktx.showLoadingDialog
import xjunz.tool.mycard.ktx.toast
import xjunz.tool.mycard.main.account.AccountManager
import xjunz.tool.mycard.main.account.Generator
import xjunz.tool.mycard.main.account.LoginDialog
import xjunz.tool.mycard.main.settings.Configs
import xjunz.tool.mycard.model.Duel
import xjunz.tool.mycard.model.MatchResult


/**
 * @author xjunz 2022/3/1
 */
object GameLauncher {

    private const val ACTION_LAUNCH_YGO_MOBILE_GAME = "ygomobile.intent.action.GAME"
    private const val SP_NAME_GAME = "game"
    private const val SP_KEY_YGO_INTRO_SEEN_INSTALL_TIME = "ygo_mobile_intro_seen_install_time"

    private inline val token get() = Generator.generateToken(AccountManager.reqU16Secret())

    /**
     * Check whether there is a game launcher on the device.
     */
    fun exists(): Boolean {
        return Intent(ACTION_LAUNCH_YGO_MOBILE_GAME).resolveActivity(app.packageManager) != null
    }

    private fun spectateAthleticDuel(duelId: String) {
        launchAthleticMatch(token + duelId)
    }

    private fun launchAthleticMatch(roomId: String) {
        launch(AccountManager.reqUsername(), Apis.HOST_ATHLETIC, 8911, roomId)
    }

    /**
     * Launch a game with specific parameters.
     */
    fun launch(username: String, host: String, port: Int, roomId: String) {
        runCatching {
            val intent = Intent(ACTION_LAUNCH_YGO_MOBILE_GAME).apply {
                // FLAG_ACTIVITY_CLEAR_TASK forces YGOMobile's MainActivity to cold-start every
                // time, gating our extras behind its async ResCheckTask and showing a black
                // screen until that completes. NEW_TASK alone lets a warm task receive the
                // intent via onNewIntent, where extras are dispatched immediately.
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("host", host)
                putExtra("port", port)
                putExtra("user", username)
                putExtra("room", roomId)
            }
            app.startActivity(intent)
        }.onFailure {
            if (it is ActivityNotFoundException) longToast(R.string.no_game_launcher_found)
            else errorToast(it)
        }
    }

    fun Game.launch() {
        launch(username, host, port, generateRoomId())
    }

    fun Duel.spectateCheckLogin(activity: FragmentActivity) {
        val duel = this
        if (!AccountManager.hasLogin()) {
            LoginDialog().doOnSuccess {
                duel.spectateCheckLogin(activity)
            }.show(activity.supportFragmentManager, "login")
            toast(R.string.pls_login_first)
            return
        }
        showFirstTimeYgoHintIfNeeded(activity) {
            val loading = activity.showLoadingDialog(R.string.connecting_to_server)
            val job = activity.lifecycleScope.launch {
                try {
                    try {
                        AccountManager.refreshU16Secret()
                    } catch (e: AccountManager.AuthExpiredException) {
                        AccountManager.logout()
                        toast(R.string.session_expired)
                        LoginDialog().doOnSuccess {
                            duel.spectateCheckLogin(activity)
                        }.show(activity.supportFragmentManager, "login")
                        return@launch
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Network or transient server error: fall back to the cached secret.
                        // The server tolerates the previous rotation, so this still works briefly.
                        e.printStackTrace()
                    }
                    spectateAthleticDuel(duel.id)
                } finally {
                    loading.dismiss()
                }
            }
            loading.setOnCancelListener { job.cancel() }
        }
    }

    /**
     * Show a one-time onboarding dialog before the first GAME-intent launch. YGOMobile's
     * MainActivity gates the intent behind an async ResCheckTask that fails quietly when
     * first-run init has not been performed by LogoActivity, leaving the user on a black
     * screen. Letting the user open YGOMobile once first sidesteps that.
     *
     * The dismissal flag is keyed to YGOMobile's [PackageInfo.firstInstallTime] so a
     * reinstall re-arms the hint, and is only persisted when the user picks "Open
     * YGOMobile first" — that's the only branch we know guarantees first-run init.
     */
    private fun showFirstTimeYgoHintIfNeeded(
        activity: FragmentActivity,
        onProceed: () -> Unit,
    ) {
        val prefs = app.sharedPrefsOf(SP_NAME_GAME)
        val pm = activity.packageManager
        val pkgName = Intent(ACTION_LAUNCH_YGO_MOBILE_GAME)
            .resolveActivity(pm)?.packageName
        val installTime = pkgName?.let {
            runCatching { pm.getPackageInfo(it, 0).firstInstallTime }.getOrNull()
        } ?: 0L
        if (installTime > 0L &&
            prefs.getLong(SP_KEY_YGO_INTRO_SEEN_INSTALL_TIME, 0L) == installTime
        ) {
            onProceed()
            return
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.first_time_ygo_title)
            .setMessage(R.string.first_time_ygo_message)
            .setCancelable(true)
            .setNegativeButton(R.string.open_ygo_first) { _, _ ->
                Configs.firstTimeYgoHintRead = true
                if (installTime > 0L) {
                    prefs.edit {
                        putLong(SP_KEY_YGO_INTRO_SEEN_INSTALL_TIME, installTime)
                    }
                }
                val launchIntent = pkgName?.let { pm.getLaunchIntentForPackage(it) }
                if (launchIntent != null) {
                    activity.startActivity(launchIntent)
                } else {
                    longToast(R.string.no_game_launcher_found)
                }
            }
            .setPositiveButton(R.string.continue_anyway) { _, _ ->
                Configs.firstTimeYgoHintRead = true
                // Intentionally don't persist the install-time flag here: if init was never
                // done, the launch will black-screen and the dialog should re-arm next time.
                onProceed()
            }
            .show()
    }

    fun MatchResult.launchGameCheckLogin(activity: FragmentActivity) {
        if (!AccountManager.hasLogin()) {
            LoginDialog().doOnSuccess {
                launch(AccountManager.reqUsername(), address, port, password)
            }.show(activity.supportFragmentManager, "login")
            toast(R.string.pls_login_first)
        } else {
            launch(AccountManager.reqUsername(), address, port, password)
        }
    }
}