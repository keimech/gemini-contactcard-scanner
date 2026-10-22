package com.businesscard.scanner.ocr

object CjkUtils {
    fun isCjk(c: Char): Boolean {
        val cp = c.code
        return (cp in 0x4E00..0x9FFF) ||   // CJK Unified Ideographs
               (cp in 0x3400..0x4DBF) ||   // CJK Extension A
               (cp in 0x3040..0x309F) ||   // Hiragana
               (cp in 0x30A0..0x30FF) ||   // Katakana (fullwidth)
               (cp in 0xFF65..0xFF9F) ||   // Halfwidth Katakana
               (cp in 0xAC00..0xD7AF) ||   // Hangul Syllables
               (cp in 0x1100..0x11FF) ||   // Hangul Jamo
               (cp in 0x3130..0x318F)       // Hangul Compatibility Jamo
    }

    fun containsCjk(s: String) = s.any { isCjk(it) }
}
