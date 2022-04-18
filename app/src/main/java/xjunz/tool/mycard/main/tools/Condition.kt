package xjunz.tool.mycard.main.tools

import androidx.annotation.IntDef
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import xjunz.tool.mycard.R
import xjunz.tool.mycard.ktx.resStr

@Serializable
data class Condition(
    var remark: String? = null,
    @Type var type: Int = TYPE_ANY_IN_DECK,
    var collectionCount: Int = 3,
    var collectionName: String? = null,
    var isInverted: Boolean = false
) {

    @Transient
    var isSpecified: Boolean = false

    @Transient
    var id: Int = -1

    @IntDef(TYPE_ANY_IN_DECK, TYPE_SPEC_IN_DECK, TYPE_ANY_IN_COLLECTION)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Type

    companion object {
        const val TYPE_ANY_IN_DECK = 1
        const val TYPE_SPEC_IN_DECK = 2
        const val TYPE_ANY_IN_COLLECTION = 3
        val TYPES = TYPE_ANY_IN_DECK..TYPE_ANY_IN_COLLECTION
    }

    fun getDescription(
        @Type type: Int = this.type,
        isInverted: Boolean = this.isInverted
    ): String {
        return when (type) {
            TYPE_ANY_IN_COLLECTION -> {
                check(collectionCount > 1) { "collectionCount must be greater than 1" }
                (if (!isInverted) R.string.any_in_collection
                else R.string.not_any_in_collection).resStr
            }
            TYPE_ANY_IN_DECK -> R.string.any_card.resStr
            TYPE_SPEC_IN_DECK -> {
                (if (!isInverted) R.string.spec_card else R.string.not_spec_card).resStr
            }
            else -> error("unknown type: $type")
        }
    }
}