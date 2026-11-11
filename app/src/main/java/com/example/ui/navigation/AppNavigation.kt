package com.example.ui.navigation

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.camera.CameraCaptureScreen
import com.example.ui.screens.camera.CameraCaptureViewModel
import com.example.ui.screens.detail.DocumentDetailScreen
import com.example.ui.screens.detail.DocumentDetailViewModel
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.main.MainScaffoldScreen
import com.example.ui.screens.tesseract.TesseractSetupScreen
import com.example.ui.screens.tesseract.TesseractSetupViewModel

object Destinations {
    const val HOME = "home"
    const val DETAIL = "detail/{documentId}"
    const val CAMERA = "camera"
    const val TESSERACT = "tesseract_setup"

    // Merged routes (for backward compatibility in tests and legacy callers)
    const val CAPTURED_RECORDS = "captured_records"
    const val GALLERY = "gallery"
    const val BULK_UPLOAD = "bulk_upload"
    const val WARISH_MATCHER = "warish_matcher"
    const val HCR = "hcr_correction"
    const val ATLAS = "atlas_timeline"
    const val SAF_IMPORT = "saf_import"
    const val GEMINI_OCR = "gemini_ocr"
    const val GOOGLE_DRIVE_INJECT = "google_drive_inject"
    const val EXTRACTION_QUEUE = "extraction_queue"

    fun detailRoute(documentId: String) = "detail/$documentId"
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application

    NavHost(
        navController = navController,
        startDestination = Destinations.HOME
    ) {
        // Tab 0: Home (Main 5-tab scaffold)
        composable(Destinations.HOME) {
            val homeViewModel: HomeViewModel = viewModel()
            MainScaffoldScreen(
                homeViewModel = homeViewModel,
                onNavigateToDetail = { docId ->
                    navController.navigate(Destinations.detailRoute(docId))
                },
                onNavigateToCamera = {
                    navController.navigate(Destinations.CAMERA)
                },
                onNavigateToTesseract = {
                    navController.navigate(Destinations.TESSERACT)
                },
                initialTab = 0
            )
        }

        // Merged Route: Import / Queue / Bulk Upload / Google Drive (Opens Tab 1: Import)
        composable(Destinations.EXTRACTION_QUEUE) {
            val homeViewModel: HomeViewModel = viewModel()
            MainScaffoldScreen(
                homeViewModel = homeViewModel,
                onNavigateToDetail = { docId -> navController.navigate(Destinations.detailRoute(docId)) },
                onNavigateToCamera = { navController.navigate(Destinations.CAMERA) },
                onNavigateToTesseract = { navController.navigate(Destinations.TESSERACT) },
                initialTab = 1
            )
        }

        composable(Destinations.SAF_IMPORT) {
            val homeViewModel: HomeViewModel = viewModel()
            MainScaffoldScreen(
                homeViewModel = homeViewModel,
                onNavigateToDetail = { docId -> navController.navigate(Destinations.detailRoute(docId)) },
                onNavigateToCamera = { navController.navigate(Destinations.CAMERA) },
                onNavigateToTesseract = { navController.navigate(Destinations.TESSERACT) },
                initialTab = 1
            )
        }

        composable(Destinations.BULK_UPLOAD) {
            val homeViewModel: HomeViewModel = viewModel()
            MainScaffoldScreen(
                homeViewModel = homeViewModel,
                onNavigateToDetail = { docId -> navController.navigate(Destinations.detailRoute(docId)) },
                onNavigateToCamera = { navController.navigate(Destinations.CAMERA) },
                onNavigateToTesseract = { navController.navigate(Destinations.TESSERACT) },
                initialTab = 1
            )
        }

        composable(Destinations.GOOGLE_DRIVE_INJECT) {
            val homeViewModel: HomeViewModel = viewModel()
            MainScaffoldScreen(
                homeViewModel = homeViewModel,
                onNavigateToDetail = { docId -> navController.navigate(Destinations.detailRoute(docId)) },
                onNavigateToCamera = { navController.navigate(Destinations.CAMERA) },
                onNavigateToTesseract = { navController.navigate(Destinations.TESSERACT) },
                initialTab = 1
            )
        }

        // Merged Route: Records / Gallery (Opens Tab 2: Records)
        composable(Destinations.CAPTURED_RECORDS) {
            val homeViewModel: HomeViewModel = viewModel()
            MainScaffoldScreen(
                homeViewModel = homeViewModel,
                onNavigateToDetail = { docId -> navController.navigate(Destinations.detailRoute(docId)) },
                onNavigateToCamera = { navController.navigate(Destinations.CAMERA) },
                onNavigateToTesseract = { navController.navigate(Destinations.TESSERACT) },
                initialTab = 2
            )
        }

        composable(Destinations.GALLERY) {
            val homeViewModel: HomeViewModel = viewModel()
            MainScaffoldScreen(
                homeViewModel = homeViewModel,
                onNavigateToDetail = { docId -> navController.navigate(Destinations.detailRoute(docId)) },
                onNavigateToCamera = { navController.navigate(Destinations.CAMERA) },
                onNavigateToTesseract = { navController.navigate(Destinations.TESSERACT) },
                initialTab = 2
            )
        }

        // Merged Route: Analysis (Warish Matcher & Atlas) (Opens Tab 3: Analysis)
        composable(Destinations.WARISH_MATCHER) {
            val homeViewModel: HomeViewModel = viewModel()
            MainScaffoldScreen(
                homeViewModel = homeViewModel,
                onNavigateToDetail = { docId -> navController.navigate(Destinations.detailRoute(docId)) },
                onNavigateToCamera = { navController.navigate(Destinations.CAMERA) },
                onNavigateToTesseract = { navController.navigate(Destinations.TESSERACT) },
                initialTab = 3
            )
        }

        composable(Destinations.ATLAS) {
            val homeViewModel: HomeViewModel = viewModel()
            MainScaffoldScreen(
                homeViewModel = homeViewModel,
                onNavigateToDetail = { docId -> navController.navigate(Destinations.detailRoute(docId)) },
                onNavigateToCamera = { navController.navigate(Destinations.CAMERA) },
                onNavigateToTesseract = { navController.navigate(Destinations.TESSERACT) },
                initialTab = 3
            )
        }

        composable(Destinations.GEMINI_OCR) {
            val homeViewModel: HomeViewModel = viewModel()
            MainScaffoldScreen(
                homeViewModel = homeViewModel,
                onNavigateToDetail = { docId -> navController.navigate(Destinations.detailRoute(docId)) },
                onNavigateToCamera = { navController.navigate(Destinations.CAMERA) },
                onNavigateToTesseract = { navController.navigate(Destinations.TESSERACT) },
                initialTab = 1
            )
        }

        composable(Destinations.HCR) {
            val homeViewModel: HomeViewModel = viewModel()
            MainScaffoldScreen(
                homeViewModel = homeViewModel,
                onNavigateToDetail = { docId -> navController.navigate(Destinations.detailRoute(docId)) },
                onNavigateToCamera = { navController.navigate(Destinations.CAMERA) },
                onNavigateToTesseract = { navController.navigate(Destinations.TESSERACT) },
                initialTab = 3
            )
        }

        // Core Sub-Screens
        composable(
            route = Destinations.DETAIL,
            arguments = listOf(navArgument("documentId") { type = NavType.StringType })
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getString("documentId") ?: ""
            val detailViewModel: DocumentDetailViewModel = viewModel(
                key = "detail_$documentId",
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return DocumentDetailViewModel(app, documentId) as T
                    }
                }
            )

            DocumentDetailScreen(
                viewModel = detailViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Destinations.CAMERA) {
            val cameraViewModel: CameraCaptureViewModel = viewModel()
            CameraCaptureScreen(
                viewModel = cameraViewModel,
                onNavigateBack = { navController.popBackStack() },
                onCaptureComplete = { docId ->
                    navController.popBackStack()
                    navController.navigate(Destinations.detailRoute(docId))
                }
            )
        }

        composable(Destinations.TESSERACT) {
            val tesseractViewModel: TesseractSetupViewModel = viewModel()
            TesseractSetupScreen(
                viewModel = tesseractViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
