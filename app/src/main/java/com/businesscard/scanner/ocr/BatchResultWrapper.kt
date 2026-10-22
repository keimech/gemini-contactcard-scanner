package com.businesscard.scanner.ocr

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A Parcelable wrapper to pass a list of [ParsedResult] between activities.
 */
@Parcelize
data class BatchResultWrapper(
    val results: List<ParsedResult>
) : Parcelable
