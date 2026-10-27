package com.businesscard.scanner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.businesscard.scanner.data.AppDatabase
import com.businesscard.scanner.data.BusinessCard
import com.businesscard.scanner.data.BusinessCardRepository
import com.businesscard.scanner.ocr.ParsedResult
import com.businesscard.scanner.ocr.toBusinessCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReviewViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BusinessCardRepository
    private val _parsedItems = MutableStateFlow<List<ParsedResult>>(emptyList())
    val parsedItems: StateFlow<List<ParsedResult>> = _parsedItems.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = BusinessCardRepository(database.businessCardDao(), database.interactionLogDao())
    }

    fun setItems(items: List<ParsedResult>) {
        _parsedItems.value = items.toList()
    }

    fun saveSingleCard(card: BusinessCard, handwrittenNote: String = "", onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val id = repository.insert(card)
                    if (handwrittenNote.isNotBlank()) {
                        repository.insertLog(
                            com.businesscard.scanner.data.InteractionLog(
                                cardId = id,
                                type = "Note",
                                note = handwrittenNote
                            )
                        )
                    }
                }
                onComplete(true)
            } catch (e: Exception) {
                onComplete(false)
            }
        }
    }

    fun saveAllRemainingCards(remaining: List<ParsedResult>, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            var savedCount = 0
            withContext(Dispatchers.IO) {
                for (item in remaining) {
                    try {
                        val card = item.toBusinessCard()
                        val id = repository.insert(card)
                        if (item.handwrittenNote.isNotBlank()) {
                            repository.insertLog(
                                com.businesscard.scanner.data.InteractionLog(
                                    cardId = id,
                                    type = "Note",
                                    note = item.handwrittenNote
                                )
                            )
                        }
                        savedCount++
                    } catch (_: Exception) {}
                }
            }
            onComplete(savedCount)
        }
    }
}
