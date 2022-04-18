package xjunz.tool.mycard.main.tools

import android.app.Dialog
import android.text.InputType
import android.util.SparseBooleanArray
import android.view.View
import android.widget.ImageButton
import androidx.core.text.parseAsHtml
import androidx.core.util.set
import androidx.core.view.children
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import xjunz.tool.mycard.R
import xjunz.tool.mycard.common.BaseBottomSheetDialog
import xjunz.tool.mycard.common.InputDialog
import xjunz.tool.mycard.databinding.DialogSingleCardConditionBinding
import xjunz.tool.mycard.ktx.*
import xjunz.tool.mycard.main.settings.Configs

class CardConditionEditorDialog : BaseBottomSheetDialog<DialogSingleCardConditionBinding>() {

    fun setArguments(condition: Condition, deckCardCount: Int): CardConditionEditorDialog {
        lifecycleScope.launchWhenCreated {
            vm.edition = condition.copy()
            vm.deckCardCount = deckCardCount
        }
        return this
    }

    private class InnerViewModel : ViewModel() {
        lateinit var edition: Condition
        lateinit var doOnConfirmed: (Condition) -> Unit
        var deckCardCount = 0
        var existingCollectionNames = emptyList<String>()
    }

    private val vm by lazyInnerViewModel<InnerViewModel>()

    private inline val edition get() = vm.edition

    private val invertingStates = SparseBooleanArray(2)

    private fun bindViews() = binding.apply {
        tvDescription1.text = edition.getDescription(Condition.TYPE_SPEC_IN_DECK)
        tvDescription2.text = edition.getDescription(Condition.TYPE_ANY_IN_COLLECTION)
        tvCollectionName.text = R.string.format_collection_name.format(
            edition.collectionName ?: R.string.html_undefined.resStr
        ).parseAsHtml()
        tvCollectionCardCount.text = R.string.format_collection_card_count.format(
            edition.collectionCount
        )
        etRemark.setText(edition.remark)
        invertingStates[edition.type] = edition.isInverted
        notifySelected(edition.type)
    }

    private fun getViewForType(@Condition.Type type: Int) = when (type) {
        Condition.TYPE_ANY_IN_DECK -> binding.containerAnyCard
        Condition.TYPE_SPEC_IN_DECK -> binding.containerSpecCard
        Condition.TYPE_ANY_IN_COLLECTION -> binding.containerAnyInCollection
        else -> throw IllegalArgumentException("invalid type")
    }

    private fun getTypeFromView(view: View) = when (view) {
        binding.containerAnyCard -> Condition.TYPE_ANY_IN_DECK
        binding.containerSpecCard -> Condition.TYPE_SPEC_IN_DECK
        binding.containerAnyInCollection -> Condition.TYPE_ANY_IN_COLLECTION
        else -> throw IllegalArgumentException("invalid view")
    }

    fun setDefCollection(
        defCollectionName: String?,
        defCount: Int?
    ): CardConditionEditorDialog {
        lifecycleScope.launchWhenCreated {
            val hasCollection = vm.edition.collectionName != null
            if (!hasCollection) {
                vm.edition.collectionName = defCollectionName
            }
            if (defCount != null && !hasCollection) vm.edition.collectionCount = defCount
        }
        return this
    }

    private fun initViews() {
        binding.btnCancel.setOnClickListener {
            dismiss()
        }
        val onClickListener = { view: View ->
            edition.type = getTypeFromView(view)
            edition.isInverted = invertingStates[edition.type]
            notifySelected(edition.type)
        }
        binding.containerAnyCard.setOnClickListener(onClickListener)
        binding.containerSpecCard.setOnClickListener(onClickListener)
        binding.containerAnyInCollection.setOnClickListener(onClickListener)
        binding.ibEditCount.setOnClickListener {
            InputDialog().apply {
                setTitle(R.string.edit_card_count.resText)
                setInitialInput(edition.collectionCount.toString())
                setInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
                setMaxLength(2)
                setDropDownData((2..15).map { it.toString() })
                setPositiveButton {
                    if (it.isEmpty()) {
                        return@setPositiveButton R.string.input_is_empty.resStr
                    }
                    val count = it.toInt()
                    if (count > vm.deckCardCount) {
                        return@setPositiveButton R.string.input_too_large.resStr
                    }
                    if (count < 2) {
                        return@setPositiveButton R.string.format_input_too_small.format(2)
                    }
                    edition.collectionCount = count
                    binding.tvCollectionCardCount.text =
                        R.string.format_collection_card_count.format(count)
                    return@setPositiveButton null
                }
            }.show(parentFragmentManager, "input")
        }
        binding.ibEditName.setOnClickListener {
            InputDialog().apply {
                setTitle(R.string.edit_collection_name.resText)
                setInitialInput(edition.collectionName)
                setDropDownData(vm.existingCollectionNames)
                setMaxLength(Configs.MAX_COLLECTION_TEXT_LENGTH)
                setPositiveButton {
                    if (it.isBlank()) return@setPositiveButton R.string.input_is_empty.resStr
                    binding.tvCollectionName.text = R.string.format_collection_name.format(it)
                    edition.collectionName = it
                    return@setPositiveButton null
                }
            }.show(parentFragmentManager, "input")
        }
        binding.ibInvert1.setOnClickListener {
            val next = !invertingStates[Condition.TYPE_SPEC_IN_DECK]
            invertingStates[Condition.TYPE_SPEC_IN_DECK] = next
            if (edition.type == Condition.TYPE_SPEC_IN_DECK) edition.isInverted = next
            binding.tvDescription1.text = edition.getDescription(Condition.TYPE_SPEC_IN_DECK, next)
        }
        binding.ibInvert2.setOnClickListener {
            val next = !invertingStates[Condition.TYPE_ANY_IN_COLLECTION]
            invertingStates[Condition.TYPE_ANY_IN_COLLECTION] = next
            if (edition.type == Condition.TYPE_ANY_IN_COLLECTION) edition.isInverted = next
            binding.tvDescription2.text =
                edition.getDescription(Condition.TYPE_ANY_IN_COLLECTION, next)
        }
        binding.etRemark.doAfterTextChanged {
            edition.remark = it?.toString()
        }
        binding.btnOk.setOnClickListener {
            if (edition.type == Condition.TYPE_ANY_IN_COLLECTION
                && edition.collectionName.isNullOrEmpty()
            ) {
                binding.tvCollectionName.shake()
                toast(R.string.prompt_empty_collection_name)
                return@setOnClickListener
            }
            vm.doOnConfirmed(edition)
            dismiss()
        }
    }

    override fun onDialogCreated(dialog: Dialog) {
        bindViews()
        initViews()
    }

    private fun notifySelected(type: Int) {
        for (i in Condition.TYPES) {
            val view = getViewForType(i)
            view.isSelected = i == type
            view.children.filter { it is ImageButton }.forEach {
                it.isEnabled = i == type
            }
        }
    }

    fun setExistingCollectionNames(names: List<String>): CardConditionEditorDialog {
        lifecycleScope.launchWhenCreated {
            vm.existingCollectionNames = names
        }
        return this
    }

    fun doOnConfirmed(block: (Condition) -> Unit): CardConditionEditorDialog {
        lifecycleScope.launchWhenCreated {
            vm.doOnConfirmed = block
        }
        return this
    }
}