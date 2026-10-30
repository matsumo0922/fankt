package me.matsumo.fankt.fanbox

/**
 * guest 経路で起きた出来事。guest を有効にした [Fanbox] の `guestEventListener` へ渡される。
 *
 * どの値も response 本文と認証情報を含まない。
 */
sealed interface FanboxGuestEvent {
    /** guest をロードした。[version] は bundle のビルド元の commit で、ローカルでビルドした bundle では `null`。 */
    data class Loaded(val version: String?) : FanboxGuestEvent

    /** guest をロードできなかった。この [Fanbox] は組み込みの経路だけを使う。[errorType] は失敗した例外の型名。 */
    data class LoadFailed(val errorType: String) : FanboxGuestEvent

    /** guest の parser が [endpoint] の response を拒否した。同じ response を組み込みの parser で解析する。 */
    data class SchemaMismatch(val endpoint: String) : FanboxGuestEvent

    /**
     * guest の呼び出しが失敗し、組み込みの経路で処理した。
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
