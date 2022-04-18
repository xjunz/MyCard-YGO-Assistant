package xjunz.tool.mycard.main.tools

import android.app.Dialog
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.text.HtmlCompat
import androidx.core.text.parseAsHtml
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import io.ktor.utils.io.streams.*
import kotlinx.coroutines.*
import xjunz.tool.mycard.R
import xjunz.tool.mycard.app
import xjunz.tool.mycard.common.BaseBottomSheetDialog
import xjunz.tool.mycard.databinding.DialogDownloadPrescriptsBinding
import xjunz.tool.mycard.game.GameLauncher
import xjunz.tool.mycard.ktx.*
import java.io.File
import java.util.zip.ZipInputStream


class PrescriptsDownloadDialog : BaseBottomSheetDialog<DialogDownloadPrescriptsBinding>() {

    private val url = "https://ygo233.com/pre"

    private var downloadUrl: String? = null

    private var updateHistory: String? = null

    private val client = HttpClient(OkHttp) {
        BrowserUserAgent()
        install(HttpTimeout) {
            socketTimeoutMillis = 5_000
        }
    }

    private fun fetchFromRemote() {
        lifecycleScope.launch {
            binding.btnDownload.isEnabled = false
            binding.progressIndicator.isVisible = true
            binding.scrollView.isVisible = false
            withContext(Dispatchers.IO) {
                runCatching {
                    val html = client.get(url).bodyAsText()
                    val downloadUrlPattern = Regex("href=\"(https:.+?ygosrv233-pre-mobile\\.zip)")
                    val historyPattern = Regex(
                        "<ul class=\"auto-generated\">\n(.+?)\n</ul>",
                        RegexOption.DOT_MATCHES_ALL
                    )
                    downloadUrl = downloadUrlPattern.find(html)?.groupValues?.get(1)
                    updateHistory = historyPattern.find(html)?.groupValues?.get(1)
                }
            }
            binding.apply {
                root.rootView.beginDelayedTransition()
                if (downloadUrl == null) {
                    btnDownload.setIconResource(R.drawable.ic_twotone_replay_24)
                    btnDownload.setText(R.string.reload)
                    tvUpdateHistory.text = R.string.prompt_load_failed.resStr
                } else {
                    btnDownload.setIconResource(R.drawable.ic_twotone_cloud_download_24)
                    btnDownload.setText(R.string.download_and_install)
                    tvUpdateHistory.text =
                        updateHistory?.parseAsHtml(HtmlCompat.FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM)
                    scrollView.updateLayoutParams {
                        height = root.rootView.height / 2
                    }
                }
                scrollView.isVisible = true
                btnDownload.isEnabled = true
                progressIndicator.isVisible = false
            }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    override fun onDialogCreated(dialog: Dialog) {
        fetchFromRemote()
        binding.btnDownload.setOnClickListener {
            if (downloadUrl == null) {
                binding.root.rootView.beginDelayedTransition()
                fetchFromRemote()
            } else {
                if (!GameLauncher.exists()) {
                    toast(R.string.no_game_launcher_found)
                } else {
                    downloadAndInstall()
                }
            }
        }
        binding.btnOpenInBrowser.setOnClickListener {
            requireContext().viewUrlSafely(url)
        }
    }

    private var downloadedFile: File? = null

    private var zipOutDir: File? = null

    @Suppress("BlockingMethodInNonBlockingContext")
    private fun downloadAndInstall() {
        binding.btnDownload.setText(R.string.downloading)
        binding.btnDownload.isEnabled = false
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val filename = downloadUrl!!.substringAfterLast('/')
                    val cacheFile = File(requireContext().cacheDir, filename)
                    downloadedFile = cacheFile
                    if (!cacheFile.exists()) cacheFile.createNewFile()
                    val bytes = client.get(downloadUrl!!).bodyAsChannel()
                    cacheFile.outputStream().use {
                        it.writePacket(bytes.readRemaining())
                        it.flush()
                    }
                    val zipInputStream = ZipInputStream(cacheFile.inputStream())
                    zipOutDir = File(requireContext().cacheDir, filename.substringBefore('.'))
                    zipOutDir?.mkdirs()
                    var nextEntry = zipInputStream.nextEntry
                    while (nextEntry != null) {
                        if (nextEntry.isDirectory) {
                            File(zipOutDir, nextEntry.name).mkdirs()
                            zipInputStream.closeEntry()
                        } else {
                            val file = File(zipOutDir, nextEntry.name)
                            file.createNewFile()
                            file.writeBytes(zipInputStream.readBytes())
                            zipInputStream.closeEntry()
                        }
                        nextEntry = zipInputStream.nextEntry
                    }
                    val uri = FileProvider.getUriForFile(
                        requireContext(), app.fileProviderAuthority,
                        File(zipOutDir, "/expansions/ygo233.com-pre-release.ypk")
                    )
                    requireContext().viewUriSafely(uri) {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
            }.onSuccess {
                toast(R.string.download_successfully)
            }.onFailure {
                it.printStackTrace()
                errorToast(it)
                binding.btnDownload.isEnabled = true
            }
            binding.btnDownload.setText(R.string.download_and_install)
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        if (client.isActive) {
            client.cancel()
        }
        // clean up
        if (downloadedFile?.exists() == true) {
            downloadedFile?.deleteRecursively()
        }
        if (zipOutDir?.exists() == true) {
            zipOutDir?.deleteRecursively()
        }
    }
}