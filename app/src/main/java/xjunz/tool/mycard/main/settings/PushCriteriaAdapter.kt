package xjunz.tool.mycard.main.settings

import android.content.Context
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import xjunz.tool.mycard.R.string
import xjunz.tool.mycard.databinding.ItemPushCriteriaBinding
import xjunz.tool.mycard.ktx.beginDelayedTransition
import xjunz.tool.mycard.ktx.requireActivity
import xjunz.tool.mycard.ktx.showSimplePromptDialog
import xjunz.tool.mycard.main.DuelListAdapter
import xjunz.tool.mycard.monitor.push.DuelPushManager
import xjunz.tool.mycard.monitor.push.DuelPushManager.removeFromAll
import xjunz.tool.mycard.monitor.push.DuelPushManager.update
import xjunz.tool.mycard.util.Motions

class PushCriteriaAdapter : RecyclerView.Adapter<PushCriteriaAdapter.PushCriteriaViewHolder>() {

    private val criteria = DuelPushManager.ALL_CRITERIA

    private lateinit var context: Context

    private lateinit var host: RecyclerView

    private val layoutInflater by lazy {
        LayoutInflater.from(context)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        host = recyclerView
        context = recyclerView.context
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PushCriteriaViewHolder {
        return PushCriteriaViewHolder(
            ItemPushCriteriaBinding.inflate(layoutInflater, parent, false),
        )
    }

    override fun onBindViewHolder(holder: PushCriteriaViewHolder, position: Int) {
        val criterion = criteria[position]
        holder.binding.swEnabled.isChecked = criterion.isEnabled
        holder.binding.tvCriteriaPreview.text = criterion.toString()
        holder.binding.tvCriteriaPreview.text = criterion.formattedString
        holder.binding.dividerBott.isVisible = position != itemCount - 1
    }

    override fun getItemCount() = criteria.size

    inner class PushCriteriaViewHolder(val binding: ItemPushCriteriaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.tvCriteriaPreview.setOnClickListener {
                val expanded = binding.flBtnContainer.isVisible
                host.rootView.beginDelayedTransition()
                binding.tvCriteriaPreview.maxLines = if (!expanded) Int.MAX_VALUE else 4
                binding.tvCriteriaPreview.isActivated = !expanded
                binding.flBtnContainer.isVisible = !expanded
            }
            binding.swEnabled.setOnClickListener {
                val criterion = criteria[adapterPosition]
                criterion.isEnabled = !criterion.isEnabled
                criterion.update()
                DuelListAdapter.broadcastAllChanged(DuelListAdapter.Payload.CHECKED_STATE)
            }
            binding.btnEdit.setOnClickListener {
                val criterion = criteria[adapterPosition]
                DuelPushCriteriaEditorDialog().editCriteria(criterion).doOnConfirmed {
                    it.update()
                    beginDelayedTransition()
                    notifyItemChanged(adapterPosition)
                    DuelListAdapter.broadcastAllChanged(DuelListAdapter.Payload.CHECKED_STATE)
                }.show(context.requireActivity().supportFragmentManager, "edit-criteria")
            }
            binding.btnDelete.setOnClickListener {
                val criterion = criteria[adapterPosition]
                context.showSimplePromptDialog(msg = string.prompt_delete_criteria) {
                    criterion.removeFromAll()
                    beginDelayedTransition()
                    notifyItemChanged(adapterPosition - 1)
                    notifyItemRemoved(adapterPosition)
                }
            }
        }
    }

    fun beginDelayedTransition() {
        TransitionManager.beginDelayedTransition(
            host.rootView as ViewGroup?, ChangeBounds().apply {
                interpolator = Motions.EASING_EMPHASIZED
            })
    }
}