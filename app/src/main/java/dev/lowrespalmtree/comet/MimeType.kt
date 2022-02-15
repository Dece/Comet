package dev.lowrespalmtree.comet

class MimeType(
    val main: String,
    val sub: String,
    val params: Map<String, String>
) {
    val short: String get() = "${main.ifEmpty { "*" }}/${sub.ifEmpty { "*" }}"
    val charset: String get() = params.getOrDefault("charset", DEFAULT_CHARSET)

    companion object {
        const val DEFAULT_CHARSET = "utf-8"
        val DEFAULT = MimeType("text", "gemini", mapOf("charset" to DEFAULT_CHARSET))

        fun from(string: String): MimeType? {
            val typeString: String
            val params: Map<String, String>
            if (";" in string) {
                val elements = string.split(";")
                typeString = elements[0]
                params = mutableMapOf<String, String>()
                elements.subList(1, elements.size)
                    .map { it.trim().lowercase() }
                    .map { p -> if (p.count { it == '=' } != 1) return@from null else p }
                    .map { it.split('=') }
                    .forEach { params[it[0]] = it[1] }
            } else {
                typeString = string.trim()
                params = mapOf()
            }
            if (typeString.count { it == '/' } != 1)
                return null
            val (main, sub) = typeString.split('/').map { it.trim() }
            return MimeType(main, sub, params)
        }
    }
}