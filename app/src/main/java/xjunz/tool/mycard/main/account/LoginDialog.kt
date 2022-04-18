package xjunz.tool.mycard.main.account

import android.app.Dialog
import android.view.KeyEvent
import android.widget.Button
import androidx.core.view.forEach
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.transition.platform.MaterialSharedAxis
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xjunz.tool.mycard.R
import xjunz.tool.mycard.common.BaseBottomSheetDialog
import xjunz.tool.mycard.databinding.DialogLoginBinding
import xjunz.tool.mycard.ktx.*
import xjunz.tool.mycard.util.HttpStatusCodeException

class LoginDialog : BaseBottomSheetDialog<DialogLoginBinding>() {

    private val client by lazy { LoginClient(lifecycle) }

    override fun onDialogCreated(dialog: Dialog) {
        binding.etUsername.setText(AccountManager.getRememberedUsername())
        binding.btnDismiss.setOnClickListener { dismiss() }
        binding.btnLogin.setOnClickListener {
            val inputUsername = binding.etUsername.textString
            val inputPassword = binding.etPwd.textString
            when {
                inputUsername.isBlank() -> {
                    binding.tilUsername.error = R.string.field_cannot_be_null.resText
                }
                inputPassword.length < 8 -> {
                    binding.tilUsername.error = null
                    binding.tilPassword.error = R.string.input_too_short.resText
                }
                else -> {
                    binding.tilUsername.error = null
                    binding.tilPassword.error = null
                    lifecycleScope.launch {
                        runCoerceAtLeastMills(2_200) {
                            startTransition(true)
                            client.login(inputUsername, inputPassword)
                        }.onSuccess {
                            toast(R.string.log_in_succeeded)
                            dismiss()
                            onSuccess?.invoke()
                        }.onFailure {
                            startTransition(false)
                            promptError(it)
                        }
                    }
                }
            }
        }
        binding.etPwd.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                v.clearFocus()
                binding.btnLogin.requestFocus()
                binding.btnLogin.performClick()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }
    }

    private var onSuccess: (() -> Unit)? = null

    fun doOnSuccess(callback: () -> Unit): LoginDialog {
        onSuccess = callback
        return this
    }

    private fun promptError(t: Throwable?) {
        when (t) {
            is LoginClient.LoginCredentialException -> {
                longToast(R.string.username_or_pwd_incorrect)
            }
            is HttpStatusCodeException -> {
                longToast(R.string.format_error_code.format(t.code))
            }
            is TimeoutCancellationException -> longToast(R.string.request_timed_out)
            else -> longToast(R.string.format_unexpected_error.format(t?.message))
        }
    }

    private suspend inline fun <T> runCoerceAtLeastMills(mills: Long, action: () -> T): T {
        val start = System.currentTimeMillis()
        val ret = action.invoke()
        val remaining = mills - System.currentTimeMillis() + start
        if (remaining > 0) delay(remaining)
        return ret
    }

    private fun startTransition(loadingMode: Boolean) {
        binding.container.beginDelayedTransition(
            MaterialSharedAxis(MaterialSharedAxis.X, true)
        )
        binding.tvTitle.setText(if (loadingMode) R.string.logging_in else R.string.log_in_to_mycard)
        binding.cpiLoad.isVisible = loadingMode
        binding.container.forEach {
            if (it is TextInputLayout || it is Button) it.isInvisible = loadingMode
        }
    }

    override fun onPause() {
        super.onPause()
        AccountManager.rememberUsername(binding.etUsername.textString)
    }
}