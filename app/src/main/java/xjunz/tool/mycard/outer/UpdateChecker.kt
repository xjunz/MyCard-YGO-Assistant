package xjunz.tool.mycard.outer

import androidx.lifecycle.Lifecycle
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import xjunz.tool.mycard.Constants
import xjunz.tool.mycard.model.UpdateInfo
import xjunz.tool.mycard.util.LifecyclePerceptiveCloseable

/**
 * @author xjunz 2022/04/18
 */
class UpdateChecker(lifecycle: Lifecycle? = null) : LifecyclePerceptiveCloseable(lifecycle) {

    private val client = HttpClient(OkHttp) {
        BrowserUserAgent()
        install(ContentNegotiation) { json() }
    }

    suspend fun checkUpdate(): Result<UpdateInfo> = runCatching {
        client.get(Constants.CHECK_UPDATE_BASE_URL + Constants.CHECK_UPDATE_FIR_APP_ID) {
            parameter("api_token", Constants.CHECK_UPDATE_FIR_API_TOKEN)
        }.body()
    }

    override fun close() {
        if (client.isActive) client.cancel()
    }

}