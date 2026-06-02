package com.example.videocutter.ui.main

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

sealed interface ProcessingState {
  data class Processing(val progress: Float) : ProcessingState
  data class Success(
    val outputPath: String,
    val fileName: String,
    val galleryUri: Uri?
  ) : ProcessingState
  data class Error(val message: String) : ProcessingState
}

/**
 * Moves the freshly-encoded temp file into the public gallery:
 *  - Android 10+: inserts via MediaStore with IS_PENDING so the gallery
 *    doesn't show a partial file while the copy is in progress.
 *  - Android 9-: copies to Movies/VideoCutter and triggers a media scan.
 *
 * Returns the gallery URI on success, null on failure (file is still
 * readable at [tempPath] in that case).
 */
private suspend fun saveToGallery(context: Context, tempPath: String, fileName: String): Uri? {
  val tempFile = File(tempPath)
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    val resolver = context.contentResolver
    val cv = ContentValues().apply {
      put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
      put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
      put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/VideoCutter")
      put(MediaStore.Video.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv) ?: return null
    try {
      resolver.openOutputStream(uri)?.use { out ->
        tempFile.inputStream().use { it.copyTo(out) }
      }
      cv.clear()
      cv.put(MediaStore.Video.Media.IS_PENDING, 0)
      resolver.update(uri, cv, null, null)
      tempFile.delete()
      uri
    } catch (e: Exception) {
      resolver.delete(uri, null, null)
      null
    }
  } else {
    @Suppress("DEPRECATION")
    val publicDir = File(
      Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
      "VideoCutter"
    ).also { it.mkdirs() }
    val dest = File(publicDir, fileName)
    try {
      tempFile.copyTo(dest, overwrite = true)
      tempFile.delete()
      // Trigger media scanner so gallery picks it up immediately
      suspendCancellableCoroutine { cont ->
        MediaScannerConnection.scanFile(
          context,
          arrayOf(dest.absolutePath),
          arrayOf("video/mp4")
        ) { _, scannedUri -> cont.resume(scannedUri) }
      }
    } catch (e: Exception) {
      null
    }
  }
}

@OptIn(UnstableApi::class)
@Composable
fun PreviewResultScreen(
  videoUri: String,
  cropPercentage: Int,
  onBack: () -> Unit,
  onReset: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var processingState by remember { mutableStateOf<ProcessingState>(ProcessingState.Processing(0f)) }

  LaunchedEffect(videoUri, cropPercentage) {
    processingState = ProcessingState.Processing(0f)

    val inputUri = Uri.parse(videoUri)
    // Write the encoded file to a temp location in app-private cache first.
    // After encoding we move it into MediaStore so it appears in the gallery.
    val tempDir = context.cacheDir.also { it.mkdirs() }
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val outputFileName = "Cropped_${cropPercentage}pct_${timestamp}.mp4"
    val tempFile = File(tempDir, outputFileName)
    val outputPath = tempFile.absolutePath

    // Crop Bottom P%:
    // Height coordinate system goes from -1.0f (bottom) to 1.0f (top) (span of 2.0f).
    // The bottom P% corresponds to 2.0f * (P / 100).
    // So the crop bottom boundary starts at -1.0f + (2.0f * P / 100).
    val cropBottom = -1.0f + (2.0f * (cropPercentage / 100.0f))

    val cropEffect = Crop(
      /* left = */ -1.0f,
      /* right = */ 1.0f,
      /* bottom = */ cropBottom,
      /* top = */ 1.0f
    )

    val mediaItem = MediaItem.fromUri(inputUri)
    val editedMediaItem = EditedMediaItem.Builder(mediaItem)
      .setEffects(Effects(listOf(), listOf(cropEffect)))
      .build()

    val transformer = Transformer.Builder(context)
      .addListener(object : Transformer.Listener {
        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
          // Mark as success with the temp path first; gallery move happens below
          processingState = ProcessingState.Success(outputPath, outputFileName, null)
        }

        override fun onError(
          composition: Composition,
          exportResult: ExportResult,
          exportException: ExportException
        ) {
          processingState = ProcessingState.Error(exportException.message ?: "Failed to process video")
        }
      })
      .build()

    try {
      transformer.start(editedMediaItem, outputPath)

      // Polling loop for progress updating
      val progressHolder = ProgressHolder()
      while (true) {
        val currentState = processingState
        if (currentState is ProcessingState.Success || currentState is ProcessingState.Error) {
          // Move encoded file into gallery now that encoding is done
          if (currentState is ProcessingState.Success) {
            val galleryUri = saveToGallery(context, outputPath, outputFileName)
            processingState = currentState.copy(galleryUri = galleryUri)
          }
          break
        }
        val progressState = transformer.getProgress(progressHolder)
        if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
          processingState = ProcessingState.Processing(progressHolder.progress / 100f)
        }
        delay(250)
      }
    } catch (e: Exception) {
      processingState = ProcessingState.Error(e.message ?: "Failed to start transformation")
    }

    coroutineContext.job.invokeOnCompletion {
      try {
        transformer.cancel()
      } catch (e: Exception) {
        // Ignore cancel errors
      }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = when (processingState) {
              is ProcessingState.Processing -> "Cropping..."
              is ProcessingState.Success -> "Cropped Result"
              is ProcessingState.Error -> "Error"
            },
            fontWeight = FontWeight.Bold
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background
        )
      )
    },
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(16.dp),
      contentAlignment = Alignment.Center
    ) {
      when (val state = processingState) {
        is ProcessingState.Processing -> {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            CircularProgressIndicator(
              progress = state.progress,
              modifier = Modifier.size(72.dp),
              strokeWidth = 6.dp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
              text = "Cropping Watermark...",
              fontSize = 20.sp,
              fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "${(state.progress * 100).toInt()}% Completed",
              fontSize = 15.sp,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
              progress = state.progress,
              modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "Applying bottom crop to remove watermark. Please hold on...",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(horizontal = 24.dp)
            )
          }
        }
        is ProcessingState.Success -> {
          SuccessPreviewLayout(
            playbackUri = state.galleryUri ?: Uri.fromFile(File(state.outputPath)),
            fileName = state.fileName,
            onBack = onBack,
            onReset = onReset
          )
        }
        is ProcessingState.Error -> {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Info,
              contentDescription = "Error",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
              text = "Processing Failed",
              fontSize = 20.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = state.message,
              fontSize = 14.sp,
              color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
              textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
              onClick = onBack,
              modifier = Modifier.fillMaxWidth().height(48.dp),
              shape = RoundedCornerShape(12.dp)
            ) {
              Text("Try Again")
            }
          }
        }
      }
    }
  }
}

@Composable
fun SuccessPreviewLayout(
  playbackUri: Uri,
  fileName: String,
  onBack: () -> Unit,
  onReset: () -> Unit
) {
  val context = LocalContext.current

  // Instantiate and control the ExoPlayer
  val exoPlayer = remember(playbackUri) {
    ExoPlayer.Builder(context).build().apply {
      setMediaItem(MediaItem.fromUri(playbackUri))
      prepare()
      playWhenReady = true
    }
  }

  DisposableEffect(exoPlayer) {
    onDispose {
      exoPlayer.release()
    }
  }

  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.SpaceBetween,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Success Header
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
      ) {
        Icon(
          imageVector = Icons.Default.CheckCircle,
          contentDescription = "Success",
          tint = Color(0xFF4CAF50),
          modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Video Saved Successfully!",
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF4CAF50)
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Media3 Video Previewer
      // IMPORTANT: PlayerView uses SurfaceView by default, which renders on a separate
      // hardware layer and cannot be clipped by Compose. We attach a TextureView instead
      // so that rounded corners and Compose modifiers work correctly.
      AndroidView(
        factory = { ctx ->
          android.view.TextureView(ctx).also { textureView ->
            exoPlayer.setVideoTextureView(textureView)
          }
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(280.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(Color.Black)
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Details Card
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(12.dp)
          )
          .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("📂", fontSize = 20.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Saved File Location",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
          )
          Text(
            text = fileName,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "Movies/VideoCutter/$fileName",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
          )
        }
      }
    }

    // Bottom Action Buttons
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Button(
        onClick = onBack,
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary
        )
      ) {
        Icon(Icons.Default.Refresh, contentDescription = "Retry")
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Crop Another Option",
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold
        )
      }

      OutlinedButton(
        onClick = onReset,
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp),
        shape = RoundedCornerShape(12.dp)
      ) {
        Text(
          text = "Choose New Video",
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}
