package xjunz.tool.mycard.main.filter

import android.app.Dialog
import androidx.core.widget.doAfterTextChanged
import xjunz.tool.mycard.R
import xjunz.tool.mycard.common.BaseBottomSheetDialog
import xjunz.tool.mycard.databinding.DialogDuelListFilterBinding
import xjunz.tool.mycard.ktx.lazyActivityViewModel
import xjunz.tool.mycard.ktx.resArray
import xjunz.tool.mycard.ktx.setEntries
import xjunz.tool.mycard.main.MainViewModel
import xjunz.tool.mycard.main.settings.Configs
import xjunz.tool.mycard.main.settings.PlayerCriteriaBindingMixin

/**
 * @author xjunz 2023/05/25
 */
class DuelListFilterDialog : BaseBottomSheetDialog<DialogDuelListFilterBinding>() {

    private val mvm by lazyActivityViewModel<MainViewModel>()

    private lateinit var criteriaClone: DuelListFilterCriteria

    private val mixin by lazy {
        PlayerCriteriaBindingMixin(
            arrayOf(binding.cardPlayer1, binding.cardPlayer2), binding.etDuelDuration
        )
    }

    private fun initMenus() {
        val sortBys = R.array.duel_list_sort_by.resArray
        binding.menuSortBy.setEntries(sortBys) {
            criteriaClone.sortBy = it
        }
        binding.menuSortBy.threshold = Int.MAX_VALUE
        binding.menuSortBy.setText(sortBys[criteriaClone.sortBy])
        val orders = R.array.orders.resArray
        binding.menuOrder.setEntries(orders) {
            criteriaClone.isAscending = it == 0
        }
        binding.etKeyword.setText(criteriaClone.keyword)
        binding.etKeyword.doAfterTextChanged {
            criteriaClone.keyword = it?.toString()?.takeIf { str -> str.isNotEmpty() }
        }
        binding.menuOrder.threshold = Int.MAX_VALUE
        binding.menuOrder.setText(orders[if (criteriaClone.isAscending) 0 else 1])
    }

    override fun onDialogCreated(dialog: Dialog) {
        criteriaClone = Configs.duelListFilter.deepClone()
        initMenus()
        mixin.init(criteriaClone.duelCriteria, binding.btnConfirm, true) {
            criteriaClone.duelCriteria = it
            if (Configs.duelListFilter != criteriaClone) {
                Configs.duelListFilter = criteriaClone
            }
            mvm.notifyDuelListFilterChanged()
            dismiss()
        }
        mixin.bind(criteriaClone.duelCriteria)
        binding.btnDiscard.setOnClickListener {
            criteriaClone = DuelListFilterCriteria()
            initMenus()
            mixin.bind(criteriaClone.duelCriteria)
        }
        binding.ibClose.setOnClickListener {
            dismiss()
        }
    }
}