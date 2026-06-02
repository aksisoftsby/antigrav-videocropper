package com.example.videocutter

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.videocutter.ui.main.ChooseVideoScreen
import com.example.videocutter.ui.main.CropOptionsScreen
import com.example.videocutter.ui.main.PreviewResultScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(ChooseVideo)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<ChooseVideo> {
          ChooseVideoScreen(
            onVideoSelected = { videoUri -> backStack.add(CropOptions(videoUri)) },
            modifier = Modifier.safeDrawingPadding().padding(16.dp)
          )
        }
        entry<CropOptions> { key ->
          CropOptionsScreen(
            videoUri = key.videoUri,
            onCropSelected = { cropPct -> backStack.add(PreviewResult(key.videoUri, cropPct)) },
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.safeDrawingPadding().padding(16.dp)
          )
        }
        entry<PreviewResult> { key ->
          PreviewResultScreen(
            videoUri = key.videoUri,
            cropPercentage = key.cropPercentage,
            onBack = { backStack.removeLastOrNull() },
            onReset = { backStack.removeUntil(ChooseVideo) },
            modifier = Modifier.safeDrawingPadding().padding(16.dp)
          )
        }
      },
  )
}
