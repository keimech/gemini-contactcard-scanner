package com.businesscard.scanner.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.businesscard.scanner.data.BusinessCard
import com.businesscard.scanner.databinding.ItemReviewContactBinding
import com.businesscard.scanner.ocr.ParsedResult
import java.io.File

class ReviewAdapter(
    private val onSaveClick: (BusinessCard, Int) -> Unit,
    private val onSkipClick: (Int) -> Unit
) : RecyclerView.Adapter<ReviewAdapter.ViewHolder>() {

    private val items = mutableListOf<ParsedResult>()

    fun submitList(results: List<ParsedResult>) {
        items.clear()
        items.addAll(results)
        notifyDataSetChanged()
    }

    fun removeItem(position: Int) {
        if (position in items.indices) {
            items.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, items.size - position)
        }
    }

    fun getItem(position: Int): ParsedResult? {
        return items.getOrNull(position)
    }

    fun getRemainingItems(): List<ParsedResult> = items.toList()

    class ViewHolder(val binding: ItemReviewContactBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReviewContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val binding = holder.binding

        // Populate fields
        binding.editPersonName.setText(item.personName)
        binding.editCompanyName.setText(item.companyName)
        binding.editJobTitle.setText(item.jobTitle)
        binding.editPhone.setText(item.phone)
        binding.editMobile.setText(item.mobile)
        binding.editEmail.setText(item.email)
        binding.editWebsite.setText(item.website)
        binding.editAddress.setText(item.address)
        binding.editTags.setText(item.tags)

        // Images
        if (item.frontImagePath.isNotBlank()) {
            binding.ivReviewFrontImage.visibility = View.VISIBLE
            Glide.with(binding.ivReviewFrontImage.context)
                .load(File(item.frontImagePath))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(binding.ivReviewFrontImage)
        } else {
            binding.ivReviewFrontImage.visibility = View.GONE
        }

        if (item.backImagePath.isNotBlank()) {
            binding.ivReviewBackImage.visibility = View.VISIBLE
            Glide.with(binding.ivReviewBackImage.context)
                .load(File(item.backImagePath))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(binding.ivReviewBackImage)
        } else {
            binding.ivReviewBackImage.visibility = View.GONE
        }

        binding.btnSaveAndNext.setOnClickListener {
            val adapterPos = holder.bindingAdapterPosition
            if (adapterPos != RecyclerView.NO_POSITION) {
                val card = BusinessCard(
                    personName = binding.editPersonName.text.toString().trim(),
                    companyName = binding.editCompanyName.text.toString().trim(),
                    jobTitle = binding.editJobTitle.text.toString().trim(),
                    phone = binding.editPhone.text.toString().trim(),
                    mobile = binding.editMobile.text.toString().trim(),
                    email = binding.editEmail.text.toString().trim(),
                    website = binding.editWebsite.text.toString().trim(),
                    address = binding.editAddress.text.toString().trim(),
                    rawTextFront = item.rawTextFront,
                    rawTextBack = item.rawTextBack,
                    frontImagePath = item.frontImagePath,
                    backImagePath = item.backImagePath,
                    tags = binding.editTags.text.toString().trim()
                )
                onSaveClick(card, adapterPos)
            }
        }

        binding.btnSkip.setOnClickListener {
            val adapterPos = holder.bindingAdapterPosition
            if (adapterPos != RecyclerView.NO_POSITION) {
                onSkipClick(adapterPos)
            }
        }
    }

    override fun getItemCount() = items.size
}
