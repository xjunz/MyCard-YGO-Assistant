package xjunz.tool.mycard.main.tools

import android.annotation.SuppressLint
import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.math.MathUtils
import androidx.core.text.parseAsHtml
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import xjunz.tool.mycard.R
import xjunz.tool.mycard.common.BaseBottomSheetDialog
import xjunz.tool.mycard.common.InputDialog
import xjunz.tool.mycard.databinding.DialogCalculatorBinding
import xjunz.tool.mycard.databinding.ItemCalculatorResultBinding
import xjunz.tool.mycard.databinding.ItemCardConditionBinding
import xjunz.tool.mycard.databinding.ItemConditionSetBinding
import xjunz.tool.mycard.ktx.*
import xjunz.tool.mycard.main.settings.Configs
import java.math.BigDecimal
import java.math.RoundingMode

class ProbabilityCalculatorDialog : BaseBottomSheetDialog<DialogCalculatorBinding>() {

    private val minDeckCardCount = 40
    private val defDeckCardCount = minDeckCardCount
    private val maxDeckCardCount = 60
    private val defHandCardCount = 5
    private val maxHandCardCount = 15

    private val conditionSets = mutableListOf<ConditionSet>()

    private lateinit var currentSelection: ConditionSet

    private inline val conditions get() = currentSelection.conditions

    private val cardsAdapter = CardsAdapter()

    private fun getCurrentFocus() = dialog?.currentFocus

    private var resultSum = BigDecimal.ZERO

    private var latestUsedCollectionName: String? = null
    private var latestUsedCollectionCount: Int? = null

    private val focusChangeListener = View.OnFocusChangeListener { _, _ ->
        val focused = dialog?.currentFocus?.id ?: View.NO_ID in intArrayOf(
            R.id.menu_deck_card_count, R.id.et_hand_cards_count
        )
        binding.btnMinusOne.isEnabled = focused
        binding.btnPlusOne.isEnabled = focused
        binding.btnReset.isEnabled = focused
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun bindViews() = binding.apply {
        menuDeckCardCount.setText(currentSelection.deckCardCount.toString())
        etHandCardsCount.setText(conditions.size.toString())
        if (rvHandCards.adapter == null) {
            rvHandCards.adapter = cardsAdapter
        } else {
            cardsAdapter.notifyDataSetChanged()
        }
    }

    override fun onDialogCreated(dialog: Dialog) {
        initConditionSet()
        initViews()
        bindViews()
    }

    private fun initConditionSet() {
        currentSelection = newDefaultConditionSet()
        conditionSets.add(currentSelection)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initViews() = binding.apply {
        menuDeckCardCount.onFocusChangeListener = focusChangeListener
        etHandCardsCount.onFocusChangeListener = focusChangeListener
        btnReset.setOnClickListener {
            val focus = getCurrentFocus()
            if (focus === binding.etHandCardsCount) focus.setText(defHandCardCount.toString())
            else if (focus === binding.menuDeckCardCount) focus.setText(defDeckCardCount.toString())
        }
        btnMinusOne.setOnClickListener {
            val focus = getCurrentFocus()
            if (focus is EditText) {
                val count = focus.textString.toIntOrNull()
                if (count != null) focus.setText(String.format("%d", count - 1))
            }
        }
        btnPlusOne.setOnClickListener {
            val focus = getCurrentFocus()
            if (focus is EditText) {
                val count = focus.textString.toIntOrNull()
                if (count != null) focus.setText(String.format("%d", count + 1))
            }
        }
        btnDetails.setOnClickListener {
            val sb = StringBuilder()
            conditionSets.filter { it.getResult() != null }.forEach {
                sb.append(it.label).append("(")
                    .append(it.getResult()!!.toPlainString().pruneZero(2))
                    .append(") + ")
            }
            if (sb.isNotEmpty()) {
                sb.delete(sb.lastIndex - 2, sb.lastIndex)
                val fraction = if (resultSum == BigDecimal.ZERO) {
                    R.string.bar.resStr
                } else {
                    "1/" + BigDecimal.ONE.divide(resultSum, 2, RoundingMode.CEILING).toPlainString()
                }
                val msg = R.string.html_sum_details.format(
                    sb.toString(),
                    resultSum.toPlainString().pruneZero(2),
                    fraction,
                    (resultSum * BigDecimal(100)).toPlainString().pruneZero(2) + "%"
                ).parseAsHtml()
                MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.sum_probability)
                    .setMessage(msg).setPositiveButton(android.R.string.ok, null).show()
            }
        }
        btnCalculate.setOnClickListener {
            lifecycleScope.launch {
                it.isEnabled = false
                resultSum = BigDecimal.ZERO
                withContext(Dispatchers.Default) {
                    runCatching {
                        conditionSets.forEach {
                            it.calculateProbability()
                            val ret = it.getResult()
                            if (ret != null) {
                                resultSum += ret
                            }
                        }
                    }
                }.onFailure {
                    toast(R.string.format_unexpected_error.format(it.message))
                }.onSuccess {
                    binding.tvSum.text = resultSum.toPlainString().pruneZero(2)
                    notifyResultsUpdated()
                }
                it.isEnabled = true
            }.invokeOnCompletion {
                if (it is CancellationException) {
                    currentSelection.cancelCalculating()
                }
            }
        }
        btnNew.setOnClickListener {
            if (conditionSets.size >= Configs.MAX_CONDITION_SET_COUNT) {
                toast(R.string.prompt_reach_max_limit)
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(requireContext()).setMessage(
                R.string.prompt_retain_current
            ).setTitle(R.string.prompt).setPositiveButton(android.R.string.ok) { _, _ ->
                addConditionSets(true)
            }.setNegativeButton(R.string.do_not_retain) { _, _ ->
                addConditionSets(false)
            }.setNeutralButton(android.R.string.cancel, null).show()
        }
        menuDeckCardCount.setEntries((minDeckCardCount..maxDeckCardCount step 5).toList())
        menuDeckCardCount.threshold = Int.MAX_VALUE
        etHandCardsCount.doAfterTextChanged {
            val count = it.toString().toInt()
            val clamped = MathUtils.clamp(count, 1, maxHandCardCount)
            if (clamped != count) {
                etHandCardsCount.setText(clamped.toString())
            } else {
                val delta = count - conditions.size
                if (delta == 0) return@doAfterTextChanged
                if (delta > 0) {
                    for (i in 0 until delta) {
                        conditions.add(Condition(type = Condition.TYPE_ANY_IN_DECK))
                    }
                    cardsAdapter.notifyItemRangeInserted(conditions.size - delta, delta)
                    cardsAdapter.notifyItemRangeChanged(0, conditions.size - delta, 1)
                    rvHandCards.post {
                        rvHandCards.scrollToPosition(conditions.size - 1)
                    }
                } else if (delta < 0) {
                    for (i in 0 until -delta) {
                        conditions.removeLast()
                    }
                    cardsAdapter.notifyItemRangeRemoved(conditions.size, -delta)
                    cardsAdapter.notifyItemRangeChanged(0, conditions.size, 1)
                }
            }
        }
        menuDeckCardCount.doAfterTextChanged {
            val count = it.toString().toInt()
            val clamped = MathUtils.clamp(count, minDeckCardCount, maxDeckCardCount)
            if (clamped != count) {
                menuDeckCardCount.setText(clamped.toString())
            } else {
                currentSelection.deckCardCount = count
            }
        }
        binding.rvConditions.adapter = conditionSetAdapter
        binding.btnViewExample.setOnClickListener {

            var checkedItem = 0
            MaterialAlertDialogBuilder(requireContext()).setSingleChoiceItems(
                R.array.calculator_examples,
                0
            ) { _, which ->
                checkedItem = which
            }.setTitle(R.string.examples).setPositiveButton(android.R.string.ok) { _, _ ->

                fun select() {
                    val json = resources.openRawResource(R.raw.calculator_examples).readBytes()
                        .decodeToString()
                    val current = Json.decodeFromString<Array<ConditionSet>>(json)[checkedItem]
                    conditionSets.clear()
                    conditionSets.add(current)
                    currentSelection = current
                    bindViews()
                    resultsAdapter.notifyDataSetChanged()
                    conditionSetAdapter.notifyDataSetChanged()
                }
                if (conditionSets.size > 1) {
                    requireContext().showSimplePromptDialog(
                        msg = R.string.prompt_discard_condition_sets
                    ) {
                        select()
                    }
                } else {
                    select()
                }
            }.setNegativeButton(android.R.string.cancel, null).show()
        }
    }

    private var unnamedIndex = 0

    private fun newDefaultConditionSet(): ConditionSet {
        val defConditions = mutableListOf<Condition>()
        for (i in 1..defHandCardCount) {
            defConditions.add(Condition())
        }
        return ConditionSet(
            defConditions, defDeckCardCount,
            R.string.format_unnamed_condition_set.format(++unnamedIndex)
        )
    }

    private fun addConditionSets(copyPrevious: Boolean = false) {
        val oldIndex = conditionSets.indexOf(currentSelection)
        val insertion = oldIndex + 1
        val newSet = if (!copyPrevious || oldIndex == -1) {
            newDefaultConditionSet()
        } else {
            currentSelection.copy()
        }
        newSet.label = R.string.format_unnamed_condition_set.format(++unnamedIndex)
        conditionSets.add(insertion, newSet)
        currentSelection = newSet
        notifyConditionSetAdded(oldIndex, insertion)
        bindViews()
    }

    private fun notifyConditionSetAdded(oldIndex: Int, newIndex: Int) {
        conditionSetAdapter.notifyItemChanged(oldIndex, 1)
        conditionSetAdapter.notifyItemInserted(newIndex)
        resultsAdapter.notifyItemInserted(newIndex)
        resultsAdapter.notifyItemChanged(oldIndex, 1)
        binding.rvResult.post {
            binding.rvResult.scrollPositionToCenter(newIndex, true)
        }
        binding.rvConditions.post {
            binding.rvConditions.scrollPositionToCenter(newIndex, true)
        }
    }

    private fun notifyResultsUpdated() {
        binding.rvResult.rootView.beginDelayedTransition()
        if (!binding.rvResult.isVisible) {
            binding.rvResult.isVisible = true
        }
        if (binding.rvResult.adapter == null) {
            binding.rvResult.adapter = resultsAdapter
        } else {
            resultsAdapter.notifyItemRangeChanged(0, conditionSets.size)
        }
        binding.btnDetails.isEnabled = true
    }

    private var itemHeight = -1

    private inner class CardsAdapter : RecyclerView.Adapter<CardViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
            return CardViewHolder(
                ItemCardConditionBinding.inflate(layoutInflater, parent, false)
            )
        }

        override fun onBindViewHolder(
            holder: CardViewHolder, position: Int, payloads: MutableList<Any>
        ) {
            if (payloads.isNotEmpty()) {
                holder.itemBinding.tvOrdinal.text = String.format("%d", position + 1)
            } else {
                super.onBindViewHolder(holder, position, payloads)
            }
        }

        override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
            holder.itemBinding.apply {
                val condition = conditions[position]
                if (condition.type == Condition.TYPE_ANY_IN_COLLECTION) {
                    tvDescription.text = (
                            if (condition.isInverted) R.string.format_not_any_in_collection
                            else R.string.format_any_in_collection
                            ).format(condition.collectionName, condition.collectionCount)
                } else {
                    tvDescription.text = condition.getDescription()
                }
                tvRemark.isVisible = !condition.remark.isNullOrEmpty()
                tvRemark.text = condition.remark
                tvOrdinal.text = String.format("%d", position + 1)
            }
        }

        override fun getItemCount() = conditions.size
    }

    private inner class CardViewHolder(val itemBinding: ItemCardConditionBinding) :
        RecyclerView.ViewHolder(itemBinding.root) {
        init {
            itemBinding.root.setOnClickListener {
                if (adapterPosition == RecyclerView.NO_POSITION) return@setOnClickListener
                val condition = conditions[adapterPosition]
                val oldCount = condition.collectionCount
                CardConditionEditorDialog()
                    .setExistingCollectionNames((conditions.asSequence().filter {
                        !it.collectionName.isNullOrEmpty()
                    }.map {
                        it.collectionName!!
                    }.toList() + R.array.preset_collection_names.resArray).distinct())
                    .setArguments(
                        condition, binding.menuDeckCardCount.textString.toInt()
                    ).setDefCollection(latestUsedCollectionName, latestUsedCollectionCount)
                    .doOnConfirmed { newCondition ->
                        conditions[adapterPosition] = newCondition
                        cardsAdapter.notifyItemChanged(adapterPosition)
                        if (newCondition.collectionCount != oldCount) {
                            conditionSets.flatMap { it.conditions }.forEach {
                                if (it.collectionName == newCondition.collectionName) {
                                    it.collectionCount = newCondition.collectionCount
                                }
                            }
                            cardsAdapter.notifyItemRangeChanged(0, conditions.size)
                        }
                        if (newCondition.collectionName != null) {
                            latestUsedCollectionName = newCondition.collectionName
                            latestUsedCollectionCount = newCondition.collectionCount
                        }
                    }.show(childFragmentManager, "condition-editor")
            }
            itemBinding.ibDelete.setOnClickListener {
                if (adapterPosition == RecyclerView.NO_POSITION) return@setOnClickListener
                requireActivity().showSimplePromptDialog(msg = R.string.prompt_delete_criteria) {
                    conditions.removeAt(adapterPosition)
                    cardsAdapter.notifyItemRemoved(adapterPosition)
                    cardsAdapter.notifyItemRangeChanged(0, conditions.size, 1)
                    binding.etHandCardsCount.setText(conditions.size.toString())
                }
            }
            itemBinding.root.doOnPreDraw {
                if (itemHeight != -1) return@doOnPreDraw
                itemHeight = it.height
                binding.cvContainer.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    height = itemHeight * 5
                }
            }
        }
    }

    private fun String.pruneZero(digits: Int): String {
        val sb = StringBuilder()
        var dotIndex = -1
        var zeroIndex = -1
        var stopLen = 0
        forEachIndexed { index, c ->
            when {
                c == '.' -> {
                    dotIndex = index
                    sb.append(c)
                }
                dotIndex != -1 -> when {
                    c == '0' -> when {
                        index - dotIndex <= digits -> sb.append(c)
                        zeroIndex == -1 -> {
                            zeroIndex = index
                            stopLen = 1
                        }
                        else -> stopLen++
                    }
                    zeroIndex != -1 -> {
                        sb.append(substring(zeroIndex, zeroIndex + stopLen + 1))
                        zeroIndex = -1
                    }
                    else -> sb.append(c)
                }
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private val resultsAdapter by lazy { ResultsAdapter() }

    private inner class ResultsAdapter : RecyclerView.Adapter<ResultsAdapter.ResultViewHolder>() {

        private inner class ResultViewHolder(val binding: ItemCalculatorResultBinding) :
            RecyclerView.ViewHolder(binding.root) {

            init {
                binding.chipResult.setOnClickListener {
                    val conditionSet = conditionSets[adapterPosition]
                    val ret = conditionSet.getResult()
                    val msg = if (ret == null) {
                        R.string.result_not_calculated.resText
                    } else {
                        val r = BigDecimal.ONE / ret
                        val p = ret * BigDecimal(100)
                        R.string.html_detailed_calculator_result.format(
                            ret.toPlainString().pruneZero(2),
                            "1/" + r.toPlainString(),
                            p.toPlainString().pruneZero(2) + "%"
                        ).parseAsHtml()
                    }
                    MaterialAlertDialogBuilder(requireContext()).setTitle(
                        R.string.format_calculator_result.format(conditionSet.label)
                    ).setPositiveButton(android.R.string.ok, null).setMessage(msg).show()
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
            return ResultViewHolder(
                ItemCalculatorResultBinding.inflate(
                    layoutInflater, parent, false
                )
            )
        }

        override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
            val cs = conditionSets[position]
            val ret = cs.getResult()
            holder.binding.chipResult.text = ret?.toPlainString()?.pruneZero(2) ?: "?"
            holder.binding.ivPlus.isVisible = position != 0
            holder.binding.chipResult.isActivated = cs === currentSelection
        }

        override fun getItemCount() = conditionSets.size
    }

    private fun notifyConditionSetRemoved(index: Int) {
        conditionSetAdapter.notifyItemRemoved(index)
        resultsAdapter.notifyItemRemoved(index)
    }

    private val conditionSetAdapter by lazy { ConditionSetAdapter() }

    private inner class ConditionSetAdapter :
        RecyclerView.Adapter<ConditionSetAdapter.ChipViewHolder>() {

        private inner class ChipViewHolder(val item: ItemConditionSetBinding) :
            RecyclerView.ViewHolder(item.root) {
            init {
                item.root.setOnCloseIconClickListener {
                    val data = conditionSets[adapterPosition]
                    requireActivity().showSimplePromptDialog(msg = R.string.prompt_remove_condition_set) {
                        val curIndex = conditionSets.indexOf(data)
                        check(curIndex >= 0)
                        conditionSets.removeAt(curIndex)
                        notifyConditionSetRemoved(curIndex)
                        if (it.isActivated) {
                            val nextIndex =
                                if (conditionSets.lastIndex >= curIndex) curIndex else curIndex - 1
                            currentSelection = conditionSets[nextIndex]
                            bindViews()
                            notifyItemChanged(nextIndex, 1)
                            resultsAdapter.notifyItemChanged(nextIndex, 1)
                            binding.rvConditions.scrollPositionToCenter(nextIndex, true)
                            binding.rvResult.scrollPositionToCenter(nextIndex, true)
                        }
                    }
                }
                item.root.setOnClickListener {
                    val newSelection = conditionSets[adapterPosition]
                    if (currentSelection === newSelection) {
                        InputDialog().apply {
                            setInitialInput(newSelection.label)
                            setPositiveButton { text ->
                                newSelection.label = text
                                notifyItemChanged(adapterPosition, 1)
                                return@setPositiveButton null
                            }
                        }.show(parentFragmentManager, "label")
                    } else {
                        val oldIndex = conditionSets.indexOf(currentSelection)
                        currentSelection = conditionSets[adapterPosition]
                        bindViews()
                        notifyItemChanged(adapterPosition)
                        notifyItemChanged(oldIndex)
                        resultsAdapter.notifyItemChanged(oldIndex)
                        resultsAdapter.notifyItemChanged(adapterPosition)
                        binding.rvConditions.scrollPositionToCenter(adapterPosition, true)
                        binding.rvResult.scrollPositionToCenter(adapterPosition, true)
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChipViewHolder {
            return ChipViewHolder(ItemConditionSetBinding.inflate(layoutInflater, parent, false))
        }

        override fun onBindViewHolder(holder: ChipViewHolder, position: Int) {
            val cs = conditionSets[position]
            holder.item.root.apply {
                isCloseIconVisible = conditionSets.size > 1
                text = cs.label
                isActivated = cs === currentSelection
                updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    marginStart = if (position == 0) 0 else 8.dp
                }
            }
        }

        override fun getItemCount() = conditionSets.size
    }
}