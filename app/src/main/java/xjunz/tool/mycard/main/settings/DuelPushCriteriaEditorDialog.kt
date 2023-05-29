package xjunz.tool.mycard.main.settings

import android.app.Dialog
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.TransitionManager
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withCreated
import kotlinx.coroutines.launch
import xjunz.tool.mycard.R
import xjunz.tool.mycard.common.BaseBottomSheetDialog
import xjunz.tool.mycard.databinding.DialogCriteriaEditorBinding
import xjunz.tool.mycard.ktx.lazyViewModel
import xjunz.tool.mycard.ktx.setEntries
import xjunz.tool.mycard.monitor.push.DuelFilterCriteria
import xjunz.tool.mycard.util.Motions

/**
 * @author xjunz 2022/3/23
 */
class DuelPushCriteriaEditorDialog : BaseBottomSheetDialog<DialogCriteriaEditorBinding>() {

    class DuelPushCriteriaViewModel : ViewModel() {

        lateinit var onConfirmed: (DuelFilterCriteria) -> Unit

        var criteria2edit: DuelFilterCriteria? = null
    }

    private val viewModel by lazyViewModel<DuelPushCriteriaViewModel>()

    private val criteria2edit: DuelFilterCriteria? get() = viewModel.criteria2edit

    private val mixin by lazy {
        PlayerCriteriaBindingMixin(
            arrayOf(binding.cardPlayer1, binding.cardPlayer2), binding.etPushDelay
        )
    }

    fun editCriteria(criteria: DuelFilterCriteria): DuelPushCriteriaEditorDialog {
        lifecycleScope.launch {
            lifecycle.withCreated {
                viewModel.criteria2edit = criteria
            }
        }
        return this
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogStyle)
    }

    override fun onDialogCreated(dialog: Dialog) {
        initViews()
    }

    private fun initViews() {
        mixin.init(criteria2edit, binding.btnConfirm, false, viewModel.onConfirmed)
        if (!isInEditMode) {
            binding.tilPreset.isVisible = true
            binding.menuPresetCriteria.setEntries(R.array.preset_criteria) {
                TransitionManager.beginDelayedTransition(
                    dialog?.window!!.findViewById(android.R.id.content),
                    ChangeBounds().setInterpolator(Motions.EASING_EMPHASIZED)
                )
                mixin.bind(DuelFilterCriteria.PRESET_CRITERIA_ARRAY[it].copy())
            }
        }
        criteria2edit?.let {
            if (it.pushDelayInMinute != 0) binding.etPushDelay.setText(it.pushDelayInMinute.toString())
        }
        binding.btnDiscard.setOnClickListener {
            dismiss()
        }
    }

    private inline val isInEditMode get() = criteria2edit != null

    fun doOnConfirmed(block: (DuelFilterCriteria) -> Unit): DuelPushCriteriaEditorDialog {
        lifecycleScope.launch {
            lifecycle.withCreated {
                viewModel.onConfirmed = block
            }
        }
        return this
    }


}