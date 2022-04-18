package xjunz.tool.mycard.game

data class Game(
    var username: String = DEF_USERNAME,
    var host: String = DEF_HOST_NAME,
    var port: Int = DEF_HOST_PORT,
    var mode: Int = 0,
    var rule: Int = 0,
    var lp: Int = DEF_LIFE_POINTS,
    var turnDuration: Int = DEF_TURN_DURATION,
    var startDraw: Int = DEF_START_DRAW,
    var perDraw: Int = DEF_PER_DRAW,
    var cardPool: Int = 0,
    var noFlList: Boolean = false,
    var noShuffle: Boolean = false,
    var noCheck: Boolean = false,
    var password: String? = null
) {

    companion object {
        const val DEF_USERNAME = "Knight of Hanoi"
        const val DEF_HOST_NAME = "s1.ygo233.com"
        const val DEF_HOST_PORT = 233
        const val DEF_LIFE_POINTS = 8000
        val LIFE_POINTS_RANGE = 1..99_999
        const val DEF_TURN_DURATION = 233
        val TURN_DURATION_RANGE = 0..999
        const val DEF_START_DRAW = 5
        val START_DRAW_RANGE = 1..40
        const val DEF_PER_DRAW = 1
        val PER_DRAW_RANGE = 1..35
    }

    fun generateRoomId(): String {
        val sb = StringBuilder()
        sb.append(
            when (mode) {
                0 -> "S"
                1 -> "M"
                2 -> "T"
                else -> error("unknown mode: $mode")
            }
        ).append(",")
        if (rule != 0) {
            sb.append("MR$rule,")
        }
        if (lp != DEF_LIFE_POINTS) {
            sb.append("LP$lp,")
        }
        if (turnDuration != DEF_TURN_DURATION) {
            sb.append("TM$turnDuration,")
        }
        if (startDraw != DEF_START_DRAW) {
            sb.append("ST$startDraw,")
        }
        if (perDraw != DEF_PER_DRAW) {
            sb.append("DR$perDraw,")
        }
        if (cardPool != 0) {
            sb.append(
                when (cardPool) {
                    1 -> "OT"
                    2 -> "TO"
                    3 -> "NU"
                    else -> error("unknown card pool: $cardPool")
                }
            ).append(",")
        }
        if (noFlList) sb.append("NF,")
        if (noShuffle) sb.append("NS,")
        if (noCheck) sb.append("NC,")
        if (sb.endsWith(',')) {
            sb.deleteCharAt(sb.lastIndex)
        }
        if (!password.isNullOrEmpty()) {
            sb.append("#$password")
        }
        return sb.toString()
    }
}