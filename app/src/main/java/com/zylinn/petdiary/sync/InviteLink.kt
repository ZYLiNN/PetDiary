package com.zylinn.petdiary.sync

import java.security.SecureRandom

/**
 * An invitation as shared / shown in the QR code (P13, D79):
 * `https://petdiary-878a6.web.app/join?openExternalBrowser=1#g=<groupId>&c=<code>` — a normal web link that chat apps
 * make clickable; Android opens it straight in PetDiary (App Links) and otherwise the page offers an "open" button.
 * The code sits after `#`, which browsers never send to the server. The older `petdiary://join?g=…&c=…` form
 * (v0.8–v0.12) is still understood. Pure, unit-tested.
 */
data class InviteLink(val groupId: String, val code: String) {

    fun asText() = "$WEB_PREFIX?openExternalBrowser=1#g=$groupId&c=$code"

    companion object {
        const val HOST = "petdiary-878a6.web.app"
        private const val WEB_PREFIX = "https://$HOST/join"
        private const val APP_PREFIX = "petdiary://join?"
        private val ID = Regex("[A-Za-z0-9-]{8,64}")
        private val CODE = Regex("[a-z2-7]{20,64}")
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz234567"

        /** 26 random base-32 characters = 130 bits: not guessable. */
        fun newCode(random: SecureRandom = SecureRandom()): String = buildString { repeat(26) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

        /** Finds the link inside whatever was scanned or pasted (surrounding words / whitespace are fine); null if none. */
        fun parse(text: String): InviteLink? {
            val params = webParams(text) ?: appParams(text) ?: return null
            val g = params["g"]?.takeIf { ID.matches(it) } ?: return null
            val c = params["c"]?.takeIf { CODE.matches(it) } ?: return null
            return InviteLink(g, c)
        }

        /** `https://<our host>/join…#g=…&c=…` — only our own host, and only the part after `#`. */
        private fun webParams(text: String): Map<String, String>? {
            val start = text.indexOf(WEB_PREFIX).takeIf { it >= 0 } ?: return null
            val link = text.substring(start).takeWhile { !it.isWhitespace() }
            val rest = link.removePrefix(WEB_PREFIX)
            if (rest.isNotEmpty() && rest[0] !in "?#") return null // e.g. /joinx
            return query(link.substringAfter('#', ""))
        }

        private fun appParams(text: String): Map<String, String>? {
            val start = text.indexOf(APP_PREFIX).takeIf { it >= 0 } ?: return null
            return query(text.substring(start + APP_PREFIX.length).takeWhile { !it.isWhitespace() })
        }

        private fun query(q: String) =
            q.split('&').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
    }
}
