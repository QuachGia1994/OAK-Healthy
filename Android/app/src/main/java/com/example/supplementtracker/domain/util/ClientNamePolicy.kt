package com.example.supplementtracker.domain.util

import java.text.Normalizer
import java.util.Locale

/** Canonical profile-name presentation/duplicate policy; UUID remains the real identity. */
object ClientNamePolicy {
    fun cleaned(raw: String): String = raw.trim()

    fun canonical(raw: String): String {
        val decomposed = Normalizer.normalize(cleaned(raw), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    }

    fun isValid(raw: String): Boolean = cleaned(raw).isNotEmpty()
}
