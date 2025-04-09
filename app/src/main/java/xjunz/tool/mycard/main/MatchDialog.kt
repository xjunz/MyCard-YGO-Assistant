package xjunz.tool.mycard.main

import android.app.Dialog
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.lifecycleScope
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.internal.closeQuietly
import xjunz.tool.mycard.Apis
import xjunz.tool.mycard.R
import xjunz.tool.mycard.app
import xjunz.tool.mycard.common.BaseBottomSheetDialog
import xjunz.tool.mycard.databinding.DialogMatchBinding
import xjunz.tool.mycard.game.GameLauncher.launchGameCheckLogin
import xjunz.tool.mycard.ktx.beginDelayedTransition
import xjunz.tool.mycard.ktx.format
import xjunz.tool.mycard.ktx.formatDurationMinSec
import xjunz.tool.mycard.ktx.toast
import xjunz.tool.mycard.main.account.Generator
import xjunz.tool.mycard.model.MatchResult
import xjunz.tool.mycard.util.printLog
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

class MatchDialog : BaseBottomSheetDialog<DialogMatchBinding>() {

    override fun onDialogCreated(dialog: Dialog) {
        binding.btnCancel.setOnClickListener {
            if (matchClient.isActive) {
                matchClient.cancel()
            }
            dismiss()
        }
        isCancelable = false
        athleticMatch()
        durationUpdater.run()
    }

    private val matchClient = HttpClient(OkHttp) {
        BrowserUserAgent()
        install(HttpTimeout) {
            socketTimeoutMillis = 60_000
        }
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }
    }

    private fun getLocale(): String {
        val locale = ConfigurationCompat.getLocales(app.resources.configuration)[0]!!
        return "${locale.language}-${locale.country}"
    }

    private var startTimestamp: Long = -1L

    private var anticipation: Float = -1F

    private val mainHandler = Handler(Looper.getMainLooper())

    private val durationUpdater = object : Runnable {
        override fun run() {
            var duration = (System.currentTimeMillis() - startTimestamp).formatDurationMinSec()
            if (anticipation != -1f) {
                duration += "/" + R.string.format_expected_duration.format(anticipation)
            }
            binding.tvMatchDuration.text = R.string.format_match_duration.format(duration)
            mainHandler.postDelayed(this, 1000)
        }
    }

    private fun athleticMatch() {
        startTimestamp = System.currentTimeMillis()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    matchClient.get(
                        Apis.BASE_API + Apis.API_MATCH_ANTICIPATE + Apis.ARENA_ATHLETIC
                    ).bodyAsText().toFloat()
                }
            }.onSuccess {
                anticipation = it
                printLog(binding.root.height.toString())
                mainHandler.removeCallbacks(durationUpdater)
                dialog?.findViewById<ViewGroup>(android.R.id.content)
                    ?.beginDelayedTransition()
                durationUpdater.run()
            }.onFailure {
                it.printStackTrace()
            }
        }
          requireActivity().lifecycleScope.launch {
              withContext(Dispatchers.IO) {
                  runCatching {
                      matchClient.post(Apis.BASE_API + Apis.API_MATCH) {
                          parameter("arena", Apis.ARENA_ATHLETIC)
                          parameter("locale", getLocale())
                          header("Authorization", Generator.generateMatchAuth())
                      }.body<MatchResult>()
                  }
              }.onSuccess {
                 it.launchGameCheckLogin(requireActivity())
              }.onFailure {
                  it.printStackTrace()
                  if (it is TimeoutCancellationException
                      || it is SocketTimeoutException
                      || it is TimeoutException
                  ) {
                      toast(R.string.request_timed_out)
                  } else if (it is CancellationException) {
                      toast(R.string.match_cancelled)
                  } else {
                      toast(R.string.network_error_or_server_error)
                  }
              }
              dismiss()
          }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        matchClient.closeQuietly()
        mainHandler.removeCallbacksAndMessages(null)
    }
}