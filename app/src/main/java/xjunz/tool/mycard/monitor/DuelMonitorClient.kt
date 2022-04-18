package xjunz.tool.mycard.monitor

import android.accounts.NetworkErrorException
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.ArraySet
import androidx.lifecycle.MutableLiveData
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.json.*
import xjunz.tool.mycard.Constants
import xjunz.tool.mycard.app
import xjunz.tool.mycard.info.DuelRepository
import xjunz.tool.mycard.info.DuelRepository.cacheAll
import xjunz.tool.mycard.info.DuelRepository.removeFromCache
import xjunz.tool.mycard.info.DuelRepository.tryReadFromCache
import xjunz.tool.mycard.model.Duel
import xjunz.tool.mycard.monitor.push.DuelPushManager
import xjunz.tool.mycard.monitor.push.DuelPushManager.cancelPush
import xjunz.tool.mycard.util.CompositeCloseable
import xjunz.tool.mycard.util.printLog
import java.io.Closeable
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * @author xjunz 2022/3/2
 */
class DuelMonitorClient : Closeable, DuelMonitorDelegate {

    override val state = MutableLiveData(State.DISCONNECTED_IDLE)

    override val duelList: MutableList<Duel> = mutableListOf()

    override var isInitialized = false

    override var lastUpdateTimestamp = -1L

    override fun addEventObserverIfAbsent(observer: DuelMonitorEventObserver) {
        if (eventObservers == null) {
            eventObservers = ArraySet()
        }
        eventObservers?.add(observer)
    }

    override fun removeEventObserve(observer: DuelMonitorEventObserver) {
        eventObservers?.remove(observer)
    }

    override fun clearAllIfOutOfDate() {
        if (state.value in State.DISCONNECTED) {
            duelList.clear()
            isInitialized = false
            eventObservers?.forEach {
                it.onAllDuelCleared()
            }
        }
    }

    private var eventObservers: ArraySet<DuelMonitorEventObserver>? = null

    private val closeables = CompositeCloseable()

    private val connectivityService by lazy {
        app.getSystemService(ConnectivityManager::class.java)
    }

    /**
     * The ordinal for all duels used for natural order sorting.
     */
    private var ordinal = -1

    private val client = closeables.compose(HttpClient(OkHttp) {
        BrowserUserAgent()
        install(HttpTimeout) {
            socketTimeoutMillis = 10_000
        }
        install(WebSockets) {
            pingInterval = 10_000
        }
    })

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            if (msg.what == MSG_CANCEL_MONITOR_JOB && monitorJob?.isActive == true) {
                monitorJob?.cancel(LongTimeNoFrameCancellationException())
            }
        }
    }

    private var monitorJob: Job? = null

    fun cancelIfNeeded() {
        if (monitorJob?.isActive == true) monitorJob?.cancel()
    }

    /**
     * Activate the WSS client only when the client is not active, so this method could be called
     * multiple times without side effects.
     */
    fun activateIfNeeded() {
        if (monitorJob?.isActive == true) {
            printLog("The wss client is active, ignore the start command.")
            return
        }
        monitorJob = client.launch(Dispatchers.Default + SupervisorJob()) {
            state.postValue(State.CONNECTING)
            val ret = runCatching {
                if (connectivityService.activeNetwork == null) throw NetworkErrorException()
                client.wss(host = Constants.HOST_DUEL_LIST, port = 8923, path = "?filter=started") {
                    // cancel if long time no init event
                    handler.sendEmptyMessageDelayed(
                        MSG_CANCEL_MONITOR_JOB, INTERVAL_LONG_TIME_NO_INIT
                    )
                    while (isActive) (incoming.receive() as? Frame.Text)?.parse()
                }
            }
            // reset ordinal
            ordinal = -1
            // record disconnecting timestamp
            lastUpdateTimestamp = System.currentTimeMillis()
            // remove countdown cancellation
            handler.removeMessages(MSG_CANCEL_MONITOR_JOB)
            // cache all duels now
            duelList.cacheAll()
            // mute all pending pushes
            DuelPushManager.suffocateAll()
            state.postValue(
                when (val e = ret.exceptionOrNull()?.also { it.printStackTrace() }) {
                    is TimeoutCancellationException, is SocketTimeoutException,
                    is LongTimeNoFrameCancellationException
                    -> State.DISCONNECTED_TIMED_OUT
                    null, is CancellationException, is ClosedReceiveChannelException
                    -> State.DISCONNECTED_USER_REQUEST
                    is NetworkErrorException -> State.DISCONNECTED_NETWORK
                    is SocketException, is UnknownHostException ->
                        if (e.message?.contains("reset") == true) State.DISCONNECTED_REJECTED
                        else State.DISCONNECTED_SERVER
                    else -> State.DISCONNECTED_UNEXPECTED
                }
            )
        }
    }

    private fun JsonElement.getString(key: String): String {
        return jsonObject.getValue(key).jsonPrimitive.content
    }

    private fun JsonElement.getArray(key: String): JsonArray {
        return jsonObject.getValue(key).jsonArray
    }

    private fun JsonElement.getObject(key: String): JsonObject {
        return jsonObject.getValue(key).jsonObject
    }

    companion object {
        const val MSG_CANCEL_MONITOR_JOB = 1

        // the max acceptable interval between two neighboring frame
        const val INTERVAL_LONG_TIME_NO_FRAME = 10 * 60 * 1000L

        // the max acceptable interval between connected and initialized
        const val INTERVAL_LONG_TIME_NO_INIT = 5 * 1000L

        const val EVENT_INIT = "init"
        const val EVENT_DELETE = "delete"
        const val EVENT_CREATE = "create"

        const val KEY_EVENT = "event"
        const val KEY_DATA = "data"
        const val KEY_USERNAME = "username"
        const val KEY_USERS = "users"
        const val KEY_ID = "id"
    }

    private class LongTimeNoFrameCancellationException :
        CancellationException("Long time no frame!")

    private fun parseDuel(duelData: JsonElement, duelists: JsonArray) = Duel(
        duelData.getString(KEY_ID), ++ordinal,
        duelists[0].getString(KEY_USERNAME),
        duelists[1].getString(KEY_USERNAME)
    )

    private suspend inline fun <T : Any> T.dispatchToMain(crossinline block: T.() -> Unit) {
        withContext(Dispatchers.Main) { block.invoke(this@dispatchToMain) }
    }

    private suspend fun Frame.Text.parse() {
        if (state.value == State.CONNECTED) {
            // reset countdown
            handler.removeMessages(MSG_CANCEL_MONITOR_JOB)
            handler.sendEmptyMessageDelayed(MSG_CANCEL_MONITOR_JOB, INTERVAL_LONG_TIME_NO_FRAME)
        }
        val root = Json.parseToJsonElement(readText())
        when (val event = root.getString(KEY_EVENT)) {
            EVENT_INIT -> {
                val ret = root.getArray(KEY_DATA).map { parseDuel(it, it.getArray(KEY_USERS)) }
                duelList.clear()
                duelList.addAll(ret)

                // read from cache
                duelList.forEach {
                    it.tryReadFromCache()
                }
                // clear all old cache
                DuelRepository.clearAllCache()
                // awake all possible pending pushes
                DuelPushManager.awakeSurvivors(duelList)

                isInitialized = true
                // really connected now
                state.postValue(State.CONNECTED)
                // cancel if no time no frame
                handler.removeMessages(MSG_CANCEL_MONITOR_JOB)
                handler.sendEmptyMessageDelayed(MSG_CANCEL_MONITOR_JOB, INTERVAL_LONG_TIME_NO_FRAME)

                eventObservers?.dispatchToMain { forEach { it.onInitialized(duelList) } }
            }
            EVENT_DELETE -> {
                val id = root.getString(KEY_DATA)
                val index = duelList.indexOfFirst { it.id == id }
                if (index == -1) return
                val found = duelList.removeAt(index)
                found.endTimestamp = System.currentTimeMillis()
                found.removeFromCache()
                found.cancelPush()
                eventObservers?.dispatchToMain { forEach { observer -> observer.onDuelDeleted(found) } }
            }
            EVENT_CREATE -> {
                val data = root.getObject(KEY_DATA)
                val duel = parseDuel(data, data.getArray(KEY_USERS))
                duel.startTimestamp = System.currentTimeMillis()
                duelList.add(duel)
                eventObservers?.dispatchToMain { forEach { it.onDuelCreated(duel) } }
            }
            else -> printLog("unknown event: $event")
        }
    }

    override fun close() {
        if (client.isActive) client.cancel()
        closeables.close()
    }
}