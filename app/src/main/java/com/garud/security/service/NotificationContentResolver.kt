package com.garud.security.service

/** Selects the text Android actually exposed in a notification without retaining it. */
internal object NotificationContentResolver {
    private const val MAX_CONTENT_CHARS = 10_000
    private const val MAX_MESSAGE_LINES = 8

    fun resolveBody(
        bigText: String?,
        messagingLines: List<String>,
        text: String?,
        textLines: List<String>
    ): String? {
        val candidates = sequenceOf(
            messagingLines.asSequence().filter(String::isNotBlank).toList().takeLast(MAX_MESSAGE_LINES).joinToString("\n"),
            bigText.orEmpty(),
            text.orEmpty(),
            textLines.filter(String::isNotBlank).takeLast(MAX_MESSAGE_LINES).joinToString("\n")
        )
        return candidates.firstOrNull(String::isNotBlank)?.take(MAX_CONTENT_CHARS)
    }
}
