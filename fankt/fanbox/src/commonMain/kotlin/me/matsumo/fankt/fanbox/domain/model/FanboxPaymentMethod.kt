package me.matsumo.fankt.fanbox.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class FanboxPaymentMethod {
    CARD,
    PAYPAL,
    CVS,
    UNKNOWN,
    ;

    companion object {
        fun fromString(string: String?) = when (string?.lowercase()) {
            "card" -> CARD
            "paypal" -> PAYPAL
            "cvs" -> CVS
            else -> UNKNOWN
        }
    }
}
