package com.example.videocutter

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object ChooseVideo : NavKey
@Serializable data class CropOptions(val videoUri: String) : NavKey
@Serializable data class PreviewResult(val videoUri: String, val cropPercentage: Int) : NavKey
