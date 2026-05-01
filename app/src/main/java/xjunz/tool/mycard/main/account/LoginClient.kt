package xjunz.tool.mycard.main.account

import androidx.lifecycle.Lifecycle
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xjunz.tool.mycard.Apis
import xjunz.tool.mycard.model.UserInfo
import xjunz.tool.mycard.util.HttpStatusCodeException
import xjunz.tool.mycard.util.LifecyclePerceptiveCloseable

class LoginClient(lifecycle: Lifecycle) : LifecyclePerceptiveCloseable(lifecycle) {

    class LoginCredentialException : HttpStatusCodeException(HttpStatusCode.BadRequest)

    private val client = HttpClient(OkHttp) {
        BrowserUserAgent()
        // when set to true (by default), an exception would thrown if the response http status code
        // is not in 200-300
        expectSuccess = false
        install(HttpTimeout) {
            socketTimeoutMillis = 5_000
        }
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    suspend fun login(username: String, password: String) =
        withContext(Dispatchers.IO) {
            runCatching {
                val ret = client.submitForm(
                    Apis.BASE_API_ACCOUNTS + Apis.API_LOGIN,
                    Parameters.build {
                        append("account", username)
                        append("password", password)
                    }, false
                )

                when {
                    ret.status.isSuccess() -> ret.body<UserInfo>().also {
                        val u16Secret = client.get(Apis.BASE_API_ACCOUNTS + Apis.API_AUTH_USER) {
                            bearerAuth(it.token)
                        }.body<AuthUserResponse>().u16Secret
                        checkNotNull(u16Secret) {
                            "u16secret is null"
                        }
                        AccountManager.persistUserInfo(it.user, it.token, u16Secret)
                    }
                    ret.status == HttpStatusCode.BadRequest -> throw LoginCredentialException()
                    else -> throw HttpStatusCodeException(ret.status)
                }
            }
        }

    private suspend fun authUser(token: String): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                client.get(Apis.BASE_API_ACCOUNTS + Apis.API_AUTH_USER) {
                    bearerAuth(token)
                }.body<AuthUserResponse>().u16Secret
            }
        }

    override fun close() = client.close()

    @Serializable
    private data class AuthUserResponse(
        val u16Secret: Int
    )
}