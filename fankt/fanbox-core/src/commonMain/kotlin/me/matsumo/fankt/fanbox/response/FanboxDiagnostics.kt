package me.matsumo.fankt.fanbox.response

import me.matsumo.fankt.fanbox.InternalFanboxApi

@InternalFanboxApi
public fun interface FanboxDiagnosticSink {
    fun report(message: String)

    companion object {
        val none = FanboxDiagnosticSink {}
    }
}

/** 認証情報を除去し、長さを制限した診断情報を生成する。 */
@InternalFanboxApi
public object FanboxDiagnostics {
    const val MAX_FRAGMENT_LENGTH = 2_048

    private const val REDACTED = "[REDACTED]"
    private const val CREDENTIAL_KEY =
        "(?:csrfToken|FANBOXSESSID|x-csrf-token|authorization|cookie)"

    private val jsonCredential = Regex(
        pattern = "([\"']?$CREDENTIAL_KEY[\"']?\\s*:\\s*[\"'])([^\"']*)([\"'])",
        option = RegexOption.IGNORE_CASE,
    )
    private val htmlCredential = Regex(
        pattern = "($CREDENTIAL_KEY&amp;quot;\\s*:\\s*&amp;quot;)(.*?)(?=&amp;quot;)",
        option = RegexOption.IGNORE_CASE,
    )
    private val entityCredential = Regex(
        pattern = "($CREDENTIAL_KEY&quot;\\s*:\\s*&quot;)(.*?)(?=&quot;)",
        option = RegexOption.IGNORE_CASE,
    )
    private val headerCredential = Regex(
        pattern = "($CREDENTIAL_KEY\\s*[=:]\\s*)([^\\s;,]+)",
        option = RegexOption.IGNORE_CASE,
    )

    fun sanitizeFragment(body: String): String {
        val redacted = body
            .replace(htmlCredential) { match -> match.groupValues[1] + REDACTED }
            .replace(entityCredential) { match -> match.groupValues[1] + REDACTED }
            .replace(jsonCredential) { match -> match.groupValues[1] + REDACTED + match.groupValues[3] }
            .replace(headerCredential) { match -> match.groupValues[1] + REDACTED }
        val normalized = buildString(redacted.length) {
            redacted.forEach { char ->
                append(if (char.code < 32 || char.code == 127) ' ' else char)
            }
        }

        if (normalized.length <= MAX_FRAGMENT_LENGTH) return normalized
        return normalized.take(MAX_FRAGMENT_LENGTH - 1) + '…'
    }
}
