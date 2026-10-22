package com.businesscard.scanner.ui

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.businesscard.scanner.R
import com.businesscard.scanner.databinding.ActivityReviewContactsBinding
import com.businesscard.scanner.ocr.BatchResultWrapper
import com.businesscard.scanner.ocr.ParsedResult

class ReviewContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReviewContactsBinding
    private val viewModel: ReviewViewModel by viewModels()
    private lateinit var adapter: ReviewAdapter

    private var totalInitialCount = 0
    private var savedCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.review_title)

        val wrapper = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_BATCH_RESULTS, BatchResultWrapper::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_BATCH_RESULTS)
        }

        val results = wrapper?.results ?: emptyList()
        totalInitialCount = results.size

        if (results.isEmpty()) {
            showEmptyState()
            return
        }

        setupAdapter(results)
        setupListeners()
        updateProgressSubtitle(0)
    }

    private fun setupAdapter(results: List<ParsedResult>) {
        adapter = ReviewAdapter(
            onSaveClick = { card, position ->
                viewModel.saveSingleCard(card) { success ->
                    if (success) {
                        savedCount++
                        Toast.makeText(this, getString(R.string.contact_saved), Toast.LENGTH_SHORT).show()
                    }
                    adapter.removeItem(position)
                    checkRemainingAndUpdate()
                }
            },
            onSkipClick = { position ->
                adapter.removeItem(position)
                checkRemainingAndUpdate()
            }
        )

        binding.viewPagerReview.adapter = adapter
        adapter.submitList(results)

        binding.viewPagerReview.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateProgressSubtitle(position)
            }
        })
    }

    private fun setupListeners() {
        binding.btnSaveAllRemaining.setOnClickListener {
            val remaining = adapter.getRemainingItems()
            if (remaining.isNotEmpty()) {
                binding.btnSaveAllRemaining.isEnabled = false
                viewModel.saveAllRemainingCards(remaining) { count ->
                    savedCount += count
                    finishWithSummary()
                }
            } else {
                finishWithSummary()
            }
        }
    }

    private fun checkRemainingAndUpdate() {
        val remainingCount = adapter.itemCount
        if (remainingCount == 0) {
            finishWithSummary()
        } else {
            val currentPos = binding.viewPagerReview.currentItem
            updateProgressSubtitle(currentPos)
        }
    }

    private fun updateProgressSubtitle(currentPos: Int) {
        val remainingCount = adapter.itemCount
        if (remainingCount > 0) {
            val posDisplay = (currentPos + 1).coerceAtMost(remainingCount)
            supportActionBar?.subtitle = getString(R.string.review_card_progress, posDisplay, remainingCount)
        } else {
            supportActionBar?.subtitle = null
        }
    }

    private fun showEmptyState() {
        binding.viewPagerReview.visibility = View.GONE
        binding.bottomBar.visibility = View.GONE
        binding.layoutEmpty.visibility = View.VISIBLE
        supportActionBar?.subtitle = null
    }

    private fun finishWithSummary() {
        Toast.makeText(this, getString(R.string.review_completed_toast, savedCount), Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        const val EXTRA_BATCH_RESULTS = "extra_batch_results"
    }
}
