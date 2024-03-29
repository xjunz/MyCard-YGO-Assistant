package xjunz.tool.mycard.main.settings

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xjunz.tool.mycard.app
import xjunz.tool.mycard.main.filter.DuelListFilterCriteria

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

    val LenientJson = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val configSharedPrefs by lazy {
        app.sharedPrefsOf("configs")
    }

    private val balloonShardPrefs by lazy {
        app.sharedPrefsOf("balloon")
    }

    private const val SP_KEY_NOTIFICATION_DISABLED = "notification_disabled"

    var isNotificationDisabled: Boolean
        get() {
            return configSharedPrefs.getBoolean(SP_KEY_NOTIFICATION_DISABLED, false)
        }
        set(value) {
            configSharedPrefs.edit().putBoolean(SP_KEY_NOTIFICATION_DISABLED, value).apply()
        }

    private const val SP_KEY_PIN_FOLLOWED_DUELS = "pin_followed_duels"

    var shouldPinFollowedDuels: Boolean
        get() {
            return configSharedPrefs.getBoolean(SP_KEY_PIN_FOLLOWED_DUELS, false)
        }
        set(value) {
            configSharedPrefs.edit().putBoolean(SP_KEY_PIN_FOLLOWED_DUELS, value).apply()
        }

    private const val SP_KEY_MINE_AS_HOME = "mine_as_home"

    var isMineAsHome: Boolean
        get() {
            return configSharedPrefs.getBoolean(SP_KEY_MINE_AS_HOME, false)
        }
        set(value) {
            configSharedPrefs.edit().putBoolean(SP_KEY_MINE_AS_HOME, value).apply()
        }

    private const val SP_KEY_DUEL_LIST_FILTER = "duel_list_filter"

    private val EMPTY_DUEL_LIST_FILTER_CRITERIA = DuelListFilterCriteria()

    var duelListFilter: DuelListFilterCriteria
        get() = _duelListFilter ?: EMPTY_DUEL_LIST_FILTER_CRITERIA
        set(value) {
            _duelListFilter = if (value == EMPTY_DUEL_LIST_FILTER_CRITERIA) null else value
        }

    val hasFilterApplied get() = duelListFilter != EMPTY_DUEL_LIST_FILTER_CRITERIA

    private var _duelListFilter: DuelListFilterCriteria? = null
        get() {
            if (field == null) {
                val serialized =
                    configSharedPrefs.getString(SP_KEY_DUEL_LIST_FILTER, null) ?: return null
                field = LenientJson.decodeFromString(serialized)
            }
            return field
        }
        set(value) {
            field = if (value == null) {
                configSharedPrefs.edit().remove(SP_KEY_DUEL_LIST_FILTER).apply()
                null
            } else {
                configSharedPrefs.edit()
                    .putString(SP_KEY_DUEL_LIST_FILTER, LenientJson.encodeToString(value))
                    .apply()
                value
            }
        }

    fun clearBalloonPrefs() {
        balloonShardPrefs.edit().clear().apply()
        shouldShowHistoryPlayerNameBalloon = true
        shouldShowFilterBalloon = true
        shouldShowBackToTopBalloon = true
    }

    private const val SP_KEY_SHOW_FILTER_BALLOON = "show_filter_balloon"

    var shouldShowFilterBalloon: Boolean
        get() {
            return balloonShardPrefs.getBoolean(SP_KEY_SHOW_FILTER_BALLOON, true)
        }
        set(value) {
            balloonShardPrefs.edit().putBoolean(SP_KEY_SHOW_FILTER_BALLOON, value).apply()
        }

    private const val SP_KEY_SHOW_BACK_TO_TOP_BALLOON = "show_back_to_top_balloon"

    var shouldShowBackToTopBalloon: Boolean
        get() {
            return balloonShardPrefs.getBoolean(SP_KEY_SHOW_BACK_TO_TOP_BALLOON, true)
        }
        set(value) {
            balloonShardPrefs.edit().putBoolean(SP_KEY_SHOW_BACK_TO_TOP_BALLOON, value).apply()
        }

    private const val SP_KEY_SHOW_HISTORY_PLAYER_NAME_BALLOON = "show_history_player_name_balloon"

    var shouldShowHistoryPlayerNameBalloon: Boolean
        get() {
            return balloonShardPrefs.getBoolean(SP_KEY_SHOW_HISTORY_PLAYER_NAME_BALLOON, true)
        }
        set(value) {
            balloonShardPrefs.edit().putBoolean(SP_KEY_SHOW_HISTORY_PLAYER_NAME_BALLOON, value)
                .apply()
        }
}