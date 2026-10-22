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

    fun katakanaToHiragana(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            val code = c.code
            if (code in 0x30A1..0x30F6) {
                sb.append((code - 0x60).toChar())
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    fun romajiToHiragana(romaji: String): String {
        val map = mapOf(
            "ka" to "か", "ki" to "き", "ku" to "く", "ke" to "け", "ko" to "こ",
            "sa" to "さ", "shi" to "し", "si" to "し", "su" to "す", "se" to "せ", "so" to "そ",
            "ta" to "た", "chi" to "ち", "tsu" to "つ", "te" to "て", "to" to "と",
            "na" to "な", "ni" to "に", "nu" to "ぬ", "ne" to "ね", "no" to "の",
            "ha" to "は", "hi" to "ひ", "fu" to "ふ", "hu" to "ふ", "he" to "へ", "ho" to "ほ",
            "ma" to "ま", "mi" to "み", "mu" to "む", "me" to "め", "mo" to "も", "m" to "ん",
            "ya" to "や", "yu" to "ゆ", "yo" to "よ",
            "ra" to "ら", "ri" to "り", "ru" to "る", "re" to "れ", "ro" to "ろ",
            "wa" to "わ", "wo" to "を", "n" to "ん",
            "ga" to "が", "gi" to "ぎ", "gu" to "ぐ", "ge" to "げ", "go" to "ご",
            "za" to "ざ", "ji" to "じ", "zi" to "じ", "zu" to "ず", "ze" to "ぜ", "zo" to "ぞ",
            "da" to "だ", "di" to "ぢ", "du" to "づ", "de" to "で", "do" to "ど",
            "ba" to "ば", "bi" to "び", "bu" to "ぶ", "be" to "べ", "bo" to "ぼ",
            "pa" to "ぱ", "pi" to "ぴ", "pu" to "ぷ", "pe" to "ぺ", "po" to "ぽ",
            "kya" to "きゃ", "kyu" to "きゅ", "kyo" to "きょ",
            "sha" to "しゃ", "shu" to "しゅ", "sho" to "しょ",
            "cha" to "ちゃ", "chu" to "ちゅ", "cho" to "ちょ", "tsya" to "ちゃ", "tsyu" to "ちゅ", "tsyo" to "ちょ",
            "nya" to "にゃ", "nyu" to "にゅ", "nyo" to "にょ",
            "hya" to "ひゃ", "hyu" to "ひゅ", "hyo" to "ひょ",
            "mya" to "みゃ", "myu" to "みゅ", "myo" to "みょ",
            "rya" to "りゃ", "ryu" to "りゅ", "ryo" to "りょ",
            "gya" to "ぎゃ", "gyu" to "ぎゅ", "gyo" to "ぎょ",
            "ja" to "じゃ", "ju" to "じゅ", "jo" to "じょ", "jya" to "じゃ", "jyu" to "じゅ", "jyo" to "じょ",
            "bya" to "びゃ", "byu" to "びゅ", "byo" to "びょ",
            "pya" to "ぴゃ", "pyu" to "ぴゅ", "pyo" to "ぴょ",
            "a" to "あ", "i" to "い", "u" to "う", "e" to "え", "o" to "お"
        )
        val lower = romaji.lowercase().trim()
        val sb = StringBuilder()
        var i = 0
        while (i < lower.length) {
            val ch = lower[i]
            if (!ch.isLetter()) {
                sb.append(ch)
                i++
                continue
            }
            var matched = false
            if (i + 3 <= lower.length) {
                val sub3 = lower.substring(i, i + 3)
                if (map.containsKey(sub3)) {
                    sb.append(map[sub3])
                    i += 3
                    matched = true
                }
            }
            if (!matched && i + 2 <= lower.length) {
                val sub2 = lower.substring(i, i + 2)
                if (map.containsKey(sub2)) {
                    sb.append(map[sub2])
                    i += 2
                    matched = true
                }
            }
            if (!matched) {
                val sub1 = lower.substring(i, i + 1)
                if (map.containsKey(sub1)) {
                    sb.append(map[sub1])
                } else {
                    sb.append(sub1)
                }
                i++
            }
        }
        return sb.toString()
    }
}
