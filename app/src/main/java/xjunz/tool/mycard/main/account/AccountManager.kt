package xjunz.tool.mycard.main.account

import android.graphics.Bitmap
import androidx.core.content.edit
import kotlinx.serialization.json.Json
import xjunz.tool.mycard.app
import xjunz.tool.mycard.model.User
import xjunz.tool.mycard.util.printLog
import java.io.File
import java.net.URL

/**
 * @author xjunz 2022/2/28
 */
object AccountManager {
    private const val SP_KEY_USER = "user"
    private const val SP_KEY_REMEMBERED_USERNAME = "remembered_username"
    private const val SP_KEY_LOGIN_TIMESTAMP = "user_login_timestamp"
    private const val SP_KEY_U16SECRET = "u16secret"

    private var isInitialized = false
    private var user: User? = null
    private var u16Secret: Int? = null

    // Never expire
    private const val LOGIN_STATE_EXPIRED_DURATION = Long.MAX_VALUE // 1_209_600_000

    private const val LOCAL_AVATAR_NAME = "avatar"

    private val sharedPrefs = app.sharedPrefsOf("account")

    fun initIfNeeded() {
        if (isInitialized) return
        val lastLoginTimestamp = sharedPrefs.getLong(SP_KEY_LOGIN_TIMESTAMP, -1)
        if (lastLoginTimestamp != -1L
            && System.currentTimeMillis() - lastLoginTimestamp >= LOGIN_STATE_EXPIRED_DURATION
        ) {
            logout()
        } else {
            sharedPrefs.getString(SP_KEY_USER, null)?.let {
                user = Json.decodeFromString(it)
            }
            sharedPrefs.getInt(SP_KEY_U16SECRET, -1).let {
                u16Secret = if (it == -1) null else it
            }
        }
        isInitialized = true
    }

    fun logout() {
        user = null
        sharedPrefs.edit {
            remove(SP_KEY_USER)
            remove(SP_KEY_LOGIN_TIMESTAMP)
        }
        app.getFileStreamPath(LOCAL_AVATAR_NAME).delete()
    }

    fun hasLogin(): Boolean {
        check(isInitialized) { "AccountManager is not initialized" }
        return user != null && u16Secret != null
    }

    fun peekUser(): User {
        check(isInitialized) { "AccountManager is not initialized" }
        checkNotNull(user) { "User is not logged in" }
        return user!!
    }

    fun reqUsername(): String {
        return peekUser().username
    }

    fun reqU16Secret(): Int {
        return u16Secret!!
    }

    private fun getLocalAvatarFile(): File {
        return app.getFileStreamPath(LOCAL_AVATAR_NAME)
    }

    fun getAvatarUrl(): URL {
        check(hasLogin()) { "User is not logged in" }
        val file = getLocalAvatarFile()
        if (file.exists()) {
            printLog("Use local avatar")
            return file.toURI().toURL()
        }
        printLog("Use remote avatar")
        return URL(peekUser().avatar)
    }

    fun rememberUsername(username: String) {
        sharedPrefs.edit { putString(SP_KEY_REMEMBERED_USERNAME, username) }
    }

    fun getRememberedUsername(): String? {
        return sharedPrefs.getString(SP_KEY_REMEMBERED_USERNAME, null)
    }

    fun persistAvatarIfNeeded(bitmap: Bitmap) {
        val file = getLocalAvatarFile()
        if (file.createNewFile()) {
            runCatching {
                file.outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }.onFailure {
                file.delete()
            }
        }
    }

    fun persistUserInfo(user: User, u16secret: Int) {
        this.user = user
        this.u16Secret = u16secret
        sharedPrefs.edit(commit = true) {
            putString(SP_KEY_USER, Json.encodeToString(user))
            putLong(SP_KEY_LOGIN_TIMESTAMP, System.currentTimeMillis())
            putInt(SP_KEY_U16SECRET, u16secret)
        }
    }
}