package com.businesscard.scanner.ocr

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class GeminiContactParserTest {

    @Test
    fun `GenerableParsedContact default values are blank`() {
        val contact = GenerableParsedContact()
        assertEquals("", contact.personName)
        assertEquals("", contact.companyName)
        assertEquals("", contact.jobTitle)
        assertEquals("", contact.phone)
        assertEquals("", contact.mobile)
        assertEquals("", contact.email)
        assertEquals("", contact.website)
        assertEquals("", contact.address)
    }

    @Test
    fun `warmup handles test environment gracefully without throwing exception`() = runBlocking {
        val parser = GeminiContactParser()
        parser.warmup()
    }

    @Test
    fun `parseSmart falls back to rule-based parser when geminiParser is null`() = runBlocking {
        val ocr = "山田 太郎\n株式会社サンプル\nCEO\ntaro@example.com"
        val result = TextParser.parseSmart(ocr, geminiParser = null)

        assertEquals("山田 太郎", result.personName)
        assertEquals("株式会社サンプル", result.companyName)
        assertEquals("CEO", result.jobTitle)
        assertEquals("taro@example.com", result.email)
    }

    @Test
    fun `parseSmart falls back when Gemini Nano is unavailable on test environment`() = runBlocking {
        val parser = GeminiContactParser()
        val ocr = "John Smith\nAcme Pty Ltd\njohn@acme.com"
        val result = TextParser.parseSmart(ocr, geminiParser = parser)

        assertNotNull(result)
        assertEquals("John Smith", result.personName)
        assertEquals("john@acme.com", result.email)
    }
}
