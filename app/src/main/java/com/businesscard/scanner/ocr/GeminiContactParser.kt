package com.businesscard.scanner.ocr

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generateTypedContentRequest
import com.google.mlkit.genai.schema.annotations.Generable
import com.google.mlkit.genai.schema.annotations.Guide

@Generable("Structured contact details extracted from a business card")
data class GenerableParsedContact(
    @Guide(description = "Full name of the person (e.g. 山田 太郎, John Smith). Exclude department names, division names, affiliations, and company names (e.g. 営業部, 開発本部, Sales Dept). If both Japanese and English names are present, ALWAYS prefer the Japanese name. If surname and given name are separated by wide spaces or on separate lines, join them into a single full name (e.g. 山田 太郎). Exclude honorifics like 様, 殿")
    val personName: String = "",

    @Guide(description = "Furigana or phonetic reading of the person's name in Hiragana (e.g. やまだ たろう). Extract from Furigana/Katakana/Romaji reading on the card if present, or generate the standard Hiragana reading for the Japanese Kanji name.")
    val phoneticName: String = "",

    @Guide(description = "Full official company or organization name (e.g. 株式会社サンプル, Acme Corp). Include corporate suffixes like 株式会社, 有限会社, 合同会社, Ltd., Inc., Co.")
    val companyName: String = "",

    @Guide(description = "Job title, rank, or position (e.g. 代表取締役社長, 営業部長, Director, Senior Engineer)")
    val jobTitle: String = "",

    @Guide(description = "Office or landline telephone number")
    val phone: String = "",

    @Guide(description = "Mobile or cellular telephone number")
    val mobile: String = "",

    @Guide(description = "Email address")
    val email: String = "",

    @Guide(description = "Official website URL (e.g. https://example.com, www.example.com). Do NOT extract the domain part of the email address (e.g. example.com from taro@example.com) as a website unless explicitly printed as a website URL.")
    val website: String = "",

    @Guide(description = "Physical postal address or office location (e.g. 〒100-0001 東京都千代田区1-1-1 サンプルビル5F). Include postal code, prefecture, city, street, and building/floor name if available.")
    val address: String = ""
)

class GeminiContactParser {

    private val generativeModel: GenerativeModel by lazy {
        Generation.getClient()
    }

    @Volatile
    private var cachedStatus: Int? = null

    suspend fun checkStatus(): Int {
        val cached = cachedStatus
        if (cached != null) return cached
        return try {
            val status = generativeModel.checkStatus()
            cachedStatus = status
            status
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to check Gemini Nano status: ${e.message}")
            FeatureStatus.UNAVAILABLE
        }
    }

    suspend fun isAvailable(): Boolean {
        return checkStatus() == FeatureStatus.AVAILABLE
    }

    suspend fun warmup() {
        try {
            if (isAvailable()) {
                generativeModel.warmup()
                Log.d(TAG, "Gemini Nano model warmed up into memory")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to warmup Gemini Nano model: ${e.message}")
        }
    }

    suspend fun downloadIfNeeded(onProgress: ((String) -> Unit)? = null) {
        cachedStatus = null
        val status = checkStatus()
        if (status == FeatureStatus.DOWNLOADABLE) {
            try {
                generativeModel.download().collect { downloadStatus ->
                    when (downloadStatus) {
                        is DownloadStatus.DownloadStarted -> onProgress?.invoke("Download started (${downloadStatus.bytesToDownload} bytes)")
                        is DownloadStatus.DownloadProgress -> onProgress?.invoke("Progress: ${downloadStatus.totalBytesDownloaded} bytes")
                        is DownloadStatus.DownloadCompleted -> {
                            cachedStatus = FeatureStatus.AVAILABLE
                            onProgress?.invoke("Download completed")
                        }
                        is DownloadStatus.DownloadFailed -> Log.e(TAG, "Download failed: ${downloadStatus.e.message}")
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error downloading Gemini Nano model: ${e.message}")
            }
        }
    }

    suspend fun parseWithGemini(frontText: String, backText: String = ""): TextParser.ParsedContact? {
        if (frontText.isBlank() && backText.isBlank()) return null

        return try {
            if (!isAvailable()) {
                Log.d(TAG, "Gemini Nano feature status is not AVAILABLE")
                return null
            }

            if (!generativeModel.isStructuredOutputFeatureAvailable()) {
                Log.d(TAG, "Structured output feature is not available on this device")
                return null
            }

            val combinedText = buildString {
                if (frontText.isNotBlank()) append("Front:\n").append(frontText).append("\n")
                if (backText.isNotBlank()) append("Back:\n").append(backText)
            }.trim()

            val promptText = "Extract contact details from OCR text:\n$combinedText\nNote: If the person's name appears in both Japanese (Kanji/Kana) and English/Romaji, ALWAYS prioritize the Japanese name. If surname and given name are separated by large spaces or split across lines, join them into a single full name (e.g. 山田 太郎)."

            val baseRequest = generateContentRequest(TextPart(promptText)) {
                temperature = 0.0f
                maxOutputTokens = 160
            }

            val typedRequest = generateTypedContentRequest(
                generateContentRequest = baseRequest,
                outputClass = GenerableParsedContact::class
            )

            val response = generativeModel.generateContent(typedRequest)
            val candidate = response.candidates.firstOrNull()?.response
            if (candidate != null) {
                TextParser.ParsedContact(
                    personName = candidate.personName,
                    phoneticName = candidate.phoneticName,
                    companyName = candidate.companyName,
                    jobTitle = candidate.jobTitle,
                    phone = candidate.phone,
                    mobile = candidate.mobile,
                    email = candidate.email,
                    website = candidate.website,
                    address = candidate.address
                )
            } else {
                Log.w(TAG, "Gemini Nano response candidate was null")
                null
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Gemini Nano extraction failed: ${e.message}", e)
            null
        }
    }

    fun close() {
        try {
            generativeModel.close()
        } catch (e: Throwable) {
            Log.e(TAG, "Error closing GenerativeModel instance: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "GeminiContactParser"
    }
}
