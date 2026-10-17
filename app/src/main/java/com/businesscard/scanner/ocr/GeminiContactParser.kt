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
    @Guide(description = "Full name of the person (e.g. 山田 太郎, John Smith). Exclude honorifics like 様, 殿")
    val personName: String = "",

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

    @Guide(description = "Official website URL")
    val website: String = "",

    @Guide(description = "Physical postal address or office location")
    val address: String = ""
)

class GeminiContactParser {

    private val generativeModel: GenerativeModel by lazy {
        Generation.getClient()
    }

    suspend fun checkStatus(): Int {
        return try {
            generativeModel.checkStatus()
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
        val status = checkStatus()
        if (status == FeatureStatus.DOWNLOADABLE) {
            try {
                generativeModel.download().collect { downloadStatus ->
                    when (downloadStatus) {
                        is DownloadStatus.DownloadStarted -> onProgress?.invoke("Download started (${downloadStatus.bytesToDownload} bytes)")
                        is DownloadStatus.DownloadProgress -> onProgress?.invoke("Progress: ${downloadStatus.totalBytesDownloaded} bytes")
                        is DownloadStatus.DownloadCompleted -> onProgress?.invoke("Download completed")
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
            if (!generativeModel.isStructuredOutputFeatureAvailable()) {
                Log.d(TAG, "Structured output feature is not available on this device")
                return null
            }

            if (!isAvailable()) {
                Log.d(TAG, "Gemini Nano feature status is not AVAILABLE")
                return null
            }

            val combinedText = buildString {
                if (frontText.isNotBlank()) append("Front:\n").append(frontText).append("\n")
                if (backText.isNotBlank()) append("Back:\n").append(backText)
            }.trim()

            val promptText = """
                Extract contact info from this business card OCR text. Match original text accurately.
                OCR Text:
                $combinedText
            """.trimIndent()

            val baseRequest = generateContentRequest(TextPart(promptText)) {
                temperature = 0.0f
                maxOutputTokens = 256
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
