package xjunz.tool.mycard.main.account

import android.graphics.Bitmap
import androidx.core.content.edit
import kotlinx.serialization.decodeFromString
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

    private var isInitialized = false
    private var user: User? = null

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
        }
        isInitialized = true
    }

    private fun getLoginTimestamp(): Long {
        return sharedPrefs.getLong(SP_KEY_LOGIN_TIMESTAMP, System.currentTimeMillis())
    }

    fun logout() {
        user = null
        sharedPrefs.edit {
            remove(SP_KEY_USER)
            remove(SP_KEY_LOGIN_TIMESTAMP)
        }
        app.getFileStreamPath(LOCAL_AVATAR_NAME).delete()
    }

    // reserved
    private fun checkExpiration(): Boolean {
        if (hasLogin() &&
            System.currentTimeMillis() - getLoginTimestamp() > LOGIN_STATE_EXPIRED_DURATION
        ) {
            logout()
            return true
        }
        return false
    }

    fun hasLogin(): Boolean {
        check(isInitialized) { "AccountManager is not initialized" }
        return user != null
    }

    fun peekUser(): User {
        check(isInitialized) { "AccountManager is not initialized" }
        checkNotNull(user) { "User is not logged in" }
        return user!!
    }

    fun reqUsername(): String {
        return peekUser().username
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

    fun reqUserId(): Int {
        return peekUser().id
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

    fun persistUserInfo(user: User) {
        this.user = user
        sharedPrefs.edit {
            putString(SP_KEY_USER, Json.encodeToString(User.serializer(), user))
            putLong(SP_KEY_LOGIN_TIMESTAMP, System.currentTimeMillis())
        }
    }
}