package com.businesscard.scanner.ocr

import android.util.Patterns
import java.util.regex.Pattern

object TextParser {

    data class ParsedContact(
        val personName: String = "",
        val phoneticName: String = "",
        val companyName: String = "",
        val jobTitle: String = "",
        val phone: String = "",
        val mobile: String = "",
        val email: String = "",
        val website: String = "",
        val address: String = "",
        val handwrittenNote: String = "",
        val handwrittenDate: String = ""
    )

    // Matches phone numbers with optional country code, parentheses, separators
    private val PHONE_PATTERN = Pattern.compile(
        """(?:(?:\+|00)\d{1,3}[\s\-.])?(?:\(?\d{2,4}\)?[\s\-.])\d{3,4}[\s\-.]?\d{3,4}(?:[\s\-.]?\d{1,4})?"""
    )

    // Label prefixes like "T:", "M:", "Ph:", "Mobile:", "Tel:", "F:" before a number
    private val PHONE_LABEL_PATTERN = Pattern.compile(
        """(?:^|(?<=\s))(?:t|m|f|tel|mob|mobile|ph|phone|fax|direct|office)\s*[:\-]\s*([\d\s\-()+.]{7,})""",
        Pattern.CASE_INSENSITIVE or Pattern.MULTILINE
    )

    private val EMAIL_PATTERN = Patterns.EMAIL_ADDRESS

    private val WEBSITE_PATTERN = Pattern.compile(
        """(?:https?://)?(?:www\.)?[a-zA-Z0-9][a-zA-Z0-9\-]{1,61}[a-zA-Z0-9]\.[a-zA-Z]{2,}(?:/\S*)?"""
    )

    private val JOB_TITLE_KEYWORDS = listOf(
        "ceo", "cto", "cfo", "coo", "director", "manager", "engineer", "developer",
        "designer", "analyst", "consultant", "president", "founder", "owner",
        "sales", "marketing", "account", "executive", "officer", "head of",
        "senior", "junior", "lead", "principal", "associate", "coordinator",
        "specialist", "representative", "advisor", "partner", "vice president", "vp"
    )

    private val COMPANY_INDICATORS = listOf(
        // English / Australian
        "pty", "ltd", "limited", "inc", "corp", "corporation", "llc", "co.",
        "group", "solutions", "services", "technologies", "consulting", "enterprises",
        "holdings", "international", "global", "australia", "pty ltd",
        // Chinese (Simplified & Traditional)
        "有限公司", "股份有限公司", "集团", "企业", "贸易", "科技",
        // Japanese
        "株式会社", "有限会社", "合同会社", "合資会社"
    )

    /** Returns a human-readable breakdown of how each piece of the raw OCR text was classified. */
    fun debugParse(frontText: String, backText: String = ""): String {
        val allText = "$frontText\n$backText"
        val lines = allText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val sb = StringBuilder()

        sb.appendLine("Lines (${lines.size}):")
        lines.forEachIndexed { i, line -> sb.appendLine("  [$i] $line") }
        sb.appendLine()

        val email = extractEmail(allText)
        sb.appendLine("Email: ${email.ifBlank { "(none)" }}")

        val (phone, mobile) = extractPhones(allText)
        sb.appendLine("Phone: ${phone.ifBlank { "(none)" }}")
        sb.appendLine("Mobile: ${mobile.ifBlank { "(none)" }}")

        val website = extractWebsite(allText, email)
        sb.appendLine("Website: ${website.ifBlank { "(none)" }}")

        val address = extractAddress(lines)
        sb.appendLine("Address: ${address.ifBlank { "(none)" }}")

        val jobTitle = extractJobTitle(lines, address)
        sb.appendLine("Job title: ${jobTitle.ifBlank { "(none)" }}")

        val allPhones = (phone.lines() + mobile.lines()).filter { it.isNotBlank() }
        val (personName, companyName) = extractNameAndCompany(lines, email, allPhones, website, jobTitle, address)
        sb.appendLine("Name: ${personName.ifBlank { "(none)" }}")
        sb.appendLine("Company: ${companyName.ifBlank { "(none)" }}")
        sb.appendLine()

        val skipLines = (setOf(email, website, jobTitle, address) + allPhones).filter { it.isNotBlank() }
        val candidates = lines.filter { line ->
            skipLines.none { line.contains(it, ignoreCase = true) } &&
            !line.matches(DIGIT_RUN_PATTERN) &&
            line.length in 2..60
        }
        sb.appendLine("Name/company candidates (${candidates.size}):")
        if (candidates.isEmpty()) sb.appendLine("  (all lines were skipped)")
        candidates.forEach { sb.appendLine("  • $it") }
        sb.appendLine()
        sb.append("Skipped because matched phone/email/etc (${skipLines.size}): ${skipLines.joinToString(", ").ifBlank { "(none)" }}")

        return sb.toString()
    }

    /**
     * Smart parsing method that attempts Gemini Nano AI extraction first, and
     * falls back to rule-based [parse] if Gemini Nano is unavailable or fails.
     */
    suspend fun parseSmart(
        frontText: String,
        backText: String = "",
        geminiParser: GeminiContactParser? = null
    ): ParsedContact {
        if (geminiParser != null) {
            val aiResult = geminiParser.parseWithGemini(frontText, backText)
            if (aiResult != null && (aiResult.personName.isNotBlank() || aiResult.companyName.isNotBlank())) {
                val fallback = parse(frontText, backText)
                return ParsedContact(
                    personName = aiResult.personName.ifBlank { fallback.personName },
                    phoneticName = aiResult.phoneticName.ifBlank { fallback.phoneticName },
                    companyName = aiResult.companyName.ifBlank { fallback.companyName },
                    jobTitle = aiResult.jobTitle.ifBlank { fallback.jobTitle },
                    phone = aiResult.phone.ifBlank { fallback.phone },
                    mobile = aiResult.mobile.ifBlank { fallback.mobile },
                    email = aiResult.email.ifBlank { fallback.email },
                    website = aiResult.website.ifBlank { fallback.website },
                    address = aiResult.address.ifBlank { fallback.address },
                    handwrittenNote = aiResult.handwrittenNote.ifBlank { fallback.handwrittenNote },
                    handwrittenDate = aiResult.handwrittenDate.ifBlank { fallback.handwrittenDate }
                )
            }
        }
        return parse(frontText, backText)
    }

    fun parse(frontText: String, backText: String = ""): ParsedContact {
        val allText = "$frontText\n$backText"
        val lines = allText.lines().map { it.trim() }.filter { it.isNotBlank() }

        val email = extractEmail(allText)
        val (phone, mobile) = extractPhones(allText)
        val website = extractWebsite(allText, email)
        val address = extractAddress(lines)
        val jobTitle = extractJobTitle(lines, address)
        val allPhones = (phone.lines() + mobile.lines()).filter { it.isNotBlank() }
        val (personName, companyName) = extractNameAndCompany(lines, email, allPhones, website, jobTitle, address)
        val phoneticName = extractPhoneticName(lines, personName)
        val handwrittenDate = extractHandwrittenDate(lines)
        val handwrittenNote = extractHandwrittenNote(lines, setOf(email, website, address, jobTitle, personName, companyName, phone, mobile))

        return ParsedContact(
            personName = personName,
            phoneticName = phoneticName,
            companyName = companyName,
            jobTitle = jobTitle,
            phone = phone,
            mobile = mobile,
            email = email,
            website = website,
            address = address,
            handwrittenNote = handwrittenNote,
            handwrittenDate = handwrittenDate
        )
    }

    private fun extractEmail(text: String): String {
        val matcher = EMAIL_PATTERN.matcher(text)
        return if (matcher.find()) matcher.group() else ""
    }

    // Returns Pair(landlines, mobiles) — both as newline-joined strings
    private fun extractPhones(text: String): Pair<String, String> {
        // Remove known business identifiers so their digit sequences aren't matched as phone numbers
        val cleaned = stripBusinessIdentifiers(text)
        val found = mutableListOf<String>()

        // Labelled numbers first (T:, M:, Ph:, Mobile:, etc.)
        val labelMatcher = PHONE_LABEL_PATTERN.matcher(cleaned)
        while (labelMatcher.find()) {
            val raw = labelMatcher.group(1)?.trim() ?: continue
            val candidate = trimToLastDigit(raw)
            val digits = candidate.filter { it.isDigit() }
            if (isValidPhoneDigitCount(digits.length)) found.add(candidate)
        }

        // Unlabelled numbers
        val matcher = PHONE_PATTERN.matcher(cleaned)
        while (matcher.find()) {
            val candidate = matcher.group().trim()
            val digits = candidate.filter { it.isDigit() }
            if (isValidPhoneDigitCount(digits.length) &&
                found.none { it.contains(candidate) || candidate.contains(it) }) {
                found.add(candidate)
            }
        }

        val mobiles = found.filter { isMobile(it) }
        val landlines = found.filter { !isMobile(it) }
        return Pair(landlines.joinToString("\n"), mobiles.joinToString("\n"))
    }

    private val DIGIT_RUN_PATTERN = Regex(""".*\d{3,}.*""")
    private val WHITESPACE = Regex("\\s+")
    private val ABN_PATTERN = Regex("""(?i)ABN\s*:?\s*\d[\d\s]{8,13}""")
    private val ACN_PATTERN = Regex("""(?i)ACN\s*:?\s*\d[\d\s]{6,10}""")
    private val BSB_PATTERN = Regex("""(?i)BSB\s*:?\s*\d[\d\s-]{5,9}""")
    private val TFN_PATTERN = Regex("""(?i)TFN\s*:?\s*\d[\d\s]{6,10}""")

    // Erase ABN, ACN, BSB, TFN and similar AU business identifiers before phone matching
    private fun stripBusinessIdentifiers(text: String): String = text
        .replace(ABN_PATTERN, "")
        .replace(ACN_PATTERN, "")
        .replace(BSB_PATTERN, "")
        .replace(TFN_PATTERN, "")

    // Strips trailing text annotations like "(AUS)", "(FAX)" after the last digit
    private fun trimToLastDigit(s: String): String {
        val last = s.indexOfLast { it.isDigit() }
        return if (last >= 0) s.substring(0, last + 1).trim() else s
    }

    // Accepts 7-8 digits (local AU), 10 (AU with area code / mobile), 11 (+61...), 12-15 (other international)
    private fun isValidPhoneDigitCount(count: Int) = count in 7..15

    // Australian mobile: local 04xx or international +614xx
    private fun isMobile(number: String): Boolean {
        val digits = number.filter { it.isDigit() }
        return digits.startsWith("04") || digits.startsWith("614")
    }

    private fun extractWebsite(text: String, email: String): String {
        val emailLower = email.lowercase()
        val emailLocalPart = if (email.contains("@")) email.substringBefore("@").lowercase() else ""
        val emailDomain = if (email.contains("@")) email.substringAfter("@").lowercase() else ""

        val matcher = WEBSITE_PATTERN.matcher(text)
        while (matcher.find()) {
            val match = matcher.group()
            val matchLower = match.lowercase()
            if (matchLower.contains("@")) continue
            if (matchLower == emailLower) continue
            if (emailLocalPart.isNotBlank() && emailLocalPart.contains(matchLower)) continue
            if (matchLower == emailDomain && !matchLower.startsWith("www.") && !match.contains("://")) continue
            return match
        }
        return ""
    }

    private val JAPANESE_POSTAL_PATTERN = Pattern.compile("""〒?\s*\d{3}-\d{4}""")
    private val PREFECTURES = listOf(
        "東京都", "北海道", "京都府", "大阪府", "青森県", "岩手県", "宮城県", "秋田県", "山形県", "福島県",
        "茨城県", "栃木県", "群馬県", "埼玉県", "千葉県", "神奈川県", "新潟県", "富山県", "石川県", "福井県",
        "山梨県", "長野県", "岐阜県", "静岡県", "愛知県", "三重県", "滋賀県", "兵庫県", "奈良県", "和歌山県",
        "鳥取県", "島根県", "岡山県", "広島県", "山口県", "徳島県", "香川県", "愛媛県", "高知県", "福岡県",
        "佐賀県", "長崎県", "熊本県", "大分県", "宮崎県", "鹿児島県", "沖縄県"
    )

    private fun extractAddress(lines: List<String>): String {
        val addressLines = mutableListOf<String>()

        val addressPatterns = listOf(
            Regex("""\d+\s+\w+.*(street|st|avenue|ave|road|rd|drive|dr|lane|ln|way|blvd|boulevard|court|ct|place|pl|suite|floor|fl)""", RegexOption.IGNORE_CASE),
            Regex("""(po box|p\.o\. box)\s*\d+""", RegexOption.IGNORE_CASE),
            Regex("""(nsw|vic|qld|wa|sa|tas|act|nt)\s*\d{4}""", RegexOption.IGNORE_CASE),
            Regex("""[a-zA-Z]{3,}.*\b\d{4}\b|\b\d{4}\b.*[a-zA-Z]{3,}""")
        )

        for (i in lines.indices) {
            val line = lines[i]
            val digitRatio = line.count { it.isDigit() }.toFloat() / line.length.coerceAtLeast(1)
            if (digitRatio > 0.5f) continue
            if (PHONE_LABEL_PATTERN.matcher(line).find()) continue
            if (PHONE_PATTERN.matcher(line).find()) continue

            val hasPostalCode = JAPANESE_POSTAL_PATTERN.matcher(line).find()
            val hasPrefecture = PREFECTURES.any { line.contains(it) }
            val hasJpAddressKeywords = (line.contains("市") || line.contains("区") || line.contains("町") || line.contains("村")) &&
                    (line.contains("丁目") || line.contains("番地") || line.contains("号") || line.contains("ビル") || line.contains("階") || line.contains("F") || line.contains("タワー") || line.contains("1-") || line.contains("2-") || line.contains("3-") || line.contains("4-") || line.contains("5-") || line.contains("6-") || line.contains("7-") || line.contains("8-") || line.contains("9-"))

            if (hasPostalCode || hasPrefecture || hasJpAddressKeywords) {
                addressLines.add(line)
                if (i + 1 < lines.size) {
                    val nextLine = lines[i + 1]
                    val isNextBuildingOrFloor = nextLine.contains("ビル") || nextLine.contains("タワー") || nextLine.contains("階") || nextLine.contains("F") || nextLine.contains("号室") || nextLine.contains("館") || nextLine.contains("スクエア")
                    if (isNextBuildingOrFloor && !PHONE_LABEL_PATTERN.matcher(nextLine).find() && !PHONE_PATTERN.matcher(nextLine).find()) {
                        addressLines.add(nextLine)
                    }
                }
                return addressLines.joinToString(" ")
            }

            for (pattern in addressPatterns) {
                if (pattern.containsMatchIn(line)) return line
            }
        }
        return ""
    }

    private val DATE_PATTERN = Pattern.compile(
        """(\d{4}[-/年]\d{1,2}[-/月]\d{1,2}日?|\d{1,2}/\d{1,2}(?:/\d{2,4})?)"""
    )

    private fun extractHandwrittenDate(lines: List<String>): String {
        for (line in lines) {
            val matcher = DATE_PATTERN.matcher(line)
            if (matcher.find()) {
                return matcher.group(1) ?: line
            }
        }
        return ""
    }

    private fun extractHandwrittenNote(lines: List<String>, claimedLines: Set<String>): String {
        val notes = mutableListOf<String>()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue
            val isClaimed = claimedLines.any { it.isNotBlank() && trimmed.contains(it, ignoreCase = true) }
            val isPhone = PHONE_PATTERN.matcher(trimmed).find() || PHONE_LABEL_PATTERN.matcher(trimmed).find()
            val isEmail = EMAIL_PATTERN.matcher(trimmed).find()
            val isWebsite = WEBSITE_PATTERN.matcher(trimmed).find()

            if (!isClaimed && !isPhone && !isEmail && !isWebsite && trimmed.length > 5) {
                notes.add(trimmed)
            }
        }
        return notes.joinToString("\n")
    }

    private fun extractPhoneticName(lines: List<String>, personName: String): String {
        if (personName.isBlank()) return ""

        val hasKanaOnly = personName.all { c ->
            c == ' ' || (c.code in 0x3040..0x309F) || (c.code in 0x30A0..0x30FF) || (c.code in 0xFF65..0xFF9F)
        }
        if (hasKanaOnly) {
            return CjkUtils.katakanaToHiragana(personName)
        }

        for (line in lines) {
            if (line == personName) continue
            val trimmed = line.trim()
            val isKanaReading = trimmed.length in 2..20 && trimmed.all { c ->
                c == ' ' || (c.code in 0x3040..0x309F) || (c.code in 0x30A0..0x30FF)
            }
            if (isKanaReading && (trimmed.contains(" ") || trimmed.length in 2..8)) {
                return CjkUtils.katakanaToHiragana(trimmed)
            }
        }

        for (line in lines) {
            if (line == personName) continue
            val trimmed = line.trim()
            val isRomajiName = trimmed.length in 3..25 &&
                    trimmed.split(WHITESPACE).size in 1..3 &&
                    trimmed.all { it.isLetter() || it == ' ' || it == '-' }
            if (isRomajiName && !JOB_TITLE_KEYWORDS.any { trimmed.lowercase().contains(it) }) {
                val converted = CjkUtils.romajiToHiragana(trimmed)
                if (converted.isNotBlank()) return converted
            }
        }

        return ""
    }

    private fun extractJobTitle(lines: List<String>, addressLine: String = ""): String {
        for (line in lines) {
            if (addressLine.isNotBlank() && line == addressLine) continue
            val lower = line.lowercase()
            if (JOB_TITLE_KEYWORDS.any { lower.contains(it) } && line.length < 60) {
                return line
            }
        }
        return ""
    }

    private fun extractNameAndCompany(
        lines: List<String>,
        email: String,
        phones: List<String>,
        website: String,
        jobTitle: String,
        address: String
    ): Pair<String, String> {
        val skipLines = (setOf(email, website, jobTitle, address) + phones)
            .filter { it.isNotBlank() }

        val rawCandidates = lines.filter { line ->
            skipLines.none { line.contains(it, ignoreCase = true) } &&
            !line.matches(DIGIT_RUN_PATTERN) &&
            line.length in 2..60
        }

        val candidates = preprocessCjkCandidates(rawCandidates)

        var personName = ""
        var companyName = ""

        val nonCompanyCandidates = mutableListOf<String>()
        for (line in candidates) {
            val lower = line.lowercase()
            if (companyName.isEmpty() && COMPANY_INDICATORS.any { lower.contains(it) }) {
                companyName = line
            } else {
                nonCompanyCandidates.add(line)
            }
        }

        val nameCandidates = nonCompanyCandidates.filter { looksLikeName(it) }

        // Prefer CJK (Japanese/Chinese/Korean) name candidate if present over Latin name candidate
        val cjkName = nameCandidates.firstOrNull { CjkUtils.containsCjk(it) }
        if (cjkName != null) {
            personName = cjkName
        } else if (nameCandidates.isNotEmpty()) {
            personName = nameCandidates.first()
        }

        if (companyName.isEmpty()) {
            val remainingForCompany = nonCompanyCandidates.filter { line ->
                line != personName &&
                !looksLikeName(line) &&
                line.length > 3
            }
            companyName = remainingForCompany.firstOrNull() ?: ""
        }

        return Pair(personName, companyName)
    }

    private fun preprocessCjkCandidates(lines: List<String>): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val current = lines[i].trim()
            val currentCjkCount = current.count { CjkUtils.isCjk(it) }
            val isCurrentPureCjk = currentCjkCount > 0 && current.all { it == ' ' || CjkUtils.isCjk(it) }

            if (i + 1 < lines.size && isCurrentPureCjk && currentCjkCount in 1..3) {
                val next = lines[i + 1].trim()
                val nextCjkCount = next.count { CjkUtils.isCjk(it) }
                val isNextPureCjk = nextCjkCount > 0 && next.all { it == ' ' || CjkUtils.isCjk(it) }
                val combinedCjkCount = currentCjkCount + nextCjkCount

                val isCompanyIndicator = COMPANY_INDICATORS.any {
                    current.lowercase().contains(it) || next.lowercase().contains(it)
                }

                if (isNextPureCjk && nextCjkCount in 1..3 && combinedCjkCount in 2..6 && !isCompanyIndicator) {
                    val normalizedMerged = "${current.replace(WHITESPACE, " ")} ${next.replace(WHITESPACE, " ")}".trim()
                    result.add(normalizedMerged)
                    i += 2
                    continue
                }
            }

            if (isCurrentPureCjk) {
                result.add(current.replace(WHITESPACE, " "))
            } else {
                result.add(current)
            }
            i++
        }
        return result
    }

    private val DEPARTMENT_INDICATORS = listOf(
        "部", "課", "係", "チーム", "室", "局", "本部", "支店", "営業所", "センター", "グループ", "事業部",
        "department", "division", "team", "section", "branch", "group", "office", "unit"
    )

    private fun looksLikeName(text: String): Boolean {
        val trimmed = text.trim()
        val lower = trimmed.lowercase()
        if (DEPARTMENT_INDICATORS.any { lower.contains(it) } || COMPANY_INDICATORS.any { lower.contains(it) }) {
            return false
        }
        // CJK names: checked first so the word-count guard below doesn't block names whose
        // characters OCR split into individual tokens (e.g. "张 三 李 四" → 4 words).
        val cjkCount = trimmed.count { CjkUtils.isCjk(it) }
        if (cjkCount in 1..6 && trimmed.all { it == ' ' || CjkUtils.isCjk(it) }) return true
        // Latin/mixed names: 1–4 capitalised words
        val words = trimmed.split(WHITESPACE)
        if (words.size !in 1..4) return false
        return words.all { word ->
            // Strip trailing punctuation (e.g. "ANDRICH," → "ANDRICH")
            val clean = word.trimEnd(',', '.', ';', ':')
            clean.isNotEmpty() &&
            clean[0].isUpperCase() &&
            clean.all { it.isLetter() || it == '-' || it == '\'' }
        }
    }
}
