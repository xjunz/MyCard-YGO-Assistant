package xjunz.tool.mycard.outer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.StringBuilderPrinter
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import xjunz.tool.mycard.*
import xjunz.tool.mycard.ktx.*
import java.io.File

/**
 * @author xjunz 2022/04/18
 */
object Feedbacks {

    private fun sendMail(body: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(
                Intent.EXTRA_SUBJECT,
                R.string.format_mail_subject.format(System.currentTimeMillis().formatToDate())
            )
            .putExtra(Intent.EXTRA_TEXT, body)
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(Constants.EMAIL_ADDRESS))
        app.launchIntentSafely(intent)
    }

    fun showErrorFeedbackDialog(context: Context, logFile: File) {
        var checkedItem = 0
        MaterialAlertDialogBuilder(context).setTitle(R.string.choose_a_way_to_feedback)
            .setSingleChoiceItems(
                R.array.error_feedback_ways.resArray, checkedItem
            ) { _, position ->
                checkedItem = position
            }.setPositiveButton(android.R.string.ok) { _, _ ->
                when (checkedItem) {
                    0 -> sendMail(logFile.readText())
                    1 -> app.viewUrlSafely(Constants.FEEDBACK_GROUP_URL)
                    2 -> {
                        val uri = FileProvider.getUriForFile(
                            context, app.fileProviderAuthority, logFile
                        )
                        val intent = if (BuildConfig.DEBUG) {
                            Intent(Intent.ACTION_VIEW, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } else {
                            Intent(Intent.ACTION_SEND, uri)
                                .putExtra(Intent.EXTRA_STREAM, uri)
                                .setType("text/plain")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.launchIntentSafely(
                            Intent.createChooser(intent, R.string.open_via.resStr)
                        )
                    }
                }
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showFeedbackDialog(context: Context) {
        var checkedItem = 0
        MaterialAlertDialogBuilder(context).setTitle(R.string.choose_a_way_to_feedback)
            .setSingleChoiceItems(R.array.feedback_ways.resArray, checkedItem) { _, position ->
                checkedItem = position
            }.setPositiveButton(android.R.string.ok) { _, _ ->
                when (checkedItem) {
                    0 -> {
                        val sb = StringBuilder()
                        App.dumpBasicEnvInfo(StringBuilderPrinter(sb))
                        sendMail(sb.toString())
                    }
                    1 -> app.viewUrlSafely(Constants.FEEDBACK_GROUP_URL)
                }
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }


}