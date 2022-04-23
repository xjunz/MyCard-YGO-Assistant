package xjunz.tool.mycard

/**
 * @author xjunz 2022/04/23
 */
object Apis {
    const val BASE_API = "https://sapi.moecube.com:444/ygopro/"

    const val ARENA_ATHLETIC = "athletic"

    // https://sapi.moecube.com:444/ygopro/match?arena=athletic&locale=zh-CN
    const val API_MATCH = "match"

    const val API_MATCH_ANTICIPATE = "match/stats/"

    // https://sapi.moecube.com:444/ygopro/arena/users?o=pt
    const val API_TOP_PLAYER = "arena/users?o=pt"

    // https://sapi.moecube.com:444/ygopro/arena/history?username=xjunz
    const val API_PLAYER_HISTORY = "arena/history"

    // https://sapi.moecube.com:444/ygopro/arena/user?username=xjunz
    const val API_PLAYER_INFO = "arena/user"
    const val BASE_API_ACCOUNTS = "https://api.moecube.com/accounts/"
    const val API_LOGIN = "signin"
    const val HOST_DUEL_LIST = "tiramisu.mycard.moe"
    const val HOST_ATHLETIC = "tiramisu.mycard.moe"
}