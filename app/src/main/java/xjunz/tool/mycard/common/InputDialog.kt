package xjunz.tool.mycard.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import kotlinx.coroutines.launch
import xjunz.tool.mycard.R
import xjunz.tool.mycard.databinding.DialogInputBinding
import xjunz.tool.mycard.ktx.*

/**
 * @author xjunz 2022/3/10
 */
class InputDialog : DialogFragment() {

    private lateinit var binding: DialogInputBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.DialogTheme_Fragment)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogInputBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnNegative.setOnClickListener { dismiss() }
        binding.btnPositive.setOnClickListener { dismiss() }
        binding.etInput.requestFocus()
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        binding.etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                binding.btnPositive.performClick()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }
    }

    fun setTitle(title: CharSequence) = lifecycleScope.launch {
        lifecycle.withStarted {
            binding.tvTitle.text = title
        }
    }

    fun setNegativeAsImportant(): InputDialog {
        lifecycleScope.launch {
            lifecycle.withStarted {
                val colorError =
                    requireContext().resolveAttribute(com.google.android.material.R.attr.colorError)
                        .resColor.asStateList
                binding.btnNegative.strokeColor = colorError
                binding.btnNegative.setTextColor(colorError)
            }
        }
        return this
    }

    var allowEmptyResult = false

    fun setMaxLength(max: Int) = lifecycleScope.launch {
        lifecycle.withStarted {
            binding.etInput.setMaxLength(max)
        }
    }

    fun setDropDownData(preset: List<String>) = lifecycleScope.launch {
        lifecycle.withStarted {
            binding.etInput.setAdapter(
                DropdownArrayAdapter(
                    requireContext(),
                    preset.toMutableList()
                )
            )
        }
    }

    /**
     * @param block Deal with the result and return the error string. If the error string is empty,
     * the dialog would be dismissed.
     */
    fun setPositiveButton(text: CharSequence? = null, block: (String) -> String?) =
        lifecycleScope.launch {
            lifecycle.withStarted {
                if (text != null) binding.btnNegative.text = text
                binding.btnPositive.setOnClickListener {
                    if (binding.etInput.text.isNullOrBlank()) {
                        binding.tilInput.error = R.string.input_is_empty.resText
                        dismiss()
                        return@setOnClickListener
                    }
                    binding.tilInput.error = null
                    val ret = block(binding.etInput.text.toString())
                    if (ret == null) dismiss() else binding.tilInput.error = ret
                }
            }
        }

    fun setInputType(inputType: Int) = lifecycleScope.launch {
        lifecycle.withStarted {
            binding.etInput.inputType = inputType
        }
    }

    fun setInitialInput(text: CharSequence?) = lifecycleScope.launch {
        lifecycle.withStarted {
            binding.etInput.setText(text)
            binding.etInput.setSelection(text?.length ?: 0)
        }
    }

    fun setHint(hint: CharSequence) = lifecycleScope.launch {
        lifecycle.withStarted {
            binding.tilInput.hint = hint
        }
    }

    /**
     * @param onClick If returns true, the dialog would be dismissed.
     */
    fun setNegativeButton(text: CharSequence? = null, onClick: () -> Boolean) =
        lifecycleScope.launch {
            lifecycle.withStarted {
                if (text != null) binding.btnNegative.text = text
                binding.btnNegative.setOnClickListener {
                    if (onClick()) dismiss()
                }
            }
        }

}