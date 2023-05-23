package xjunz.tool.mycard.model

import kotlinx.serialization.Serializable

/**
 * @author xjunz 2023/05/18
 */
@Serializable
abstract class BasePlayer {

    abstract val athletic_all: Int

    abstract val athletic_draw: Int

    abstract val athletic_lose: Int

    abstract val athletic_win: Int

    abstract val exp: Float

    abstract val pt: Float

    abstract var rank: Int

    abstract var name: String

}