package xjunz.tool.mycard.main.detail

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import xjunz.tool.mycard.Constants
import xjunz.tool.mycard.R
import xjunz.tool.mycard.common.InputDialog
import xjunz.tool.mycard.databinding.ItemTagViewBinding
import xjunz.tool.mycard.info.PlayerInfoManager
import xjunz.tool.mycard.ktx.*
import xjunz.tool.mycard.main.settings.Configs

/**
 * @author xjunz 2022/3/11
 */
class TagAdapter(private var playerName: String) :
    RecyclerView.Adapter<TagAdapter.TagViewHolder>() {

    object Action {
        const val ADD = 0
        const val REMOVE = 1
        const val RENAME = 2
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updatePlayerName(playerName: String) {
        this.playerName = playerName
        notifyDataSetChanged()
    }

    private lateinit var rv: RecyclerView

    private lateinit var context: AppCompatActivity

    private lateinit var layoutManager: StaggeredGridLayoutManager

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        rv = recyclerView
        context = rv.context.requireActivity()
        layoutManager = rv.layoutManager as StaggeredGridLayoutManager
    }

    private val presetTags by lazy {
        PlayerInfoManager.getAllDistinctTags().distinct()
    }

    private val tags get() = PlayerInfoManager.getTags(playerName)

    inner class TagViewHolder(val binding: ItemTagViewBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val pos = adapterPosition
                // developer tag is not editable
                if (playerName == Constants.DEVELOPER_NAME) {
                    toast("(>.<)")
                    binding.root.shake()
                    return@setOnClickListener
                }
                if (pos == 0) {
                    if (tags.size == Configs.MAX_TAG_COUNT_PER_PLAYER) {
                        longToast(
                            R.string.no_more_tags.format(Configs.MAX_TAG_COUNT_PER_PLAYER)
                        )
                        return@setOnClickListener
                    }
                    InputDialog().apply {
                        setTitle(R.string.hint_add_tag.resText)
                        setMaxLength(Configs.MAX_TAG_TEXT_LENGTH)
                        setDropDownData(presetTags)
                        setHint(R.string.hint_tag_name.resText)
                        setPositiveButton s@{
                            if (it.isBlank()) return@s R.string.tag_is_blank.resStr
                            if (it.contains('|')) return@s R.string.tag_illegal_char.resStr
                            if (tags.contains(it)) return@s R.string.tag_existed.resStr
                            PlayerInfoManager.insertTag(playerName, it)
                            notifyItemInserted(1)
                            onTagChanged?.invoke(Action.ADD, it)
                            return@s null
                        }
                    }.show(context.supportFragmentManager, "insert-tag-dialog")
                } else {
                    val tag = tags[pos - 1]
                    InputDialog().apply {
                        setTitle(R.string.edit_tag.resText)
                        setInitialInput(tag)
                        setHint(R.string.rename_to.resText)
                        setMaxLength(Configs.MAX_TAG_TEXT_LENGTH)
                        setDropDownData(presetTags)
                        setPositiveButton s@{
                            if (it.isBlank()) return@s R.string.tag_is_blank.resStr
                            if (it.contains('|')) return@s R.string.tag_illegal_char.resStr
                            if (tag == it) return@s null
                            if (tags.contains(it)) return@s R.string.tag_existed.resStr
                            val index = PlayerInfoManager.renameTag(playerName, tag, it)
                            if (index != -1) {
                                notifyItemChanged(index + 1)
                                onTagChanged?.invoke(Action.RENAME, it)
                            }
                            return@s null
                        }
                        setNegativeAsImportant()
                        setNegativeButton(R.string.delete_tag.resText) s@{
                            val index = PlayerInfoManager.removeTag(playerName, tag)
                            if (index == -1) return@s true
                            notifyItemRemoved(index + 1)
                            if (tags.isEmpty()) {
                                notifyItemChanged(0)
                            }
                            onTagChanged?.invoke(Action.REMOVE, tag)
                            return@s true
                        }
                    }.show(context.supportFragmentManager, "edit-tag-dialog")
                }
            }
        }
    }

    private var onTagChanged: ((action: Int, tag: String) -> Unit)? = null

    fun doOnTagChanged(block: (action: Int, tag: String) -> Unit): TagAdapter {
        onTagChanged = block
        return this
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
        return TagViewHolder(ItemTagViewBinding.inflate(context.layoutInflater, parent, false))
    }

    override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
        holder.binding.root.apply {
            if (position != 0) {
                isActivated = false
                text = tags[position - 1]
            } else {
                isActivated = true
                text = R.string.add_tag.resText
            }
        }
    }

    override fun getItemCount() = tags.size + 1

}