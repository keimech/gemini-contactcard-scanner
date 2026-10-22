package com.businesscard.scanner.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class OcrHelper {
    private val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val chinese = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    private val japanese = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    private val korean = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

    suspend fun recognizeFile(context: Context, path: String): String {
        if (path.isBlank()) return ""
        val file = File(path)
        if (!file.exists()) return ""
        val image = try {
            InputImage.fromFilePath(context, Uri.fromFile(file))
        } catch (e: Exception) {
            return ""
        }
        return recognize(image)
    }

    suspend fun recognize(image: InputImage): String = coroutineScope {
        val latinText = async { run(latin, image) }
        val chineseText = async { run(chinese, image) }
        val japaneseText = async { run(japanese, image) }
        val koreanText = async { run(korean, image) }
        smartMerge(latinText.await(), chineseText.await(), japaneseText.await(), koreanText.await())
    }

    private fun smartMerge(
        latin: String,
        chinese: String,
        japanese: String,
        korean: String
    ): String {
        val japaneseHasKanaOrCjk = japanese.lines().any { line ->
            line.any { c -> (c.code in 0x3040..0x309F) || (c.code in 0x30A0..0x30FF) } || CjkUtils.containsCjk(line)
        }

        if (japaneseHasKanaOrCjk) {
            val result = LinkedHashSet<String>()
            japanese.lines().map { it.trim() }.filter { it.isNotBlank() }.forEach { result.add(it) }

            latin.lines().map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
                if (result.none { it.contains(line, ignoreCase = true) || line.contains(it, ignoreCase = true) }) {
                    if (line.contains("@") || line.contains("http") || line.contains("www.") || line.any { it.isDigit() }) {
                        result.add(line)
                    }
                }
            }
            return result.joinToString("\n")
        }

        val chineseHasCjk = chinese.lines().any { CjkUtils.containsCjk(it) }
        if (chineseHasCjk) {
            val result = LinkedHashSet<String>()
            chinese.lines().map { it.trim() }.filter { it.isNotBlank() }.forEach { result.add(it) }
            latin.lines().map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
                if (result.none { it.contains(line, ignoreCase = true) }) {
                    if (line.contains("@") || line.contains("http") || line.contains("www.") || line.any { it.isDigit() }) {
                        result.add(line)
                    }
                }
            }
            return result.joinToString("\n")
        }

        val koreanHasHangul = korean.lines().any { line ->
            line.any { c -> (c.code in 0xAC00..0xD7AF) || (c.code in 0x1100..0x11FF) }
        }
        if (koreanHasHangul) {
            val result = LinkedHashSet<String>()
            korean.lines().map { it.trim() }.filter { it.isNotBlank() }.forEach { result.add(it) }
            latin.lines().map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
                if (result.none { it.contains(line, ignoreCase = true) }) {
                    if (line.contains("@") || line.contains("http") || line.contains("www.") || line.any { it.isDigit() }) {
                        result.add(line)
                    }
                }
            }
            return result.joinToString("\n")
        }

        return latin.lines().map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString("\n")
    }

    private suspend fun run(recognizer: TextRecognizer, image: InputImage): String =
        suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { if (cont.isActive) cont.resume(extractStructuredText(it)) }
                .addOnFailureListener { if (cont.isActive) cont.resume("") }
        }

    private fun extractStructuredText(visionText: com.google.mlkit.vision.text.Text): String {
        val lineBoxes = mutableListOf<LineBox>()
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val text = line.text.trim()
                if (text.isNotBlank()) {
                    val box = line.boundingBox
                    val top = box?.top ?: 0
                    val left = box?.left ?: 0
                    val height = box?.height() ?: 20
                    lineBoxes.add(LineBox(text, top, left, height))
                }
            }
        }
        if (lineBoxes.isEmpty()) return ""

        val avgHeight = lineBoxes.map { it.height }.average().toInt().coerceAtLeast(10)
        val rowThreshold = (avgHeight * 0.6).toInt().coerceAtLeast(12)

        lineBoxes.sortBy { it.top }

        val rows = mutableListOf<MutableList<LineBox>>()
        for (box in lineBoxes) {
            val matchingRow = rows.find { row ->
                row.any { Math.abs(it.top - box.top) <= rowThreshold }
            }
            if (matchingRow != null) {
                matchingRow.add(box)
            } else {
                rows.add(mutableListOf(box))
            }
        }

        rows.sortBy { row -> row.minOf { it.top } }

        val resultLines = mutableListOf<String>()
        for (row in rows) {
            row.sortBy { it.left }
            for (box in row) {
                resultLines.add(box.text)
            }
        }

        return resultLines.joinToString("\n")
    }

    private data class LineBox(val text: String, val top: Int, val left: Int, val height: Int)

    fun close() {
        latin.close()
        chinese.close()
        japanese.close()
        korean.close()
    }
}
