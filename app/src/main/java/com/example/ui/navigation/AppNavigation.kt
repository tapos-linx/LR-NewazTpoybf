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
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.records.CapturedRecordsScreen
import com.example.ui.screens.saf.SafImportScreen
import com.example.ui.screens.saf.SafImportViewModel
import com.example.ui.screens.tesseract.TesseractSetupScreen
import com.example.ui.screens.tesseract.TesseractSetupViewModel

object Destinations {
    const val HOME = "home"
    const val CAPTURED_RECORDS = "captured_records"
    const val DETAIL = "detail/{documentId}"
    const val CAMERA = "camera"
    const val SAF_IMPORT = "saf_import"
    const val TESSERACT = "tesseract_setup"

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
        composable(Destinations.HOME) {
            val homeViewModel: HomeViewModel = viewModel()
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToDetail = { docId ->
                    navController.navigate(Destinations.detailRoute(docId))
                },
                onNavigateToCamera = {
                    navController.navigate(Destinations.CAMERA)
                },
                onNavigateToSafImport = {
                    navController.navigate(Destinations.SAF_IMPORT)
                },
                onNavigateToTesseract = {
                    navController.navigate(Destinations.TESSERACT)
                },
                onNavigateToCapturedRecords = {
                    navController.navigate(Destinations.CAPTURED_RECORDS)
                }
            )
        }

        composable(Destinations.CAPTURED_RECORDS) {
            val homeViewModel: HomeViewModel = viewModel()
            CapturedRecordsScreen(
                viewModel = homeViewModel,
                onNavigateToDetail = { docId ->
                    navController.navigate(Destinations.detailRoute(docId))
                },
                onNavigateToCamera = {
                    navController.navigate(Destinations.CAMERA)
                },
                onNavigateToSafImport = {
                    navController.navigate(Destinations.SAF_IMPORT)
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

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

        composable(Destinations.SAF_IMPORT) {
            val safViewModel: SafImportViewModel = viewModel()
            SafImportScreen(
                viewModel = safViewModel,
                onNavigateBack = { navController.popBackStack() },
                onProcessingFinished = {
                    navController.popBackStack()
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
