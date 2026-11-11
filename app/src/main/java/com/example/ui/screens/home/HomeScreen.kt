package com.example.ui.screens.home

import androidx.compose.runtime.Composable
import com.example.ui.screens.main.MainScaffoldScreen

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToSafImport: () -> Unit = {},
    onNavigateToTesseract: () -> Unit = {},
    onNavigateToCapturedRecords: (() -> Unit)? = null,
    onNavigateToGallery: (() -> Unit)? = null,
    onNavigateToBulkUpload: (() -> Unit)? = null,
    onNavigateToWarishMatcher: (() -> Unit)? = null,
    onNavigateToHcr: (() -> Unit)? = null,
    onNavigateToAtlas: (() -> Unit)? = null,
    onNavigateToGeminiOcr: (() -> Unit)? = null,
    onNavigateToTaskManager: (() -> Unit)? = null,
    onNavigateToGoogleDrive: (() -> Unit)? = null,
    onNavigateToExtractionQueue: (() -> Unit)? = null
) {
    MainScaffoldScreen(
        homeViewModel = viewModel,
        onNavigateToDetail = onNavigateToDetail,
        onNavigateToCamera = onNavigateToCamera,
        onNavigateToTesseract = onNavigateToTesseract
    )
}
