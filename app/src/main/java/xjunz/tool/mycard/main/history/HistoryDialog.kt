package xjunz.tool.mycard.main.history

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.DashPathEffect
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.core.content.ContextCompat
import androidx.core.text.buildSpannedString
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.withCreated
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xjunz.tool.mycard.R
import xjunz.tool.mycard.common.BaseBottomSheetDialog
import xjunz.tool.mycard.databinding.DialogHistoryBinding
import xjunz.tool.mycard.info.PlayerInfoLoaderClient
import xjunz.tool.mycard.ktx.*
import xjunz.tool.mycard.model.DuelRecord


/**
 * @author xjunz 2023/05/12
 */
class HistoryDialog : BaseBottomSheetDialog<DialogHistoryBinding>() {

    class ViewModel : androidx.lifecycle.ViewModel() {

        lateinit var playerName: String

        private val client = PlayerInfoLoaderClient()

        val records = MutableLiveData<List<DuelRecord>>()

        val selectedIndex = MutableLiveData(0)

        fun loadHistory() {
            viewModelScope.launch {
                val history = withContext(Dispatchers.IO) {
                    client.queryPlayerHistory(playerName, 25)
                }
                records.value = history?.records
            }
        }

        override fun onCleared() {
            super.onCleared()
            client.close()
        }
    }

    private val viewModel by lazyViewModel<ViewModel>()

    private val primaryColor by lazy {
        requireActivity().resolveAttribute(androidx.appcompat.R.attr.colorPrimary).resColor
    }

    private val errorColor by lazy {
        requireActivity().resolveAttribute(androidx.appcompat.R.attr.colorError).resColor
    }

    fun setPlayerName(name: String): HistoryDialog {
        lifecycleScope.launch {
            lifecycle.withCreated {
                viewModel.playerName = name
            }
        }
        return this
    }

    override fun onDialogCreated(dialog: Dialog) {
        initChartView()
        viewModel.loadHistory()
        binding.tvPlayerName.text = buildSpannedString {
            append(
                viewModel.playerName,
                ForegroundColorSpan(primaryColor),
                SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            append(R.string.format_history.resText)
        }
        binding.ibOpenInBrowser.setTooltipCompat(R.string.open_in_browser.resText)
        binding.ibOpenInBrowser.setOnClickListener {
            requireActivity().viewUrlSafely("https://mycard.moe/ygopro/arena/#/userinfo?username=${viewModel.playerName}")
        }
        viewModel.records.observe(this) {
            binding.root.rootView.beginDelayedTransition()
            binding.lineChart.isInvisible = it == null
            if (it == null) {
                binding.progress.show()
            } else {
                binding.progress.hide()
            }
            if (it != null) {
                setChartData(it)
                viewModel.selectedIndex.value = it.size - 1
            }
        }
        viewModel.selectedIndex.observe(this) {
            if (it == null) return@observe
            val record = viewModel.records.value?.asReversed()?.getOrNull(it)
            record?.apply {
                binding.root.rootView.beginDelayedTransition()
                if (!binding.recordContainer.isVisible) {
                    binding.recordContainer.isVisible = true
                }
                val win = winner == viewModel.playerName
                val isPlayerA = viewModel.playerName == usernamea
                val stateListColor = if (win) {
                    ColorStateList.valueOf(0xff3edc85.toInt())
                } else {
                    ColorStateList.valueOf(errorColor)
                }
                binding.tvWinLogo.backgroundTintList = stateListColor
                binding.tvDp.backgroundTintList = stateListColor
                binding.tvDp.text =
                    R.string.format_dp.format((if (isPlayerA) pta else ptb).toInt().toString())
                binding.tvDpChanges.setTextColor(stateListColor)
                val dpDelta = (if (isPlayerA) pta - pta_ex else ptb - ptb_ex).toInt()
                binding.tvDpChanges.text =
                    R.string.format_dp.format(if (dpDelta >= 0) "+$dpDelta" else dpDelta.toString())
                binding.tvTime.text = R.string.format_duel_duration.format(start_time, end_time)
                if (win) {
                    binding.tvWinLogo.setText(R.string.win)
                } else {
                    binding.tvWinLogo.setText(R.string.lose)
                }
                val opponentPlayerName = if (isPlayerA) usernameb else usernamea
                binding.tvVsPlayerName.text = buildSpannedString {
                    append(
                        R.string.versus.resText,
                        StyleSpan(Typeface.ITALIC),
                        SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    append(" ")
                    append(
                        opponentPlayerName,
                        ForegroundColorSpan(primaryColor),
                        SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                binding.tvVsPlayerName.setOnClickListener {
                    HistoryDialog().setPlayerName(opponentPlayerName)
                        .show(parentFragmentManager, "$opponentPlayerName-history")
                }
            }
        }
    }

    private fun initChartView() {
        binding.lineChart.apply {
            description = Description().apply {
                text = R.string.chart_desc.resStr
                textSize = 10F
            }
            axisRight.setDrawLabels(false)
            xAxis.granularity = 1F

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry, h: Highlight) {
                    viewModel.selectedIndex.value = (e.x - 1).toInt()
                }

                override fun onNothingSelected() {

                }
            })
            legend.form = Legend.LegendForm.CIRCLE
            isDoubleTapToZoomEnabled = false
        }
    }

    private fun setChartData(records: List<DuelRecord>) {
        val values: List<Entry> = records.asReversed().mapIndexed { index, record ->
            val pt = if (record.usernamea == viewModel.playerName) record.pta else record.ptb
            Entry(index + 1F, pt)
        }
        // create a dataset and give it a type
        val set = LineDataSet(values, R.string.chart_lengend_desc.resStr)

        set.setDrawIcons(false)

        // draw dashed line
        set.enableDashedLine(10f, 5f, 0f)

        // black lines and points
        set.color = primaryColor
        set.setCircleColor(primaryColor)

        // line thickness and point size
        set.lineWidth = 1f
        set.circleRadius = 3f

        // draw points as solid circles
        set.setDrawCircleHole(false)

        // customize legend entry
        set.formLineWidth = 1f
        set.formLineDashEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
        set.formSize = 15f

        // text size of values
        set.valueTextSize = 9f

        // draw selection line as dashed
        set.enableDashedHighlightLine(10f, 5f, 0f)
        set.highlightLineWidth = 1.5F
        // set the filled area

        set.setDrawFilled(true)
        set.setFillFormatter { _, _ ->
            binding.lineChart.axisLeft.axisMinimum
        }

        // set color of filled area
        set.fillDrawable =
            ContextCompat.getDrawable(requireContext(), R.drawable.chart_area_gradient)
        set.fillAlpha = 35

        val dataSets: ArrayList<ILineDataSet> = ArrayList()
        dataSets.add(set) // add the data sets

        val data = LineData(dataSets)
        binding.lineChart.data = data
        binding.lineChart.invalidate()
    }
}