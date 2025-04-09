package xjunz.tool.mycard.main.account

import androidx.lifecycle.Lifecycle
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import xjunz.tool.mycard.Apis
import xjunz.tool.mycard.model.User
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
            })
        }
    }

    suspend fun login(username: String, password: String): Result<User> =
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
                    ret.status.isSuccess() -> return@runCatching ret.body<UserInfo>().user.also {
                        AccountManager.persistUserInfo(it)
                    }
                    ret.status == HttpStatusCode.BadRequest -> throw LoginCredentialException()
                    else -> throw HttpStatusCodeException(ret.status)
                }
            }
        }

    override fun close() = client.close()

}