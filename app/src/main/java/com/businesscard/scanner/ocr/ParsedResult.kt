package com.businesscard.scanner.ocr

import android.os.Parcelable
import com.businesscard.scanner.data.BusinessCard
import kotlinx.parcelize.Parcelize

/**
 * Represents the result of a single card scan in batch mode.
 * Contains the parsed contact information and the associated image paths.
 */
@Parcelize
data class ParsedResult(
    val personName: String,
    val phoneticName: String = "",
    val companyName: String,
    val jobTitle: String,
    val phone: String,
    val mobile: String,
    val email: String,
    val website: String,
    val address: String,
    val rawTextFront: String,
    val rawTextBack: String,
    val frontImagePath: String,
    val backImagePath: String,
    val tags: String = "",
    val handwrittenNote: String = "",
    val handwrittenDate: String = ""
) : Parcelable

/**
 * Extension to convert a ParsedResult directly into a [BusinessCard] entity.
 */
fun ParsedResult.toBusinessCard(): BusinessCard {
    return BusinessCard(
        personName = personName,
        phoneticName = phoneticName,
        companyName = companyName,
        jobTitle = jobTitle,
        phone = phone,
        mobile = mobile,
        email = email,
        website = website,
        address = address,
        rawTextFront = rawTextFront,
        rawTextBack = rawTextBack,
        frontImagePath = frontImagePath,
        backImagePath = backImagePath,
        tags = tags,
        notes = handwrittenNote
    )
}
