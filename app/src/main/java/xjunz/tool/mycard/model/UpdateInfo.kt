package xjunz.tool.mycard.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateInfo(
    val binary: Binary,
    val build: String,
    val changelog: String,
    val direct_install_url: String,
    val installUrl: String,
    val install_url: String,
    val name: String,
    val update_url: String,
    val updated_at: Long,
    val version: String,
    val versionShort: String
) {
    @Serializable
    data class Binary(
        @SerialName("fsize") val size: Long
    )
}