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
import com.skydoves.balloon.ArrowPositionRules
import com.skydoves.balloon.Balloon
import com.skydoves.balloon.BalloonAnimation
import com.skydoves.balloon.BalloonSizeSpec
import com.skydoves.balloon.createBalloon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xjunz.tool.mycard.R
import xjunz.tool.mycard.common.BaseBottomSheetDialog
import xjunz.tool.mycard.databinding.DialogHistoryBinding
import xjunz.tool.mycard.info.PlayerInfoLoaderClient
import xjunz.tool.mycard.ktx.beginDelayedTransition
import xjunz.tool.mycard.ktx.format
import xjunz.tool.mycard.ktx.formatDurationMinSec
import xjunz.tool.mycard.ktx.lazyViewModel
import xjunz.tool.mycard.ktx.resColor
import xjunz.tool.mycard.ktx.resStr
import xjunz.tool.mycard.ktx.resText
import xjunz.tool.mycard.ktx.resolveAttribute
import xjunz.tool.mycard.ktx.setEntries
import xjunz.tool.mycard.ktx.setTooltipCompat
import xjunz.tool.mycard.ktx.viewUrlSafely
import xjunz.tool.mycard.main.PlayerInfoDialog
import xjunz.tool.mycard.main.settings.Configs
import xjunz.tool.mycard.model.DuelRecord
import xjunz.tool.mycard.util.TimeParser


/**
 * @author xjunz 2023/05/12
 */
class HistoryDialog : BaseBottomSheetDialog<DialogHistoryBinding>() {

    companion object {
        val QUERY_COUNT_ENTRIES = arrayOf(10, 20, 30, 50, 100)
        const val DEFAULT_QUERY_COUNT = 20
    }


    class ViewModel : androidx.lifecycle.ViewModel() {

        lateinit var playerName: String

        private val client = PlayerInfoLoaderClient()

        val isLoading = MutableLiveData(true)

        private var maxRecords = emptyList<DuelRecord>()

        val records = MutableLiveData<List<DuelRecord>?>()

        val selectedIndex = MutableLiveData(0)

        fun loadHistory(count: Int) {
            viewModelScope.launch {
                if (maxRecords.size >= count) {
                    records.value = maxRecords.subList(0, count)
                    return@launch
                }
                isLoading.value = true
                val history = withContext(Dispatchers.IO) {
                    client.queryPlayerHistory(playerName, count)
                }
                isLoading.value = false
                val currentRecords = history?.records
                if (currentRecords != null && currentRecords.size > maxRecords.size) {
                    maxRecords = currentRecords;
                }
                records.value = currentRecords
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

    private var balloon: Balloon? = null

    override fun onDialogCreated(dialog: Dialog) {
        initChartView()
        initDropDown()
        viewModel.loadHistory(DEFAULT_QUERY_COUNT)
        binding.ibClose.setTooltipCompat(R.string.close.resText)
        binding.ibClose.setOnClickListener {
            dismiss()
        }
        binding.btnPrev?.setOnClickListener {
            val value = viewModel.selectedIndex.value
            if (value != null && value > 0) {
                binding.lineChart.highlightValue(value.toFloat(), 0)
            }
        }
        binding.btnNext?.setOnClickListener {
            val value = viewModel.selectedIndex.value
            if (value != null && value < (viewModel.records.value?.size ?: 0) - 1) {
                binding.lineChart.highlightValue(value.toFloat() + 2, 0)
            }
        }
        if (binding.tvTitle == null) {
            binding.tvPlayerName.text = buildSpannedString {
                append(
                    viewModel.playerName,
                    ForegroundColorSpan(primaryColor),
                    SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                append(R.string.format_history.resText)
            }
        } else {
            binding.tvPlayerName.text = viewModel.playerName
        }

        binding.ibOpenInBrowser.setTooltipCompat(R.string.open_in_browser.resText)
        binding.ibOpenInBrowser.setOnClickListener {
            requireActivity().viewUrlSafely("https://mycard.moe/ygopro/arena/#/userinfo?username=${viewModel.playerName}")
        }
        viewModel.isLoading.observe(this) {
            binding.root.rootView.beginDelayedTransition()
            binding.tilQueryCount.isEnabled = !it
            if (it) {
                binding.progress.show()
            } else {
                binding.progress.hide()
            }
            binding.lineChart.isInvisible = it
        }
        viewModel.records.observe(this) {
            if (it == null) {
                return@observe
            }
            setChartData(it)
            viewModel.selectedIndex.value = it.lastIndex.coerceAtLeast(0)
        }
        viewModel.selectedIndex.observe(this) {
            if (it == null) return@observe
            val recordSize = viewModel.records.value?.size ?: 0;
            val selectedRecordNumber = (it + 1).coerceAtMost(recordSize)
            binding.tvIndicator?.text = "%d/%d".format(selectedRecordNumber, recordSize)
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
                binding.tvDpChanges.text = buildSpannedString {
                    append(
                        R.string.format_dp.format(if (dpDelta >= 0) "+$dpDelta" else dpDelta.toString()),
                        StyleSpan(Typeface.BOLD),
                        SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    if (isfirstwin && win) {
                        append(R.string.first_win.resText)
                    }
                }
                binding.tvTime.text = R.string.format_duel_duration.format(
                    TimeParser.reformatTime(start_time),
                    TimeParser.reformatTime(end_time),
                    (TimeParser.parseTime(end_time) - TimeParser.parseTime(start_time)).formatDurationMinSec()
                )
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
                    balloon?.dismiss()
                    Configs.shouldShowHistoryPlayerNameBalloon = false
                    PlayerInfoDialog().setPlayerName(opponentPlayerName)
                        .show(parentFragmentManager, "player-info-from-history")
                }
                if (!Configs.shouldShowHistoryPlayerNameBalloon) {
                    return@observe
                }
                if (balloon != null) {
                    balloon?.dismiss()
                } else {
                    balloon = createBalloon(requireContext()) {
                        setHeight(BalloonSizeSpec.WRAP)
                        setTextResource(R.string.tip_show_player_info)
                        setTextSize(12f)
                        setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
                        setArrowSize(10)
                        setArrowPosition(0.5f)
                        setPadding(12)
                        setCornerRadius(8f)
                        setBackgroundColor(primaryColor)
                        setBalloonAnimation(BalloonAnimation.FADE)
                        setLifecycleOwner(this@HistoryDialog)
                        build()
                    }
                    balloon?.showAlignBottom(binding.tvVsPlayerName)
                }
            }
        }
    }

    private fun initDropDown() {
        binding.etQueryCount.threshold = Int.MAX_VALUE
        binding.etQueryCount.setEntries(QUERY_COUNT_ENTRIES) {
            val count = QUERY_COUNT_ENTRIES[it]
            viewModel.loadHistory(count)
        }
        binding.etQueryCount.setText(DEFAULT_QUERY_COUNT.toString())
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
                    val x = (e.x - 1).toInt()
                    if (viewModel.selectedIndex.value != x) {
                        viewModel.selectedIndex.value = x
                    }
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
        val set = LineDataSet(
            values,
            R.string.chart_legend_desc.format(records.size)
        )

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
            ContextCompat.getDrawable(requireContext(), R.drawable.bg_chart_area_gradient)
        set.fillAlpha = 35

        val dataSets: ArrayList<ILineDataSet> = ArrayList()
        dataSets.add(set) // add the data sets

        val data = LineData(dataSets)
        binding.lineChart.data = data
        binding.lineChart.invalidate()
    }
}