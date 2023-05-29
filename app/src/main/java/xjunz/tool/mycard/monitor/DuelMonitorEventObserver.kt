package xjunz.tool.mycard.monitor

import xjunz.tool.mycard.model.Duel

interface DuelMonitorEventObserver {
    /**
     * Called when the duel [list] is initialized.
     */
    fun onInitialized(list: List<Duel>) {}

    /**
     * Called when a duel is deleted.
     */
    fun onDuelDeleted(deleted: Duel) {}

    /**
     * Called when a duel is created.
     */
    fun onDuelCreated(created: Duel) {}

    fun onPlayersInfoLoadedFromService(duel: Duel) {}

    /**
     * Called when the duel list is cleared manually due to connection failure.
     */
    fun onAllDuelCleared() {}
}