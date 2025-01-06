package me.matsumo.fankt.fanbox.domain.model

import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxUserId

data class FanboxMetaData(
    val apiUrl: String,
    val context: Context,
    val csrfToken: String,
) {
    data class Context(
        val privacyPolicy: PrivacyPolicy,
        val user: User,
    ) {
        data class PrivacyPolicy(
            val policyUrl: String,
            val revisionHistoryUrl: String,
            val shouldShowNotice: Boolean,
            val updateDate: String,
        )

        data class User(
            val creatorId: FanboxCreatorId?,
            val fanboxUserStatus: Int,
            val hasAdultContent: Boolean,
            val hasUnpaidPayments: Boolean,
            val iconUrl: String?,
            val isCreator: Boolean,
            val isMailAddressOutdated: Boolean,
            val isSupporter: Boolean,
            val lang: String,
            val name: String,
            val planCount: Int,
            val showAdultContent: Boolean,
            val userId: FanboxUserId?,
        ) {
            companion object {
                fun User.asFanboxUser() = me.matsumo.fankt.fanbox.domain.model.FanboxUser(
                    iconUrl = iconUrl,
                    name = name,
                    userId = userId,
                    creatorId = creatorId,
                )
            }
        }
    }
}
