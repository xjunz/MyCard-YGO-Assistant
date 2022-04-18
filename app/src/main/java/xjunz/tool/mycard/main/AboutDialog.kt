package xjunz.tool.mycard.main

import android.os.Bundle
import android.text.format.Formatter
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.parseAsHtml
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import xjunz.tool.mycard.BuildConfig
import xjunz.tool.mycard.Constants
import xjunz.tool.mycard.R
import xjunz.tool.mycard.databinding.DialogAboutBinding
import xjunz.tool.mycard.ktx.format
import xjunz.tool.mycard.ktx.toast
import xjunz.tool.mycard.ktx.viewUrlSafely
import xjunz.tool.mycard.model.UpdateInfo
import xjunz.tool.mycard.outer.UpdateChecker

/**
 * @author xjunz 2022/04/17
 */
class AboutDialog : DialogFragment() {

    private val updateChecker by lazy {
        UpdateChecker(viewLifecycleOwner.lifecycle)
    }

    private lateinit
    var binding: DialogAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.DialogTheme_Fragment)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = DialogAboutBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.apply {
            var version = "v${BuildConfig.VERSION_NAME}"
            if (BuildConfig.DEBUG) version += "-debug"
            tvVersionName.text = version
            tvCredits.movementMethod = LinkMovementMethod.getInstance()
            tvCaption.movementMethod = LinkMovementMethod.getInstance()
            btnUpdate.setOnClickListener {
                btnUpdate.isEnabled = false
                lifecycleScope.launch {
                    updateChecker.checkUpdate().onSuccess {
                        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.has_updates)
                            .setMessage(it.formatUpdateInfo())
                            .setPositiveButton(R.string.download) { _, _ ->
                                requireContext().viewUrlSafely(Constants.APP_DOWNLOAD_URL)
                            }.setNegativeButton(android.R.string.cancel, null).show()
                    }.onFailure {
                        toast(R.string.check_update_failed)
                    }
                    btnUpdate.isEnabled = true
                }
            }
            btnDonate.setOnClickListener {
                requireContext().viewUrlSafely(Constants.ALIPAY_DONATE_URL)
            }
        }
    }

    private fun UpdateInfo.formatUpdateInfo(): CharSequence {
        return R.string.html_updates_info.format(
            versionShort, Formatter.formatFileSize(requireContext(), binary.size), changelog
        ).parseAsHtml()
    }

}