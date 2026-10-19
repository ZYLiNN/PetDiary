package com.zylinn.petdiary.sync

import java.security.SecureRandom

/** An invitation as shown in the QR code / shared text: `petdiary://join?g=<groupId>&c=<code>`. Pure, unit-tested. */
data class InviteLink(val groupId: String, val code: String) {

    fun asText() = "petdiary://join?g=$groupId&c=$code"

    companion object {
        private const val PREFIX = "petdiary://join?"
        private val ID = Regex("[A-Za-z0-9-]{8,64}")
        private val CODE = Regex("[a-z2-7]{20,64}")
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz234567"

        /** 26 random base-32 characters = 130 bits: not guessable. */
        fun newCode(random: SecureRandom = SecureRandom()): String = buildString { repeat(26) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

        /** Finds the link inside whatever was scanned or pasted (surrounding words / whitespace are fine); null if none. */
        fun parse(text: String): InviteLink? {
            val start = text.indexOf(PREFIX).takeIf { it >= 0 } ?: return null
            val query = text.substring(start + PREFIX.length).takeWhile { !it.isWhitespace() }
            val params = query.split('&').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            val g = params["g"]?.takeIf { ID.matches(it) } ?: return null
            val c = params["c"]?.takeIf { CODE.matches(it) } ?: return null
            return InviteLink(g, c)
        }
    }
}
