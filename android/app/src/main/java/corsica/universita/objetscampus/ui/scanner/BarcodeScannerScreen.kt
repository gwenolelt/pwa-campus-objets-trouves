package corsica.universita.objetscampus.ui.scanner

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Fonctionnalite Native 1 (impossible en PWA) :
 * Pipeline CameraX natif combinant simultanement :
 * 1. Preview temps-reel liee au LifecycleOwner Compose
 * 2. ImageAnalysis temps-reel avec Google ML Kit BarcodeScanning on-device (detection QR/Code-barres a 30 fps)
 * 3. ImageCapture haute resolution pour prendre la photo de l'objet trouve dans la meme session camera.
 */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerScreen(
    modeTitle: String = "Scanner QR / Code-barres ou Photo",
    onBarcodeDetected: (rawValue: String, formatName: String) -> Unit,
    onPhotoCaptured: (photoPath: String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    var detectedCode by remember { mutableStateOf<Pair<String, String>?>(null) }
    var isTakingPhoto by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(modeTitle, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Annuler"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = runCatching { cameraProviderFuture.get() }.getOrNull()
                            ?: return@addListener
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                        val scanner = BarcodeScanning.getClient()
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { imageAnalysis ->
                                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                    processFrameWithMlKit(
                                        imageProxy = imageProxy,
                                        onFound = { value, format ->
                                            if (detectedCode == null) {
                                                detectedCode = value to format
                                            }
                                        },
                                        scanner = scanner
                                    )
                                }
                            }

                        runCatching {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                                imageCapture
                            )
                        }.onFailure { e ->
                            Log.w("CameraXScanner", "Impossible d'attacher CameraX", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )

            // Viseur central de scan QR / Code-barres
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .align(Alignment.Center)
                    .border(
                        width = 3.dp,
                        color = if (detectedCode != null) Color(0xFF10B981) else Color(0xFF3B82F6),
                        shape = RoundedCornerShape(20.dp)
                    )
            )

            // Panneau de controle en bas
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xE60F172A)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val found = detectedCode
                    if (found != null) {
                        Text(
                            text = "✅ Code détecté (${found.second}) : ${found.first}",
                            color = Color(0xFF34D399),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onBarcodeDetected(found.first, found.second) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Utiliser ce code campus")
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    } else {
                        Text(
                            text = "Pointez la caméra vers un QR code de salle, une carte étudiante/CROUS ou un code-barres d'objet.",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (!isTakingPhoto) {
                                    isTakingPhoto = true
                                    capturePhotoToStore(
                                        context = context,
                                        imageCapture = imageCapture,
                                        onSaved = { path ->
                                            isTakingPhoto = false
                                            onPhotoCaptured(path)
                                        },
                                        onError = {
                                            isTakingPhoto = false
                                        }
                                    )
                                }
                            },
                            enabled = !isTakingPhoto,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isTakingPhoto) "Capture..." else "Prendre photo")
                        }

                        // Bouton pratique pour tester sur l'emulateur Android sans avoir de QR physique
                        FilledTonalButton(
                            onClick = {
                                val sampleCode = "CAMPUS-CORTE-BAT-A-${(100..999).random()}"
                                onBarcodeDetected(sampleCode, "QR_CODE")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simuler QR")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
private fun processFrameWithMlKit(
    imageProxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onFound: (String, String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            val first = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
            if (first != null) {
                val raw = first.rawValue.orEmpty()
                val fmt = when (first.format) {
                    Barcode.FORMAT_QR_CODE -> "QR_CODE"
                    Barcode.FORMAT_EAN_13 -> "EAN_13"
                    Barcode.FORMAT_CODE_128 -> "CODE_128"
                    else -> "BARCODE_${first.format}"
                }
                onFound(raw, fmt)
            }
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

private fun capturePhotoToStore(
    context: Context,
    imageCapture: ImageCapture,
    onSaved: (String) -> Unit,
    onError: () -> Unit
) {
    val dir = File(context.filesDir, "photos").apply { mkdirs() }
    val photoFile = File(dir, "${UUID.randomUUID()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onSaved(photoFile.absolutePath)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraXScanner", "Erreur lors de la capture photo", exception)
                onError()
            }
        }
    )
}
