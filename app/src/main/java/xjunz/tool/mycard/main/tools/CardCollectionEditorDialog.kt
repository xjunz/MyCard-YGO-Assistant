package xjunz.tool.mycard.main.tools

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withCreated
import androidx.lifecycle.withStarted
import kotlinx.coroutines.launch
import xjunz.tool.mycard.R
import xjunz.tool.mycard.common.DropdownArrayAdapter
import xjunz.tool.mycard.databinding.DialogCardColletionEditorBinding
import xjunz.tool.mycard.ktx.*
import xjunz.tool.mycard.main.settings.Configs

/**
 * @author xjunz 2022/05/27
 */
class CardCollectionEditorDialog : DialogFragment() {

    class InnerViewModel : ViewModel() {

        var initialName: CharSequence? = null

        var initialCount: Int = 0
    }

    private val viewModel by lazyViewModel<InnerViewModel>()

    private lateinit var binding: DialogCardColletionEditorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.DialogTheme_Fragment)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogCardColletionEditorBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    fun setArguments(initialName: CharSequence?, initialCount: Int): CardCollectionEditorDialog {
        lifecycleScope.launch {
            lifecycle.withCreated {
                viewModel.initialName = initialName
                viewModel.initialCount = initialCount
            }
        }
        return this
    }

    fun setCollectionNameDropdown(preset: List<String>): CardCollectionEditorDialog {
        lifecycleScope.launch {
            lifecycle.withCreated {
                binding.etNameInput.setDropDownData(preset)
            }
        }
        return this
    }

    private fun AutoCompleteTextView.setDropDownData(preset: List<String>) {
        setAdapter(
            DropdownArrayAdapter(requireContext(), preset.toMutableList())
        )
    }

    fun setOnConfirmedListener(
        nameChecker: (String) -> String?,
        countChecker: (String) -> String?
    ): CardCollectionEditorDialog {
        lifecycleScope.launch {
            lifecycle.withStarted {
                binding.btnPositive.setOnClickListener {
                    val name = binding.etNameInput.textString
                    val nameError = nameChecker(name)
                    if (nameError != null) {
                        binding.tilName.shake()
                        toast(nameError)
                        return@setOnClickListener
                    }
                    val count = binding.etCountInput.textString
                    val countError = countChecker(count)
                    if (countError != null) {
                        binding.tilCount.shake()
                        toast(countError)
                        return@setOnClickListener
                    }
                    dismiss()
                }
            }
        }
        return this
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.etCountInput.setText(viewModel.initialCount.toString())
        binding.etNameInput.setText(viewModel.initialName)
        binding.etNameInput.setMaxLength(2)
        binding.etCountInput.setDropDownData((2..15).map { it.toString() })
        binding.etNameInput.setMaxLength(Configs.MAX_COLLECTION_TEXT_LENGTH)
        binding.etNameInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                binding.etNameInput.clearFocus()
                binding.etCountInput.requestFocus()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }
        binding.btnNegative.setOnClickListener {
            dismiss()
        }
    }
}