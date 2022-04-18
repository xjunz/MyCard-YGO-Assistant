package xjunz.tool.mycard.outer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xjunz.tool.mycard.app
import xjunz.tool.mycard.databinding.ActivityCrashReportBinding
import xjunz.tool.mycard.ktx.launchActivity
import xjunz.tool.mycard.ktx.toast
import xjunz.tool.mycard.main.MainActivity
import java.io.File


/**
 * @author xjunz 2022/3/16
 */
class CrashReportActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LOG_FILE_PATH = "xjunz.extra.LOG_FILE_PATH"
    }

    private val binding by lazy {
        ActivityCrashReportBinding.inflate(layoutInflater)
    }

    private lateinit var file: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        val path = intent?.getStringExtra(EXTRA_LOG_FILE_PATH)
        if (path.isNullOrEmpty() || !File(path).exists()) {
            finishAfterTransition()
            toast("No error log")
        } else {
            file = File(path)
            binding.btnFeedback.isEnabled = true
            binding.tvAttachName.text = file.name
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching {
                    val list = file.parentFile?.listFiles()!!
                    if (list.size > 10) {
                        list.sortBy { it.lastModified() }
                        if (list[0].path != path) list[0].delete()
                    }
                }.onFailure {
                    it.printStackTrace()
                }
            }
            binding.btnFeedback.setOnClickListener {
                Feedbacks.showErrorFeedbackDialog(this, file)
            }
        }
        binding.btnDismiss.setOnClickListener {
            finishAndRemoveTask()
        }
        binding.btnRestart.setOnClickListener {
            app.launchActivity(MainActivity::class.java) {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finishAndRemoveTask()
    }
}