package me.matsumo.fankt.fanbox.response

internal data class FanboxUrlParts(
    val host: String?,
    val encodedPath: String,
    val query: List<FanboxUrlQueryParameter>,
) {
    fun queryValue(name: String): String? = query.firstOrNull { it.name == name }?.value

    companion object {
        fun parse(value: String): FanboxUrlParts {
            val fragmentStart = value.indexOf('#').takeIf { it >= 0 } ?: value.length
            val queryStart = value.indexOf('?').takeIf { it in 0 until fragmentStart }
            val pathEnd = queryStart ?: fragmentStart
            val authorityStart = authorityStart(value, pathEnd)
            val authorityEnd = authorityStart?.let { start ->
                value.indexOf('/', start).takeIf { it in start until pathEnd } ?: pathEnd
            }
            val host = authorityStart?.let { start ->
                parseHost(value.substring(start, checkNotNull(authorityEnd)))
            }
            val pathStart = authorityEnd ?: 0
            val encodedPath = value.substring(pathStart, pathEnd)
            val query = queryStart?.let { start ->
                parseQuery(value.substring(start + 1, fragmentStart))
            }.orEmpty()

            return FanboxUrlParts(host, encodedPath, query)
        }

        private fun authorityStart(value: String, pathEnd: Int): Int? {
            if (value.startsWith("//")) return 2
            val schemeSeparator = value.indexOf("://")
            if (schemeSeparator <= 0 || schemeSeparator >= pathEnd) return null
            val scheme = value.substring(0, schemeSeparator)
            require(
                scheme.first().isAsciiLetter() &&
                    scheme.drop(1).all { it.isAsciiLetter() || it.isAsciiDigit() || it in "+-." },
            ) {
                "Malformed URL scheme"
            }
            return schemeSeparator + 3
        }

        private fun parseHost(authority: String): String {
            require(authority.isNotEmpty()) { "URL authority is empty" }
            val hostAndPort = authority.substringAfterLast('@')
            return if (hostAndPort.startsWith('[')) {
                val end = hostAndPort.indexOf(']')
                require(end > 0) { "Malformed IPv6 host" }
                hostAndPort.substring(1, end)
            } else {
                hostAndPort.substringBefore(':').also { require(it.isNotEmpty()) { "URL host is empty" } }
            }
        }

        private fun parseQuery(value: String): List<FanboxUrlQueryParameter> {
            if (value.isEmpty()) return emptyList()
            return value.split('&').mapNotNull { pair ->
                val separator = pair.indexOf('=')
                if (separator < 0) return@mapNotNull null
                FanboxUrlQueryParameter(
                    name = percentDecode(pair.substring(0, separator)),
                    value = percentDecode(pair.substring(separator + 1)),
                )
            }
        }

        private fun Char.isAsciiLetter(): Boolean = this in 'A'..'Z' || this in 'a'..'z'

        private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

        private fun percentDecode(value: String): String {
            val bytes = ArrayList<Byte>(value.length)
            var index = 0
            while (index < value.length) {
                val char = value[index]
                if (char == '+') {
                    bytes += ' '.code.toByte()
                    index += 1
                } else if (char == '%') {
                    require(index + 2 < value.length) { "Malformed percent escape" }
                    val high = value[index + 1].digitToIntOrNull(16)
                    val low = value[index + 2].digitToIntOrNull(16)
                    require(high != null && low != null) { "Malformed percent escape" }
                    bytes += ((high shl 4) or low).toByte()
                    index += 3
                } else {
                    val end = if (
                        char.isHighSurrogate() &&
                        index + 1 < value.length &&
                        value[index + 1].isLowSurrogate()
                    ) {
                        index + 2
                    } else {
                        index + 1
                    }
                    value.substring(index, end).encodeToByteArray().forEach(bytes::add)
                    index = end
                }
            }
            return runCatching {
                bytes.toByteArray().decodeToString(throwOnInvalidSequence = true)
            }.getOrElse { cause ->
                throw IllegalArgumentException("Malformed UTF-8 escape", cause)
            }
        }
    }
}

internal data class FanboxUrlQueryParameter(
    val name: String,
    val value: String,
)
