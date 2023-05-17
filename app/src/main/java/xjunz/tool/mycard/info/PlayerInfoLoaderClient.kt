package xjunz.tool.mycard.info

import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import xjunz.tool.mycard.Apis
import xjunz.tool.mycard.main.account.AccountManager
import xjunz.tool.mycard.model.Duel
import xjunz.tool.mycard.model.Duel.PlayerNumber
import xjunz.tool.mycard.model.History
import xjunz.tool.mycard.model.Player
import xjunz.tool.mycard.util.CompositeCloseable
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

class PlayerInfoLoaderClient : Closeable {

    private val closeables = CompositeCloseable()

    private val client by closeables.lazilyCompose {
        HttpClient(OkHttp) {
            BrowserUserAgent()
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
            install(HttpRequestRetry) {
                maxRetries = 2
                retryIf { _, httpResponse -> !httpResponse.status.isSuccess() }
            }
        }
    }

    suspend fun queryPlayerHistory(name: String, num: Int): History? {
        return withContext(Dispatchers.IO) {
            client.runCatching {
                get(Apis.BASE_API + Apis.API_PLAYER_HISTORY) {
                    parameter("username", name)
                    parameter("type", 0)
                    parameter("page_num", num)
                }.body<History>()
            }.onFailure {
                Log.e("err", it.stackTraceToString())
            }.getOrNull()
        }
    }

    private val mutexes = ConcurrentHashMap<String, Mutex>()

    /**
     * Query a player info in a specific [duel]. When the player info is queried successfully, we would
     * [put the player][Duel.setPlayer] into the [duel] automatically, hence this method does not return
     * the queried player info, but returns whether the query is succeeded or not.
     */
    suspend fun queryPlayerInfo(
        duel: Duel,
        @PlayerNumber which: Int,
        update: Boolean = false
    ): Boolean {
        val name = duel.requirePlayerName(which)
        // Every query holds a mutex lock util finished. So if a coroutine try to query a player
        // which is locked, it would suspend itself until the existing query returns.
        val ret = withContext(Dispatchers.IO) w@{
            mutexes.getOrPut(name) { Mutex() }.withLock {
                if (duel.getPlayer(which) != null && !update) return@w true
                return@w fetchPlayerInfoFromRemote(duel, which)
            }
        }
        // the mutex is useless now
        mutexes.remove(name)
        return ret
    }

    suspend fun queryMyInfo(): Player? {
        return withContext(Dispatchers.IO) w@{
            runCatching {
                client.get(Apis.BASE_API + Apis.API_PLAYER_INFO) {
                    parameter("username", AccountManager.reqUsername())
                }.body<Player>()
            }.onSuccess {
                it.name = AccountManager.reqUsername()
            }.onFailure {
                it.printStackTrace()
            }
        }.getOrNull()
    }

    /**
     * Query all players' info of a [duel] in parallel.
     *
     * @return whether all players' info is successfully queried (not null)
     */
    suspend fun queryAllPlayerInfo(duel: Duel, update: Boolean = false): Boolean =
        coroutineScope c@{
            return@c listOf(
                async { queryPlayerInfo(duel, Duel.PLAYER_1, update) },
                async { queryPlayerInfo(duel, Duel.PLAYER_2, update) })
                .awaitAll()
                .contains(false)
                .not()
        }

    private suspend fun fetchPlayerInfoFromRemote(duel: Duel, @PlayerNumber which: Int): Boolean {
        val name = duel.requirePlayerName(which)
        return runCatching {
            client.get(Apis.BASE_API + Apis.API_PLAYER_INFO) {
                parameter("username", name)
            }.body<Player>()
        }.onSuccess {
            it.name = name
            duel.setPlayer(which, it)
        }.onFailure {
            it.printStackTrace()
        }.isSuccess
    }

    override fun close() = closeables.close()
}