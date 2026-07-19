package me.matsumo.fankt.fanbox

/** Controls diagnostic logging without exposing the HTTP implementation's logging types. */
public enum class FanboxLogLevel {
    NONE,
    INFO,
    HEADERS,
    BODY,
    ALL,
}
