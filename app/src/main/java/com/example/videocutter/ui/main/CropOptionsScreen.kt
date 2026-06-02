package com.example.videocutter.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropOptionsScreen(
  videoUri: String,
  onCropSelected: (Int) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  var selectedPercentage by remember { mutableStateOf(3) }
  val fileName = remember(videoUri) {
    try {
      val uri = android.net.Uri.parse(videoUri)
      uri.lastPathSegment?.substringAfterLast('/') ?: "video.mp4"
    } catch (e: Exception) {
      "video.mp4"
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Crop Options", fontWeight = FontWeight.Bold) },
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
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(16.dp),
      verticalArrangement = Arrangement.SpaceBetween,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Selected File Info Card
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
          )
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("🎥", fontSize = 24.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "Selected File",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
              )
              Text(
                text = fileName,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
          text = "Choose Watermark Crop Size",
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = "Select how much of the bottom part of the video should be cropped to remove the watermark.",
          fontSize = 13.sp,
          color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Option 3%
        CropOptionCard(
          percentage = 3,
          description = "Best for thin/small bottom watermarks",
          isSelected = selectedPercentage == 3,
          onClick = { selectedPercentage = 3 }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Option 5%
        CropOptionCard(
          percentage = 5,
          description = "Best for standard/larger bottom watermarks",
          isSelected = selectedPercentage == 5,
          onClick = { selectedPercentage = 5 }
        )
      }

      // Action Button
      Button(
        onClick = { onCropSelected(selectedPercentage) },
        modifier = Modifier
          .fillMaxWidth()
          .height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary
        )
      ) {
        Text(
          text = "Crop & Save Video",
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}

@Composable
fun CropOptionCard(
  percentage: Int,
  description: String,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  val border = if (isSelected) {
    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
  } else {
    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
  }

  val backgroundColor = if (isSelected) {
    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
  } else {
    MaterialTheme.colorScheme.surface
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(12.dp),
    border = border,
    colors = CardDefaults.cardColors(containerColor = backgroundColor)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "$percentage% Bottom Crop",
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = description,
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
      }
      RadioButton(
        selected = isSelected,
        onClick = onClick
      )
    }
  }
}
