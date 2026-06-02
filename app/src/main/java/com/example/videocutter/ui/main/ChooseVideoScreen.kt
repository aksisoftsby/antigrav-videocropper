package com.example.videocutter.ui.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChooseVideoScreen(
  onVideoSelected: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val launcher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri ->
    uri?.let {
      onVideoSelected(it.toString())
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Logo / Title area
    Box(
      modifier = Modifier
        .size(96.dp)
        .background(
          brush = Brush.linearGradient(
            colors = listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))
          ),
          shape = RoundedCornerShape(24.dp)
        ),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = "✂️",
        fontSize = 40.sp
      )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
      text = "Video Cutter & Crop",
      fontSize = 28.sp,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = "Quickly crop the bottom watermark of your videos (3% or 5% crop)",
      fontSize = 15.sp,
      color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(horizontal = 24.dp)
    )

    Spacer(modifier = Modifier.height(48.dp))

    // Interactive upload card
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .height(180.dp)
        .clickable { launcher.launch("video/*") },
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
      )
    ) {
      Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = "📁",
          fontSize = 48.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = "Select Video from Gallery",
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Supports MP4, MKV, WebM, etc.",
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
      }
    }
  }
}
