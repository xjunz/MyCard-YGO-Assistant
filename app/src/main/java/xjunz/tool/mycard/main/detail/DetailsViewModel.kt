package xjunz.tool.mycard.main.detail

import androidx.lifecycle.ViewModel
import xjunz.tool.mycard.model.Duel

class DetailsViewModel : ViewModel() {

    var rankDeltas = IntArray(2)

    var dpDeltas = IntArray(2)

    var prevDuel: Duel? = null

    inline val reqPrevDuel get() = prevDuel!!

    var hasPendingRefresh: Boolean = false

}