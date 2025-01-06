package me.matsumo.fankt.fanbox.domain.model

enum class FanboxPaymentMethod {
    CARD,
    PAYPAL,
    CVS,
    UNKNOWN,
    ;

    companion object {
        fun fromString(string: String?) = when (string) {
            "gmo_card" -> CARD
            "paypal" -> PAYPAL
            "gmo_cvs" -> CVS
            else -> UNKNOWN
        }
    }
}
