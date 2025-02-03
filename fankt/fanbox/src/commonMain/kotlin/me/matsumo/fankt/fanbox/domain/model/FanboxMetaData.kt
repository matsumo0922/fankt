package me.matsumo.fankt.fanbox.domain.model

import kotlinx.serialization.Serializable
import me.matsumo.fankt.fanbox.domain.model.id.FanboxCreatorId
import me.matsumo.fankt.fanbox.domain.model.id.FanboxUserId

@Serializable
data class FanboxMetaData(
    val apiUrl: String?,
    val context: Context?,
    val csrfToken: String,
) {
    @Serializable
    data class Context(
        val privacyPolicy: PrivacyPolicy,
        val user: User,
    ) {
        @Serializable
        data class PrivacyPolicy(
            val policyUrl: String,
            val revisionHistoryUrl: String,
            val shouldShowNotice: Boolean,
            val updateDate: String,
        )

        @Serializable
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
        )
    }
}
