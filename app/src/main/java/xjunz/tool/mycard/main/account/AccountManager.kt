package xjunz.tool.mycard.main.account

import android.graphics.Bitmap
import androidx.core.content.edit
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xjunz.tool.mycard.Apis
import xjunz.tool.mycard.app
import xjunz.tool.mycard.model.User
import xjunz.tool.mycard.util.HttpStatusCodeException
import xjunz.tool.mycard.util.printLog
import java.io.File
import java.net.URL

/**
 * @author xjunz 2022/2/28
 */
object AccountManager {

    class AuthExpiredException : HttpStatusCodeException(HttpStatusCode.Unauthorized)

    @Serializable
    private data class AuthResponse(val u16Secret: Int)

    private const val SP_KEY_USER = "user"
    private const val SP_KEY_REMEMBERED_USERNAME = "remembered_username"
    private const val SP_KEY_LOGIN_TIMESTAMP = "user_login_timestamp"
    private const val SP_KEY_U16SECRET = "u16secret"
    private const val SP_KEY_AUTH_TOKEN = "auth_token"

    private var isInitialized = false
    private var user: User? = null
    private var u16Secret: Int? = null
    private var authToken: String? = null

    private val httpClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            BrowserUserAgent()
            expectSuccess = false
            install(HttpTimeout) { socketTimeoutMillis = 5_000 }
            install(ContentNegotiation) {
                json(Json { isLenient = true; ignoreUnknownKeys = true })
            }
        }
    }

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
            authToken = sharedPrefs.getString(SP_KEY_AUTH_TOKEN, null)
            // v2.4 -> v2.5 migration: the bearer token wasn't persisted before,
            // so logged-in v2.4 users land here with user != null but
            // authToken == null. Wipe the orphaned state and force re-login.
            if (user != null && authToken == null) logout()
        }
        isInitialized = true
    }

    fun logout() {
        user = null
        u16Secret = null
        authToken = null
        sharedPrefs.edit {
            remove(SP_KEY_USER)
            remove(SP_KEY_LOGIN_TIMESTAMP)
            remove(SP_KEY_U16SECRET)
            remove(SP_KEY_AUTH_TOKEN)
        }
        app.getFileStreamPath(LOCAL_AVATAR_NAME).delete()
    }

    fun hasLogin(): Boolean {
        check(isInitialized) { "AccountManager is not initialized" }
        return user != null && u16Secret != null && authToken != null
    }

    /**
     * Fetch a fresh u16Secret from /authUser. The server rotates this token every 4 minutes
     * and tolerates only the current and previous rotation, so callers must refresh before
     * any tiramisu connect (spectate / matchmaking).
     *
     * Throws [AuthExpiredException] when the bearer token itself has expired (HTTP 401) —
     * the user must re-enter credentials. Other failures throw [HttpStatusCodeException] or
     * surface the underlying network exception; in those cases callers can fall back to the
     * cached secret since the server still tolerates one rotation.
     */
    suspend fun refreshU16Secret(): Int = withContext(Dispatchers.IO) {
        val token = checkNotNull(authToken) { "auth token is null; user not logged in" }
        val resp = httpClient.get(Apis.BASE_API_ACCOUNTS + Apis.API_AUTH_USER) {
            bearerAuth(token)
        }
        when {
            resp.status.isSuccess() -> {
                val secret = resp.body<AuthResponse>().u16Secret
                u16Secret = secret
                sharedPrefs.edit { putInt(SP_KEY_U16SECRET, secret) }
                secret
            }
            resp.status == HttpStatusCode.Unauthorized -> throw AuthExpiredException()
            else -> throw HttpStatusCodeException(resp.status)
        }
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

    fun persistUserInfo(user: User, authToken: String, u16secret: Int) {
        this.user = user
        this.authToken = authToken
        this.u16Secret = u16secret
        sharedPrefs.edit(commit = true) {
            putString(SP_KEY_USER, Json.encodeToString(user))
            putLong(SP_KEY_LOGIN_TIMESTAMP, System.currentTimeMillis())
            putInt(SP_KEY_U16SECRET, u16secret)
            putString(SP_KEY_AUTH_TOKEN, authToken)
        }
    }
}