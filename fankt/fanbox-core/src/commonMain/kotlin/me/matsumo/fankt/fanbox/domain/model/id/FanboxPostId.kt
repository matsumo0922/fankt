package me.matsumo.fankt.fanbox.domain.model.id

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class FanboxPostId(val value: String)
