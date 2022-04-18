package xjunz.tool.mycard.game

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.fragment.app.FragmentActivity
import xjunz.tool.mycard.Constants
import xjunz.tool.mycard.R
import xjunz.tool.mycard.app
import xjunz.tool.mycard.ktx.errorToast
import xjunz.tool.mycard.ktx.longToast
import xjunz.tool.mycard.ktx.toast
import xjunz.tool.mycard.main.account.AccountManager
import xjunz.tool.mycard.main.account.Generator
import xjunz.tool.mycard.main.account.LoginDialog
import xjunz.tool.mycard.model.Duel
import xjunz.tool.mycard.model.MatchResult


/**
 * @author xjunz 2022/3/1
 */
object GameLauncher {

    private const val ACTION_LAUNCH_YGO_MOBILE_GAME = "ygomobile.intent.action.GAME"

    private inline val token get() = Generator.generateToken(AccountManager.reqUserId())

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
        launch(AccountManager.reqUsername(), Constants.HOST_ATHLETIC, 8911, roomId)
    }

    /**
     * Launch a game with specific parameters.
     */
    fun launch(username: String, host: String, port: Int, roomId: String) {
        runCatching {
            val intent = Intent(ACTION_LAUNCH_YGO_MOBILE_GAME).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
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
        if (!AccountManager.hasLogin()) {
            LoginDialog().doOnSuccess {
                spectateAthleticDuel(id)
            }.show(activity.supportFragmentManager, "login")
            toast(R.string.pls_login_first)
        } else spectateAthleticDuel(id)
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