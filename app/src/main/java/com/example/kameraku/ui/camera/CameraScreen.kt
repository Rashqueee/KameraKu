package com.example.kameraku.ui.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.Surface
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.permissions.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen() {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    when {
        cameraPermissionState.status.isGranted -> {
            CameraPreviewScreen()
        }
        cameraPermissionState.status.shouldShowRationale -> {
            PermissionRationaleDialog(
                onRequestPermission = { cameraPermissionState.launchPermissionRequest() },
                onDismiss = { }
            )
        }
        else -> {
            PermissionRequestScreen(
                onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
            )
        }
    }
}

@Composable
fun CameraPreviewScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isCameraReady by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }

    // Camera controls
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_OFF) }
    var isTorchOn by remember { mutableStateOf(false) }
    var lastCapturedImageUri by remember { mutableStateOf<Uri?>(null) }

    // Get camera info
    val hasFlash = remember(camera) {
        camera?.cameraInfo?.hasFlashUnit() ?: false
    }

    // Initialize camera
    LaunchedEffect(previewView, lensFacing) {
        val pv = previewView ?: return@LaunchedEffect
        pv.doOnLayout {
            try {
                val (capture, cam) = bindCamera(
                    context = context,
                    lifecycleOwner = lifecycleOwner,
                    previewView = pv,
                    lensFacing = lensFacing
                )
                imageCapture = capture
                camera = cam
                isCameraReady = true
            } catch (e: Exception) {
                Toast.makeText(context, "Camera error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Update flash mode
    LaunchedEffect(flashMode) {
        imageCapture?.flashMode = flashMode
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }.also { previewView = it }
            }
        )

        // Loading overlay
        if (!isCameraReady) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Text("Initializing camera...", color = Color.White)
                }
            }
        }

        // Top controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Flash control
            if (hasFlash) {
                IconButton(
                    onClick = {
                        flashMode = when (flashMode) {
                            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                            else -> ImageCapture.FLASH_MODE_OFF
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = when (flashMode) {
                            ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                            ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                            else -> Icons.Default.FlashOff
                        },
                        contentDescription = "Flash",
                        tint = Color.White
                    )
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }

            // Torch control
            if (hasFlash && lensFacing == CameraSelector.LENS_FACING_BACK) {
                IconButton(
                    onClick = {
                        isTorchOn = !isTorchOn
                        camera?.cameraControl?.enableTorch(isTorchOn)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (isTorchOn) Color.Yellow.copy(alpha = 0.7f)
                            else Color.Black.copy(alpha = 0.5f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isTorchOn) Icons.Default.FlashlightOn
                        else Icons.Default.FlashlightOff,
                        contentDescription = "Torch",
                        tint = if (isTorchOn) Color.Black else Color.White
                    )
                }
            }
        }

        // Bottom controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail of last photo
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Gray.copy(alpha = 0.3f))
                    .clickable(enabled = lastCapturedImageUri != null) {
                        Toast
                            .makeText(context, "Photo saved in Gallery!", Toast.LENGTH_SHORT)
                            .show()
                    }
            ) {
                lastCapturedImageUri?.let { uri ->
                    Image(
                        painter = rememberAsyncImagePainter(uri),
                        contentDescription = "Last photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                if (lastCapturedImageUri == null) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.Center),
                        tint = Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            // Capture button
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCameraReady && !isCapturing) Color.White
                        else Color.Gray
                    )
                    .border(4.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    .clickable(enabled = isCameraReady && !isCapturing) {
                        isCapturing = true
                        imageCapture?.let { ic ->
                            takePhoto(context, ic) { uri ->
                                isCapturing = false
                                lastCapturedImageUri = uri
                                Toast
                                    .makeText(context, "Photo saved!", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = Color.DarkGray
                    )
                }
            }

            // Switch camera button
            IconButton(
                onClick = {
                    isTorchOn = false
                    camera?.cameraControl?.enableTorch(false)
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                    isCameraReady = false
                },
                modifier = Modifier
                    .size(60.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    Icons.Default.Cameraswitch,
                    contentDescription = "Switch camera",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Flash mode indicator
        AnimatedVisibility(
            visible = flashMode != ImageCapture.FLASH_MODE_OFF,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp),
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = when (flashMode) {
                        ImageCapture.FLASH_MODE_AUTO -> "Flash: Auto"
                        ImageCapture.FLASH_MODE_ON -> "Flash: On"
                        else -> "Flash: Off"
                    },
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

fun bindCamera(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    lensFacing: Int
): Pair<ImageCapture, Camera> {
    val cameraProvider = ProcessCameraProvider.getInstance(context).get()
    val rotation = previewView.display?.rotation ?: Surface.ROTATION_0

    val preview = Preview.Builder()
        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
        .setTargetRotation(rotation)
        .build()
        .apply { setSurfaceProvider(previewView.surfaceProvider) }

    val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
        .setTargetRotation(rotation)
        .setFlashMode(ImageCapture.FLASH_MODE_OFF)
        .setJpegQuality(95)
        .build()

    val cameraSelector = CameraSelector.Builder()
        .requireLensFacing(lensFacing)
        .build()

    cameraProvider.unbindAll()
    val camera = cameraProvider.bindToLifecycle(
        lifecycleOwner,
        cameraSelector,
        preview,
        imageCapture
    )

    return Pair(imageCapture, camera)
}

fun takePhoto(
    ctx: Context,
    imageCapture: ImageCapture,
    onSaved: (Uri) -> Unit
) {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val name = "IMG_$timeStamp.jpg"

    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KameraKu")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }

    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        ctx.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        values
    ).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(ctx),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                result.savedUri?.let { uri ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val updateValues = ContentValues().apply {
                            put(MediaStore.Images.Media.IS_PENDING, 0)
                        }
                        ctx.contentResolver.update(uri, updateValues, null, null)
                    }
                    onSaved(uri)
                }
            }

            override fun onError(exc: ImageCaptureException) {
                Toast.makeText(
                    ctx,
                    "Photo capture failed: ${exc.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    )
}

@Composable
fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.PhotoCamera,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "📸 Camera Permission",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "KameraKu needs camera access to take photos.\n\n" +
                            "✓ Photos stay on your device\n" +
                            "✓ Saved to Pictures/KameraKu\n" +
                            "✓ Full control over your photos",
                    style = MaterialTheme.typography.bodyMedium
                )

                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Camera Permission")
                }
            }
        }
    }
}

@Composable
fun PermissionRationaleDialog(
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
        },
        title = { Text("Camera Permission Required") },
        text = {
            Text(
                "Without camera permission, you cannot take photos. " +
                        "Please grant the permission to use KameraKu."
            )
        },
        confirmButton = {
            Button(onClick = onRequestPermission) {
                Text("Grant Permission")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not Now")
            }
        }
    )
}