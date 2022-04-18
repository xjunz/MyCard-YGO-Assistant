package xjunz.tool.mycard.main.settings

import xjunz.tool.mycard.app

object Configs {

    const val MAX_TAG_TEXT_LENGTH = 8
    const val MAX_TAG_COUNT_PER_PLAYER = 6
    const val MAX_RANK_DIGIT_COUNT = 8

    const val MAX_CONDITION_SET_COUNT = 18
    const val MAX_COLLECTION_TEXT_LENGTH = 10

    const val MAX_PUSH_CRITERIA_COUNT = 10
    const val MAX_PUSH_DELAY_DIGIT_COUNT = 3
    const val MAX_SHOWN_PUSHES_COUNT = 9
    const val MAX_SHOWN_PUSH_ALIVE_MILLS = 3_600_000 // an hour

    private val sharedPrefs by lazy {
        app.sharedPrefsOf("configs")
    }

    private const val SP_KEY_NOTIFICATION_DISABLED = "notification_disabled"

    var isNotificationDisabled: Boolean
        get() {
            return sharedPrefs.getBoolean(SP_KEY_NOTIFICATION_DISABLED, false)
        }
        set(value) {
            sharedPrefs.edit().putBoolean(SP_KEY_NOTIFICATION_DISABLED, value).apply()
        }

    private const val SP_KEY_PIN_FOLLOWED_DUELS = "pin_followed_duels"

    var shouldPinFollowedDuels: Boolean
        get() {
            return sharedPrefs.getBoolean(SP_KEY_PIN_FOLLOWED_DUELS, false)
        }
        set(value) {
            sharedPrefs.edit().putBoolean(SP_KEY_PIN_FOLLOWED_DUELS, value).apply()
        }

    private const val SP_KEY_MINE_AS_HOME = "mine_as_home"

    var isMineAsHome: Boolean
        get() {
            return sharedPrefs.getBoolean(SP_KEY_MINE_AS_HOME, false)
        }
        set(value) {
            sharedPrefs.edit().putBoolean(SP_KEY_MINE_AS_HOME, value).apply()
        }
}