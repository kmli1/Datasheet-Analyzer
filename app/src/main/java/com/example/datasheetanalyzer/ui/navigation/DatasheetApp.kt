package com.example.datasheetanalyzer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.datasheetanalyzer.domain.extraction.QueryNormalizer
import com.example.datasheetanalyzer.ui.search.ResultsScreen
import com.example.datasheetanalyzer.ui.library.LocalLibraryScreen
import com.example.datasheetanalyzer.ui.library.PrivacyScreen
import com.example.datasheetanalyzer.ui.home.HomeScreen
import com.example.datasheetanalyzer.ui.home.HomeViewModel
import com.example.datasheetanalyzer.ui.scanner.ScannerScreen
import com.example.datasheetanalyzer.ui.scanner.ScannerViewModel
import com.example.datasheetanalyzer.ui.search.SearchScreen
import com.example.datasheetanalyzer.ui.search.SearchViewModel
import com.example.datasheetanalyzer.data.repository.ResolverDependencies
import com.example.datasheetanalyzer.ui.workspace.*
import com.example.datasheetanalyzer.features.sensoralternatives.SensorAlternativesScreen

private object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val SCANNER = "scanner"
    const val RESULTS = "results"
    const val LIBRARY = "library"
    const val PRIVACY = "privacy"
    const val PROJECTS = "projects"
    const val COMPARE = "compare"
    const val SENSORS = "sensors"
}

@Composable
fun DatasheetApp(homeViewModel: HomeViewModel = viewModel()) {
    // Created above NavHost: one Activity-scoped ViewModel for this form.
    val navController = rememberNavController()
    val state by homeViewModel.uiState.collectAsStateWithLifecycle()
    val context=LocalContext.current.applicationContext
    val local=remember(context) { ResolverDependencies.local(context) }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                state = state,
                onPartNumberChanged = homeViewModel::onPartNumberChanged,
                onCustomCriterionChanged = homeViewModel::onCustomCriterionChanged,
                onGraphTitlesChanged = homeViewModel::onGraphTitlesChanged,
                workspace=local.workspace,
                onApplyTemplate=homeViewModel::applyTemplate,
                onProjects={ navController.navigate(Routes.PROJECTS) { launchSingleTop=true } },
                onCompare={ navController.navigate(Routes.COMPARE) { launchSingleTop=true } },
                onSensors={ navController.navigate(Routes.SENSORS) { launchSingleTop=true } },
                onCriterionToggled = homeViewModel::onCriterionToggled,
                onSearch = {
                    if (homeViewModel.prepareSearch()) {
                        val form=homeViewModel.uiState.value
                        local.recent.record(form.partNumber,form.selectedCriteria,form.customCriterion,form.graphTitles)
                        navController.navigate(Routes.SEARCH) { launchSingleTop = true }
                    }
                },
                onScan = { navController.navigate(Routes.SCANNER) { launchSingleTop = true } },
                onLibrary = { navController.navigate(Routes.LIBRARY) },
                onPrivacy = { navController.navigate(Routes.PRIVACY) }
            )
        }
        composable(Routes.SEARCH) {
            val context=LocalContext.current.applicationContext
            val searchViewModel: SearchViewModel = viewModel(
                factory = SearchViewModel.Factory(ResolverDependencies.repository,{ ResolverDependencies.pipeline(context) },local)
            )
            val criteria = (state.selectedCriteria.map { it.label } +
                listOf(state.customCriterion,state.graphTitles.takeIf { it.isNotBlank() }?.let { "Grafik: $it" }.orEmpty()).filter { it.isNotBlank() }).joinToString(" · ")
            val queries=remember(state.selectedCriteria,state.customCriterion,state.graphTitles) { QueryNormalizer().queries(state.selectedCriteria,state.customCriterion,state.graphTitles) }
            SearchScreen(state.partNumber, criteria, queries, searchViewModel,
                onBack = { navController.popBackStack() },onResult={ navController.navigate(Routes.RESULTS) { launchSingleTop=true } })
        }
        composable(Routes.RESULTS) { entry ->
            val sourceEntry=remember(entry) { navController.getBackStackEntry(Routes.SEARCH) }
            val searchViewModel: SearchViewModel=viewModel(sourceEntry,factory=SearchViewModel.Factory(ResolverDependencies.repository,{ ResolverDependencies.pipeline(context) },local))
            val queries=remember(state.selectedCriteria,state.customCriterion,state.graphTitles) { QueryNormalizer().queries(state.selectedCriteria,state.customCriterion,state.graphTitles) }
            ResultsScreen(state.partNumber,queries,searchViewModel,onBack={ navController.popBackStack() },
                onProjects={ navController.navigate(Routes.PROJECTS) { launchSingleTop=true } },
                onCompare={ navController.navigate(Routes.COMPARE) { launchSingleTop=true } },
                onSensors={ navController.navigate(Routes.SENSORS) { launchSingleTop=true } },
                onVariant={ code ->
                    homeViewModel.onPartNumberChanged(code)
                    if(homeViewModel.prepareSearch()) {
                        val form=homeViewModel.uiState.value
                        local.recent.record(form.partNumber,form.selectedCriteria,form.customCriterion,form.graphTitles)
                        navController.popBackStack(Routes.HOME,false)
                        navController.navigate(Routes.SEARCH) { launchSingleTop=true }
                    }
                })
        }
        composable(Routes.LIBRARY) {
            LocalLibraryScreen(local,onOpen={ record ->
                homeViewModel.restore(record)
                if(homeViewModel.prepareSearch()) {
                    navController.popBackStack()
                    navController.navigate(Routes.SEARCH) { launchSingleTop=true }
                }
            },onBack={ navController.popBackStack() },onPrivacy={ navController.navigate(Routes.PRIVACY) })
        }
        composable(Routes.PRIVACY) { PrivacyScreen(onBack={ navController.popBackStack() }) }
        composable(Routes.PROJECTS) {
            ProjectsScreen(local.workspace,onBack={ navController.popBackStack() },onOpen={ record ->
                homeViewModel.restoreAnalysis(record)
                if(homeViewModel.prepareSearch()) {
                    navController.popBackStack(Routes.HOME,false)
                    navController.navigate(Routes.SEARCH) { launchSingleTop=true }
                }
            },onCompare={ navController.navigate(Routes.COMPARE) { launchSingleTop=true } })
        }
        composable(Routes.COMPARE) {
            ComparisonScreen(local.workspace,onBack={ navController.popBackStack() },onNewAnalysis={ navController.popBackStack(Routes.HOME,false) })
        }
        composable(Routes.SENSORS) {
            SensorAlternativesScreen(state.partNumber,onBack={ navController.popBackStack() },onAnalyze={ code ->
                homeViewModel.onPartNumberChanged(code)
                navController.popBackStack(Routes.HOME,false)
            })
        }
        composable(Routes.SCANNER) {
            val scannerViewModel: ScannerViewModel = viewModel()
            ScannerScreen(
                viewModel = scannerViewModel,
                onConfirm = { code ->
                    homeViewModel.onPartNumberChanged(code)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
