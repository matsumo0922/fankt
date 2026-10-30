package me.matsumo.fankt.fanbox

/**
 * guest で起きた出来事。[Fanbox] の `guestEventListener` へ渡される。
 *
 * どの値も response 本文と認証情報を含まない。
 */
sealed interface FanboxGuestEvent {
    /** guest をロードした。[version] は bundle のビルド元の commit で、ローカルでビルドした bundle では `null`。 */
    data class Loaded(val version: String?) : FanboxGuestEvent

    /**
     * guest をロードできなかった。この [Fanbox] の呼び出しはすべて [FanboxException.GuestFailure] で失敗する。
     * [errorType] は失敗した例外の型名。
     */
    data class LoadFailed(val errorType: String) : FanboxGuestEvent

    /** guest の parser が [endpoint] の response を拒否した。その呼び出しは [FanboxException.SchemaMismatch] で失敗する。 */
    data class SchemaMismatch(val endpoint: String) : FanboxGuestEvent

    /**
     * guest の呼び出しが失敗した。その呼び出しは [FanboxException.GuestFailure] で失敗する。
     *
     * [operation] は `buildRequest` か `parseResponse`。[errorType] は失敗した例外の型名で、guest が自身の失敗を
     * 報告した場合は `GuestFailure`。
     */
    data class CallFailed(
        val endpoint: String,
        val operation: String,
        val errorType: String,
    ) : FanboxGuestEvent
}
