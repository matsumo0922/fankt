package me.matsumo.fankt.fanbox

/** Controls diagnostic logging without exposing the HTTP implementation's logging types. */
public enum class FanboxLogLevel {
    /** Disables HTTP diagnostic logging. */
    NONE,

    /** Uses INFO diagnostics for request and response metadata. */
    INFO,

    /** Uses HEADERS diagnostics for request and response headers. */
    HEADERS,

    /** Uses effective INFO diagnostics and never passes body credentials to the logger. */
    BODY,

    /** Uses effective HEADERS diagnostics and never passes body credentials to the logger. */
    ALL,
}
