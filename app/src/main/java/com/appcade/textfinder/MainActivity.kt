package com.appcade.textfinder

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    //region declaration
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var surfaceView: SurfaceView
    private lateinit var capturedImageView: ZoomableImageView
    private lateinit var inputET: EditText
    private lateinit var cameraButton: Button
    private lateinit var galleryButton: Button
    private lateinit var captureButton: Button
    private lateinit var retakeButton: Button
    private lateinit var newPictureButton: Button
    private lateinit var pdfButton: Button
    private lateinit var homeButton: ImageButton
    private lateinit var nextPageButton: ImageButton
    private lateinit var prevPageButton: ImageButton
    private lateinit var newPDFButton: Button
    private lateinit var imageCapture: ImageCapture
    private lateinit var pageCounterTV: TextView

    private var recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var mode: Mode = Mode.CAMERA
    private var selectedPhotoUri: Uri? = null
    private var currentPhotoFile: File? = null
    private var currentPDFFile: File? = null
    private var currentPDFPage: Int = 0
    private var currentPDFPages: MutableList<File> = mutableListOf()
    private var currentPDFPagesContaining: MutableList<File> = mutableListOf()
    private var targetWord: String = ""
    //endregion

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //region initialization
        surfaceView = findViewById(R.id.surfaceView)
        cameraButton = findViewById(R.id.cameraButton)
        inputET = findViewById(R.id.inputWord)
        cameraExecutor = Executors.newSingleThreadExecutor()
        capturedImageView = findViewById(R.id.zoomableImageView)
        galleryButton = findViewById(R.id.galleryButton)
        captureButton = findViewById(R.id.captureButton)
        retakeButton = findViewById(R.id.retakeButton)
        newPictureButton = findViewById(R.id.newPictureButton)
        pdfButton = findViewById(R.id.pdfButton)
        homeButton = findViewById(R.id.homeButton)
        nextPageButton = findViewById(R.id.nextPageButton)
        prevPageButton = findViewById(R.id.prevPageButton)
        newPDFButton = findViewById(R.id.newPDFButton)
        pageCounterTV = findViewById(R.id.pageCounterTV)
        //endregion

        setDefaultVisibilities()

        val selectImageLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val data = result.data
                    val imageUri = data?.data

                    if (imageUri != null) {
                        processImageFromGallery(imageUri)
                    } else {
                        Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
                    }
                }
            }

        inputET.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                inputET.setTextColor(Color.BLACK)
                targetWord = inputET.text.toString().trim()
                if (targetWord.isNotEmpty()) {
                    closeKeyboard()

                    when (mode) {
                        Mode.GALLERY -> {
                            if (selectedPhotoUri == null) {
                                openGallery(selectImageLauncher)
                            } else {
                                processImageFromGallery(selectedPhotoUri!!)
                            }
                            newPictureButton.visibility = View.VISIBLE
                        }

                        Mode.CAMERA -> {
                            if (currentPhotoFile == null) {
                                startCamera()
                            } else {
                                //processPhoto(currentPhotoFile!!)
                                processFileAndDetectTargetWord(currentPhotoFile!!, true) {}
                            }
                            captureButton.visibility = View.VISIBLE
                            retakeButton.visibility = View.VISIBLE
                        }

                        Mode.PDF -> {
                            if (currentPDFFile == null) {
                                processPDF()
                            } else {
                                currentPDFPages.clear()
                                splitCurrentPdfToJpegPages()
                                selectCurrentPDFContainingPages {
                                    if (currentPDFPagesContaining.isNotEmpty()) {
                                        processFileAndDetectTargetWord(
                                            currentPDFPagesContaining[pageIndex()],
                                            true
                                        ) {}
                                    } else {
                                        processFileAndDetectTargetWord(currentPDFPages[0], true) {}
                                    }
                                }
                            }
                            nextPageButton.visibility = View.VISIBLE
                            prevPageButton.visibility = View.VISIBLE
                            newPDFButton.visibility = View.VISIBLE
                        }
                    }
                    true
                } else {
                    Log.e("TextFinder", "No word entered")
                    Toast.makeText(
                        this,
                        "Please enter the word you want to find.",
                        Toast.LENGTH_SHORT
                    ).show()
                    false
                }
            } else {
                false
            }
        }

        inputET.setOnClickListener {
            inputET.setTextColor(Color.BLACK)
        }

        cameraButton.setOnClickListener {
            cameraButton.visibility = View.GONE
            galleryButton.visibility = View.GONE
            pdfButton.visibility = View.GONE
            inputET.visibility = View.VISIBLE
            mode = Mode.CAMERA
        }

        retakeButton.setOnClickListener {
            startCamera()
        }

        captureButton.setOnClickListener {
            if (!targetWord.equals(null)) {
                capturePhoto()
            } else {
                Log.e("Capture failure", "No word entered")
            }
        }

        galleryButton.setOnClickListener {
            cameraButton.visibility = View.GONE
            galleryButton.visibility = View.GONE
            pdfButton.visibility = View.GONE
            inputET.visibility = View.VISIBLE
            mode = Mode.GALLERY
        }

        pdfButton.setOnClickListener {
            mode = Mode.PDF
            inputET.visibility = View.VISIBLE
            cameraButton.visibility = View.GONE
            galleryButton.visibility = View.GONE
            pdfButton.visibility = View.GONE
        }

        newPictureButton.setOnClickListener {
            openGallery(selectImageLauncher)
        }

        homeButton.setOnClickListener {
            inputET.setText("")
            pageCounterTV.text = ""
            inputET.setTextColor(Color.BLACK)
            currentPhotoFile = null
            selectedPhotoUri = null
            currentPDFFile = null
            currentPDFPages.clear()
            currentPDFPagesContaining.clear()
            setDefaultVisibilities()
            closeKeyboard()
        }

        nextPageButton.setOnClickListener {
            if (currentPDFPagesContaining.isNotEmpty()) {
                currentPDFPage++
                //processPhoto(currentPDFPagesContaining[pageIndex()])
                processFileAndDetectTargetWord(currentPDFPagesContaining[pageIndex()], true) {}
            }
        }

        prevPageButton.setOnClickListener {
            if (currentPDFPagesContaining.isNotEmpty()) {
                currentPDFPage--
                //processPhoto(currentPDFPagesContaining[pageIndex()])
                processFileAndDetectTargetWord(currentPDFPagesContaining[pageIndex()], true) {}
            }
        }

        newPDFButton.setOnClickListener {
            processPDF()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Overrides backPress closing the app
            }
        })

    }

    private fun closeKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(inputET.windowToken, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun pageIndex(): Int {
        if (currentPDFPage < 0) {
            currentPDFPage = currentPDFPagesContaining.count() - 1
        }
        return currentPDFPage % currentPDFPagesContaining.count()
    }

    private fun setDefaultVisibilities() {
        cameraButton.visibility = View.VISIBLE
        galleryButton.visibility = View.VISIBLE
        pdfButton.visibility = View.VISIBLE
        homeButton.visibility = View.VISIBLE

        inputET.visibility = View.GONE
        captureButton.visibility = View.GONE
        retakeButton.visibility = View.GONE
        newPictureButton.visibility = View.GONE
        surfaceView.visibility = View.GONE
        capturedImageView.visibility = View.GONE
        nextPageButton.visibility = View.GONE
        prevPageButton.visibility = View.GONE
        newPDFButton.visibility = View.GONE
        pageCounterTV.visibility = View.GONE
    }

    private fun startCamera() {
        // handle camera permission
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), 1)
        }

        runOnUiThread { inputET.setTextColor(Color.BLACK) }
        surfaceView.visibility = View.GONE

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = androidx.camera.core.Preview.Builder().build()
            preview.setSurfaceProvider { request ->
                val surface = surfaceView.holder.surface

                request.provideSurface(surface, ContextCompat.getMainExecutor(this)) { result ->
                    Log.d("Preview", "Surface provided: $result")
                }
            }

            imageCapture = ImageCapture.Builder()
                .setTargetRotation(Surface.ROTATION_0)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                surfaceView.visibility = View.VISIBLE
            } catch (exc: Exception) {
                Log.e("CameraX", "Error binding camera use cases", exc)
            }
        }, ContextCompat.getMainExecutor(this))

    }

    private fun capturePhoto() {
        val photoFile = File(cacheDir, "temp_photo.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    currentPhotoFile = photoFile
                    showCapturedImage(photoFile)
                    //processPhoto(photoFile)
                    processFileAndDetectTargetWord(photoFile, true) {}
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraX", "Photo capture failed: ${exception.message}", exception)
                }
            })
    }

    private fun showCapturedImage(photoFile: File) {
        selectedPhotoUri = Uri.fromFile(photoFile)
        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
        //val bitmap = correctBitmapOrientation(photoFile.absolutePath)
        runOnUiThread {
            capturedImageView.setImageBitmap(bitmap)
            capturedImageView.resetZoom()
        }
    }

    private fun highlightWord(detectedText: String, processedText: String) {
        val wordRegex = "\\b${Regex.escape(targetWord)}\\b".toRegex(RegexOption.IGNORE_CASE)

        val exactMatch =
            wordRegex.containsMatchIn(detectedText) || wordRegex.containsMatchIn(processedText)
        val partialMatch = detectedText.contains(targetWord, ignoreCase = true) ||
                processedText.contains(targetWord, ignoreCase = true)

        val color = when {
            exactMatch -> Color.GREEN
            partialMatch -> ContextCompat.getColor(
                this,
                R.color.orange
            )

            else -> Color.RED
        }

        runOnUiThread { inputET.setTextColor(color) }
    }

    private fun highlightWordOnImage(file: File, detectedText: Text) {
        // load bitmap from gallery
        val bitmap =
            BitmapFactory.decodeFile(file.absolutePath)?.copy(Bitmap.Config.ARGB_8888, true)
        //val bitmap = correctBitmapOrientation(photoFile.absolutePath).copy(Bitmap.Config.ARGB_8888, true)
        if (bitmap == null) {
            Log.e("ImageProcessing", "Failed to load bitmap")
            return
        }

        // create Canvas for drawing
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 7f
        }

        // remove punctuation from target word
        val sanitizedTargetWord = targetWord.replace(Regex("\\p{Punct}"), "")

        // iteration through text blocks and searching for target word
        for (block in detectedText.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    // remove punctuation z text element
                    val sanitizedElementText = element.text.replace(Regex("\\p{Punct}"), "")

                    // compare
                    if (sanitizedElementText.equals(sanitizedTargetWord, ignoreCase = true)) {
                        val box = element.boundingBox
                        if (box != null) {
                            // draw rectangles around found words
                            Log.e("BoundingBox", "Bounding box for ${element.text}: $box")
                            canvas.drawRect(box, paint)
                        } else {
                            Log.e("BoundingBox", "No bounding box for ${element.text}")
                        }
                    }
                }
            }
        }

        // show new bitmaps in ImageView
        runOnUiThread {
            capturedImageView.setImageBitmap(bitmap)
            capturedImageView.visibility = View.VISIBLE
            surfaceView.visibility = View.GONE
        }
    }

    private fun mergeLines(textBlocks: List<Text.TextBlock>): String {
        val builder = StringBuilder()
        for (block in textBlocks) {
            for ((index, line) in block.lines.withIndex()) {
                val text = line.text.trimEnd()
                if (text.endsWith("-")) {
                    builder.append(text.removeSuffix("-"))
                } else {
                    builder.append(text)
                    if (index < block.lines.size - 1) {
                        builder.append(" ")
                    }
                }
            }
            builder.append("\n")
        }
        return builder.toString().trim()
    }

    /*
        private fun correctBitmapOrientation(filePath: String): Bitmap {
            val bitmap = BitmapFactory.decodeFile(filePath)
            val exif = ExifInterface(filePath)

            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }

            /*
            return if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
             */
            return run {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }

        }
     */

    private fun openGallery(launcher: ActivityResultLauncher<Intent>) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ (API 34)
            Intent(MediaStore.ACTION_PICK_IMAGES)
        } else {
            // Older Versions
            requestStoragePermission(this)
            Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        }
        launcher.launch(intent)
    }

    private fun requestStoragePermission(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {  // API < 33
            if (ContextCompat.checkSelfPermission(
                    activity, android.Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                    100
                )
            }
        }
    }

    private fun processImageFromGallery(imageUri: Uri) {
        try {
            contentResolver.openInputStream(imageUri)?.use { inputStream ->
                val selectedImage = BitmapFactory.decodeStream(inputStream)

                if (selectedImage != null) {
                    val tempFile = File(cacheDir, "gallery_image.jpg")
                    tempFile.outputStream().use { out ->
                        selectedImage.compress(Bitmap.CompressFormat.JPEG, 100, out)
                    }

                    processFileAndDetectTargetWord(tempFile, true) {}
                    showCapturedImage(tempFile)
                } else {
                    Log.e("Gallery", "Failed to decode image.")
                }
            }
        } catch (e: Exception) {
            Log.e("Gallery", "Error processing image", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun processPDF() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        this.startActivityForResult(intent, 1002)
    }

    //region Suppress and Deprecation
    @SuppressLint("SetTextI18n")
    @Suppress("DEPRECATION")
    @Deprecated(
        "This method has been deprecated in favor of using the Activity Result API\n" +
                "which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n" +
                "contracts for common intents available in\n" +
                "{@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n" +
                "testing, and allow receiving results in separate, testable classes independent from your\n" +
                "activity. Use\n" +
                "{@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)}\n" +
                "with the appropriate {@link ActivityResultContract} and handling the result in the\n" +
                "{@link ActivityResultCallback#onActivityResult(Object) callback}."
    )
    //endregion
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1002 && resultCode == RESULT_OK) {
            data?.data?.let { pdfUri ->
                savePdfToInternalStorage(pdfUri)
                splitCurrentPdfToJpegPages()
                selectCurrentPDFContainingPages {
                    if (currentPDFPagesContaining.isNotEmpty()) {
                        //processPhoto(currentPDFPagesContaining[pageIndex()])
                        processFileAndDetectTargetWord(
                            currentPDFPagesContaining[pageIndex()],
                            true
                        ) {}
                    } else {
                        //processFileAndDetectTargetWord(currentPDFPages[0], true) {}
                        Toast.makeText(this, "No matches found.", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
        }
        //Toast.makeText(this, "Please wait.", Toast.LENGTH_LONG).show()
    }

    private fun savePdfToInternalStorage(pdfUri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(pdfUri)
            val fileName = "selected_file.pdf"
            val outputFile = File(filesDir, fileName)
            currentPDFFile = outputFile

            inputStream?.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun splitCurrentPdfToJpegPages() {
        val pageImageFiles = mutableListOf<File>()
        try {
            val fileDescriptor =
                ParcelFileDescriptor.open(currentPDFFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(fileDescriptor)

            // processes pdf pages
            for (pageIndex in 0 until pdfRenderer.pageCount) {
                val page = pdfRenderer.openPage(pageIndex)

                val bitmap = Bitmap.createBitmap(
                    page.width,
                    page.height,
                    Bitmap.Config.ARGB_8888
                )

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                // saves pages as JPEG
                val pageFile = File(cacheDir, "page_${pageIndex + 1}.jpg")
                FileOutputStream(pageFile).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                }
                pageImageFiles.add(pageFile)
            }
            pdfRenderer.close()
            fileDescriptor.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        currentPDFPages = pageImageFiles
    }

    @SuppressLint("SetTextI18n")
    private fun selectCurrentPDFContainingPages(onComplete: () -> Unit) {
        currentPDFPagesContaining.clear()

        pageCounterTV.text = "0 of ${currentPDFPages.size}"
        pageCounterTV.visibility = View.VISIBLE

        var counter = 0
        currentPDFPages.forEach { page ->
            processFileAndDetectTargetWord(page, false) { contains ->
                if (contains) {
                    currentPDFPagesContaining.add(page)
                }
                counter++
                pageCounterTV.text = "$counter of ${currentPDFPages.size}"
                if (counter == currentPDFPages.size) {
                    pageCounterTV.visibility = View.GONE
                    closeKeyboard()
                    onComplete()
                }
            }
        }
    }

    private fun processFileAndDetectTargetWord(
        file: File,
        useGraphic: Boolean,
        callback: (Boolean) -> Unit
    ) {
        val image = InputImage.fromFilePath(this, Uri.fromFile(file))

        recognizer.process(image)
            .addOnSuccessListener { visionText ->

                // filter out MLKit signature
                val cleanBlocks = visionText.textBlocks.filter { block ->
                    !block.text.contains("com.google.mlkit.vision.text.text", ignoreCase = true)
                }

                if (useGraphic) {
                    val processedText = mergeLines(cleanBlocks)
                    highlightWord(visionText.text, processedText)
                    highlightWordOnImage(file, visionText)
                }

                if (visionText.text.contains(targetWord, ignoreCase = true) || mergeLines(
                        cleanBlocks
                    ).contains(targetWord, ignoreCase = true)
                ) {
                    callback(true)
                } else {
                    callback(false)
                }
            }
            .addOnFailureListener { e ->
                Log.e("MLKit", "Text recognition failed", e)
                callback(false)
            }
    }

}