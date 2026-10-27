package com.businesscard.scanner.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.businesscard.scanner.BuildConfig
import com.businesscard.scanner.R
import com.businesscard.scanner.databinding.ActivityScanBinding
import com.businesscard.scanner.ocr.TextParser
import com.businesscard.scanner.ocr.OcrHelper
import com.businesscard.scanner.ocr.GeminiContactParser
import com.businesscard.scanner.ocr.BatchResultWrapper
import com.businesscard.scanner.ocr.ParsedResult
import com.google.mlkit.vision.common.InputImage
import java.io.File
import java.io.IOException
import java.util.Collections

class ScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanBinding
    private var imageCapture: ImageCapture? = null

    private var frontImagePath = ""
    private var backImagePath = ""
    private var frontText = ""
    private var backText = ""
    private var isScanningBack = false
    private var eventTag = ""
    private var isBatchMode = false

    private val ocrHelper = OcrHelper()
    private val geminiParser = GeminiContactParser()

    private val ocrQueue = Channel<String>(Channel.UNLIMITED)
    private var queueProcessorJob: Job? = null

    // Thread-safe batch management
    private var capturedCount = 0
    private val batchResults: MutableList<ParsedResult> = Collections.synchronizedList(mutableListOf())
    private val pendingParseJobs: MutableList<Job> = Collections.synchronizedList(mutableListOf())

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else {
            Toast.makeText(this, getString(R.string.camera_permission_denied), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { runOcrFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (intent.getBooleanExtra(EXTRA_BATCH_MODE, false)) {
            isBatchMode = true
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermission.launch(Manifest.permission.CAMERA)
        }

        binding.btnCapture.setOnClickListener { captureImage() }
        binding.btnGallery.setOnClickListener { pickImage.launch("image/*") }
        binding.btnFlip.setOnClickListener { flipToBack() }
        binding.btnSaveContact.setOnClickListener { finishScanAndReview() }
        binding.btnPhotoScan.setOnClickListener { openPhotoScan() }
        
        binding.btnReviewBatch.setOnClickListener { finishScanAndReview() }

        updateUI()
        updateBatchStatusUI()

        lifecycleScope.launch {
            geminiParser.downloadIfNeeded()
            geminiParser.warmup()
        }

        queueProcessorJob = lifecycleScope.launch {
            for (imagePath in ocrQueue) {
                processCapturedImageInBackground(imagePath)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_scan, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_event_tag -> { showEventTagDialog(); true }
            R.id.action_batch_mode -> { toggleBatchMode(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toggleBatchMode() {
        isBatchMode = !isBatchMode
        updateBatchStatusUI()
        val msg = if (isBatchMode) getString(R.string.batch_mode_active) else getString(R.string.scan_front_instruction)
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun showEventTagDialog() {
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()
        val input = EditText(this).apply {
            hint = getString(R.string.event_name_hint)
            setText(eventTag)
            selectAll()
            setPadding(pad, pad, pad, pad)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.event_mode_title))
            .setView(input)
            .setPositiveButton(R.string.set) { _, _ ->
                eventTag = input.text.toString().trim()
                updateEventUI()
            }
            .setNeutralButton(R.string.clear) { _, _ ->
                eventTag = ""
                updateEventUI()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateEventUI() {
        supportActionBar?.subtitle = if (eventTag.isNotBlank())
            getString(R.string.event_active_subtitle, eventTag)
        else null
    }

    private fun openPhotoScan() {
        val packages = listOf(
            "com.google.android.apps.photos.scanner",
            "com.google.android.apps.photoscan"
        )
        for (pkg in packages) {
            val launch = packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                startActivity(launch)
                return
            }
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=com.google.android.apps.photos.scanner")))
        } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.photos.scanner")))
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val resolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build().also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setResolutionSelector(resolutionSelector)
                .build()

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                )
                setupTapToFocus(camera)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTapToFocus(camera: Camera) {
        binding.viewFinder.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val factory = binding.viewFinder.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(
                    point,
                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                ).setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS).build()
                camera.cameraControl.startFocusAndMetering(action)
            }
            true
        }
    }

    private fun captureImage() {
        val imageCapture = imageCapture ?: return

        val fileName = "card_${if (isScanningBack) "back" else "front"}_${System.currentTimeMillis()}.jpg"
        val file = File(filesDir, fileName)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturedCount++
                    updateBatchStatusUI()
                    
                    if (isBatchMode) {
                        Toast.makeText(this@ScanActivity, getString(R.string.next_card_ready), Toast.LENGTH_SHORT).show()
                    }

                    ocrQueue.trySend(file.absolutePath)
                }
                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(this@ScanActivity, getString(R.string.capture_failed, exc.message), Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun runOcrFromUri(uri: Uri) {
        setBusy(true)
        lifecycleScope.launch {
            try {
                val fileName = "card_${if (isScanningBack) "back" else "front"}_${System.currentTimeMillis()}.jpg"
                val destFile = File(filesDir, fileName)
                withContext(Dispatchers.IO) {
                    val stream = contentResolver.openInputStream(uri)
                        ?: throw IOException("Could not open image stream")
                    stream.use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                capturedCount++
                updateBatchStatusUI()
                binding.btnReviewBatch.visibility = View.VISIBLE
                ocrQueue.trySend(destFile.absolutePath)
            } catch (e: Exception) {
                Toast.makeText(this@ScanActivity, "Could not read image: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                setBusy(false)
            }
        }
    }

    private suspend fun processCapturedImageInBackground(imagePath: String) {
        try {
            val text = ocrHelper.recognizeFile(this@ScanActivity, imagePath)
            if (text.isNotBlank()) {
                val currentFrontText = if (!isScanningBack) text else frontText
                val currentBackText = if (isScanningBack) text else backText
                val currentFrontPath = if (!isScanningBack) imagePath else frontImagePath
                val currentBackPath = if (isScanningBack) imagePath else backImagePath

                val parsed = TextParser.parseSmart(currentFrontText, currentBackText, geminiParser)
                val initialTags = eventTag.ifBlank { "" }

                val result = ParsedResult(
                    personName = parsed.personName,
                    phoneticName = parsed.phoneticName,
                    companyName = parsed.companyName,
                    jobTitle = parsed.jobTitle,
                    phone = parsed.phone,
                    mobile = parsed.mobile,
                    email = parsed.email,
                    website = parsed.website,
                    address = parsed.address,
                    rawTextFront = currentFrontText,
                    rawTextBack = currentBackText,
                    frontImagePath = currentFrontPath,
                    backImagePath = currentBackPath,
                    tags = initialTags,
                    handwrittenNote = parsed.handwrittenNote,
                    handwrittenDate = parsed.handwrittenDate
                )
                batchResults.add(result)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Background parsing error: ${e.message}", e)
        } finally {
            withContext(Dispatchers.Main) {
                updateBatchStatusUI()
            }
        }
    }

    private fun updateBatchStatusUI() {
        if (capturedCount > 0) {
            binding.textBatchStatus.visibility = View.VISIBLE
            binding.textBatchStatus.text = getString(R.string.batch_status_format, capturedCount, batchResults.size)
            binding.btnReviewBatch.visibility = View.VISIBLE
            // 親レイアウトも表示しないとボタンが見えないため、ここを修正
            binding.rowSecondary.visibility = View.VISIBLE
        } else if (isBatchMode) {
            binding.textBatchStatus.visibility = View.VISIBLE
            binding.textBatchStatus.text = getString(R.string.batch_mode_active)
            binding.btnReviewBatch.visibility = View.VISIBLE
            binding.rowSecondary.visibility = View.VISIBLE
        } else {
            binding.textBatchStatus.visibility = View.GONE
        }
    }

    private fun finishScanAndReview() {
        setBusy(true)
        lifecycleScope.launch {
            val activeJobs = pendingParseJobs.filter { it.isActive }
            if (activeJobs.isNotEmpty()) {
                binding.textBatchStatus.text = getString(R.string.parsing_remaining, activeJobs.size)
                activeJobs.joinAll()
            }
            setBusy(false)

            if (batchResults.isEmpty()) {
                Toast.makeText(this@ScanActivity, getString(R.string.no_cards_to_review), Toast.LENGTH_SHORT).show()
                return@launch
            }

            val intent = Intent(this@ScanActivity, ReviewContactsActivity::class.java).apply {
                putExtra(
                    ReviewContactsActivity.EXTRA_BATCH_RESULTS,
                    BatchResultWrapper(ArrayList(batchResults))
                )
            }
            startActivity(intent)
            finish()
        }
    }

    private fun flipToBack() {
        isScanningBack = true
        binding.textOcrResult.text = ""
        binding.rowSecondary.visibility = View.GONE
        updateUI()
        Toast.makeText(this, getString(R.string.flip_card), Toast.LENGTH_SHORT).show()
    }

    private fun setBusy(busy: Boolean) {
        binding.progressBar.visibility = if (busy) View.VISIBLE else View.GONE
        binding.btnCapture.isEnabled = !busy
        binding.btnGallery.isEnabled = !busy
        binding.btnReviewBatch.isEnabled = !busy
    }

    private fun updateUI() {
        binding.textScanInstruction.text = if (isScanningBack)
            getString(R.string.scan_back_instruction)
        else
            getString(R.string.scan_front_instruction)
        supportActionBar?.title = if (isScanningBack)
            getString(R.string.scan_back_title)
        else
            getString(R.string.scan_front_title)
    }

    override fun onDestroy() {
        super.onDestroy()
        queueProcessorJob?.cancel()
        ocrQueue.close()
        ocrHelper.close()
        geminiParser.close()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        private const val TAG = "ScanActivity"
        const val EXTRA_BATCH_MODE = "extra_batch_mode"
    }
}
