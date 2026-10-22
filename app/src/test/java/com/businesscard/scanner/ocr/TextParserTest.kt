package com.businesscard.scanner.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric is required because TextParser initialises android.util.Patterns at class load.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class TextParserTest {

    // ──── CJK name detection ────
    // These tests cover the two bugs fixed in the last code review:
    // 1. trimmed.length counted OCR-inserted spaces (4-char name "李 明 大 伟" = length 7 → rejected)
    // 2. words.size guard fired before CJK check (5-token name → rejected by 1..4 bound)

    @Test fun `CJK name without spaces detected`() {
        val result = TextParser.parse("张三丰")
        assertEquals("张三丰", result.personName)
    }

    @Test fun `2-char CJK name with OCR space`() {
        val result = TextParser.parse("张 三")
        assertEquals("张 三", result.personName)
    }

    @Test fun `4-char CJK name with 3 OCR spaces - trimmed length was 7 bug`() {
        // Old bug: trimmed.length == 7, "7 in 2..6" == false → name dropped
        val result = TextParser.parse("李 明 大 伟")
        assertEquals("李 明 大 伟", result.personName)
    }

    @Test fun `5-char CJK name with 4 OCR spaces - words-size guard bug`() {
        // Old bug: words.size == 5, "5 in 1..4" == false → early return before CJK check
        val result = TextParser.parse("张 三 李 四 王")
        assertEquals("张 三 李 四 王", result.personName)
    }

    @Test fun `6-char CJK name at upper limit`() {
        val result = TextParser.parse("张三李四王五")
        assertEquals("张三李四王五", result.personName)
    }

    @Test fun `1-char CJK not a name - below lower limit`() {
        val result = TextParser.parse("李")
        assertNotEquals("李", result.personName)
    }

    @Test fun `7-char CJK not a name - above upper limit`() {
        val result = TextParser.parse("张三李四王五陈")
        assertNotEquals("张三李四王五陈", result.personName)
    }

    @Test fun `mixed CJK and non-CJK not detected as CJK name`() {
        // "张3丰" — the '3' digit fails the "all chars are CJK or space" check
        val result = TextParser.parse("张3丰")
        assertNotEquals("张3丰", result.personName)
    }

    @Test fun `hiragana name detected`() {
        val result = TextParser.parse("たなか")
        assertEquals("たなか", result.personName)
    }

    @Test fun `katakana name detected`() {
        val result = TextParser.parse("スズキ")
        assertEquals("スズキ", result.personName)
    }

    @Test fun `Japanese name prioritized over English name when both are present`() {
        val ocr = "Taro Yamada\n山田 太郎\n株式会社サンプル\ntaro@example.com"
        val result = TextParser.parse(ocr)
        assertEquals("山田 太郎", result.personName)
        assertEquals("株式会社サンプル", result.companyName)
    }

    @Test fun `Japanese surname and given name split across lines are merged`() {
        val ocr = "株式会社サンプル\n山田\n太郎\n代表取締役"
        val result = TextParser.parse(ocr)
        assertEquals("山田 太郎", result.personName)
        assertEquals("株式会社サンプル", result.companyName)
    }

    @Test fun `Japanese name with wide gap normalized to single space`() {
        val ocr = "株式会社サンプル\n山田        太郎\nCEO"
        val result = TextParser.parse(ocr)
        assertEquals("山田 太郎", result.personName)
    }

    // ──── Latin name detection ────

    @Test fun `Latin first-last name detected`() {
        val result = TextParser.parse("John Smith")
        assertEquals("John Smith", result.personName)
    }

    @Test fun `Latin 4-word name at upper limit`() {
        val result = TextParser.parse("Mary Jane Watson Parker")
        assertEquals("Mary Jane Watson Parker", result.personName)
    }

    @Test fun `Latin 5-word sequence not a name`() {
        // words.size == 5 → not in 1..4 after CJK check fails → false
        val result = TextParser.parse("John Michael Andrew David Smith")
        assertNotEquals("John Michael Andrew David Smith", result.personName)
    }

    @Test fun `all-lowercase not a name`() {
        val result = TextParser.parse("john smith")
        assertNotEquals("john smith", result.personName)
    }

    // ──── Field extraction ────

    @Test fun `email extracted`() {
        val result = TextParser.parse("John Smith\njohn@example.com")
        assertEquals("john@example.com", result.email)
    }

    @Test fun `company with Ltd indicator`() {
        val ocr = "John Smith\nAcme Pty Ltd\nCEO\njohn@acme.com"
        val result = TextParser.parse(ocr)
        assertEquals("John Smith", result.personName)
        assertFalse(result.companyName.isBlank())
    }

    @Test fun `CJK company indicator 有限公司 extracted`() {
        val ocr = "张三\n上海科技有限公司\nCEO"
        val result = TextParser.parse(ocr)
        assertEquals("张三", result.personName)
    }

    @Test fun `website extracted`() {
        val result = TextParser.parse("John Smith\nwww.acme.com\njohn@acme.com")
        assertFalse(result.website.isBlank())
    }

    @Test fun `department name is not recognized as person name`() {
        val ocr = "営業部\n山田 太郎\n株式会社サンプル\ntaro@example.com"
        val result = TextParser.parse(ocr)
        assertEquals("山田 太郎", result.personName)
        assertNotEquals("営業部", result.personName)
    }

    @Test fun `email local part is not recognized as website`() {
        val ocr = "山田 太郎\njohn.smith@example.com"
        val result = TextParser.parse(ocr)
        assertEquals("山田 太郎", result.personName)
        assertTrue(result.website.isBlank())
    }

    @Test fun `Japanese address extracted`() {
        val ocr = "山田 太郎\n〒100-0001 東京都千代田区千代田1-1\nサンプルビル5F"
        val result = TextParser.parse(ocr)
        assertTrue(result.address.contains("東京都千代田区"))
    }

    @Test fun `Furigana generated from Katakana reading`() {
        val ocr = "山田 太郎\nヤマダ タロウ\n株式会社サンプル"
        val result = TextParser.parse(ocr)
        assertEquals("やまだ たろう", result.phoneticName)
    }

    @Test fun `Furigana generated from Romaji reading`() {
        val ocr = "山田 太郎\nYamada Tarou\n株式会社サンプル"
        val result = TextParser.parse(ocr)
        assertEquals("やまだ たろう", result.phoneticName)
    }

    @Test fun `front and back texts combined`() {
        val result = TextParser.parse(
            frontText = "John Smith\nAcme Corp",
            backText = "john@acme.com\n+61 2 1234 5678"
        )
        assertEquals("John Smith", result.personName)
        assertEquals("john@acme.com", result.email)
    }

    @Test fun `empty input returns blank contact`() {
        val result = TextParser.parse("")
        assertTrue(result.personName.isBlank())
        assertTrue(result.email.isBlank())
        assertTrue(result.phone.isBlank())
    }
}
