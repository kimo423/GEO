package com.geo.ledger.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.geo.ledger.R
import com.geo.ledger.domain.EditorNavigationGuard
import com.geo.ledger.domain.NavigationLockPolicy
import com.geo.ledger.ui.addtransaction.AddTransactionScreen
import com.geo.ledger.ui.addtransaction.AddTransactionViewModel
import com.geo.ledger.ui.bills.BillsScreen
import com.geo.ledger.ui.category.ManageCategoriesScreen
import com.geo.ledger.ui.home.HomeScreen
import com.geo.ledger.ui.person.ManagePersonsScreen
import com.geo.ledger.ui.settings.SettingsScreen
import com.geo.ledger.ui.transactiondetail.TransactionDetailScreen
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geo.ledger.GeoApplication
import com.geo.ledger.ui.attachments.LocalAttachmentCounts

object GeoDestinations {
    const val Home = "home"
    const val Bills = "bills"
    const val Settings = "settings"
    const val Editor = "editor?transactionId={transactionId}"
    const val TransactionId = "transactionId"
    const val Detail = "detail/{id}"
    const val DetailId = "id"
    const val Persons = "persons"
    const val Categories = "categories"
    const val History = "history"

    fun editor(transactionId: Long = -1L): String = "editor?transactionId=$transactionId"

    fun detail(id: Long): String = "detail/$id"
}

private val TopLevelRoutes = setOf(
    GeoDestinations.Home,
    GeoDestinations.Bills,
    GeoDestinations.Settings,
)

private data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val TopLevelDestinations = listOf(
    TopLevelDestination(
        route = GeoDestinations.Home,
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    ),
    TopLevelDestination(
        route = GeoDestinations.Bills,
        labelRes = R.string.nav_bills,
        selectedIcon = Icons.AutoMirrored.Filled.ReceiptLong,
        unselectedIcon = Icons.AutoMirrored.Outlined.ReceiptLong,
    ),
    TopLevelDestination(
        route = GeoDestinations.Settings,
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoApp(
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val repository=(LocalContext.current.applicationContext as GeoApplication).repository
    val counts by repository.attachmentCounts.collectAsStateWithLifecycle(emptyList())
    val countMap=remember(counts) { counts.associate { it.transactionUuid to it.count } }
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in TopLevelRoutes
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    var navigationLocked by remember { mutableStateOf(false) }
    var feedbackToken by remember { mutableIntStateOf(0) }
    var pendingFeedbackRes by remember { mutableStateOf<Int?>(null) }
    val onUserBack: () -> Unit = {
        if (NavigationLockPolicy.allowUserBack(navigationLocked)) {
            navController.popBackStack()
        }
    }
    val onOperationCompleted: (Int) -> Unit = { messageRes ->
        navController.popBackStack()
        feedbackToken += 1
        pendingFeedbackRes = messageRes
    }
    BackHandler(enabled = navigationLocked) { }
    LaunchedEffect(feedbackToken) {
        val messageRes = pendingFeedbackRes ?: return@LaunchedEffect
        if (feedbackToken == 0) return@LaunchedEffect
        snackbarHostState.showSnackbar(resources.getString(messageRes))
    }

    CompositionLocalProvider(LocalAttachmentCounts provides countMap) {
    Box {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!showBottomBar) {
                TopAppBar(
                    title = {
                        val editorId = backStackEntry?.arguments?.getLong(GeoDestinations.TransactionId) ?: -1L
                        Text(secondaryTitle(currentRoute, editorId))
                    },
                    navigationIcon = {
                        IconButton(onClick = onUserBack, enabled = !navigationLocked) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                GeoBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { route -> if(!navigationLocked) navController.navigateTopLevel(route) },
                )
            }
        },
    ) { innerPadding ->
        GeoNavHost(
            navController = navController,
            onUserBack = onUserBack,
            onOperationCompleted = onOperationCompleted,
            onNavigationLock = { navigationLocked = it },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
    }
    }
}

@Composable
private fun secondaryTitle(route: String?, transactionId: Long): String = when {
    isEditorRoute(route) -> stringResource(
        if (transactionId > 0) R.string.edit_transaction else R.string.add_transaction,
    )
    route == GeoDestinations.Persons -> stringResource(R.string.manage_persons)
    route == GeoDestinations.Categories -> stringResource(R.string.manage_categories)
    route == GeoDestinations.Detail -> stringResource(R.string.transaction_detail)
    route == GeoDestinations.History -> "修改与删除记录"
    else -> stringResource(R.string.app_name)
}

internal fun isEditorRoute(route: String?): Boolean =
    route == GeoDestinations.Editor || route?.startsWith("editor") == true

@Composable
private fun GeoBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        TopLevelDestinations.forEach { destination ->
            val selected = currentRoute == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(destination.route) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = stringResource(destination.labelRes),
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}

@Composable
private fun GeoNavHost(
    navController: NavHostController,
    onUserBack: () -> Unit,
    onOperationCompleted: (Int) -> Unit,
    onNavigationLock: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var lastEditorNavMs by remember { mutableLongStateOf(0L) }
    var lastEditorTargetId by remember { mutableLongStateOf(Long.MIN_VALUE) }

    fun navigateEditor(transactionId: Long) {
        val now = android.os.SystemClock.elapsedRealtime()
        val currentEntry = navController.currentBackStackEntry
        val currentIsSame = isEditorRoute(currentEntry?.destination?.route) &&
            (currentEntry?.arguments?.getLong(GeoDestinations.TransactionId) ?: -1L) == transactionId
        val alreadyOnStack = try {
            navController.getBackStackEntry(GeoDestinations.editor(transactionId))
            true
        } catch (_: IllegalArgumentException) {
            false
        }
        if (!EditorNavigationGuard.shouldNavigate(
                nowMs = now,
                lastNavMs = lastEditorNavMs,
                lastTargetId = lastEditorTargetId,
                targetId = transactionId,
                currentIsSameEditor = currentIsSame,
                editorAlreadyOnBackStackForTarget = alreadyOnStack,
            )
        ) {
            return
        }
        lastEditorNavMs = now
        lastEditorTargetId = transactionId
        navController.navigate(GeoDestinations.editor(transactionId)) {
            launchSingleTop = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = GeoDestinations.Home,
        modifier = modifier.clipToBounds(),
        enterTransition = GeoNavTransitions.enterTransition(),
        exitTransition = GeoNavTransitions.exitTransition(),
        popEnterTransition = GeoNavTransitions.popEnterTransition(),
        popExitTransition = GeoNavTransitions.popExitTransition(),
        sizeTransform = GeoNavTransitions.sizeTransform(),
    ) {
        composable(GeoDestinations.Home) {
            HomeScreen(
                onAddTransaction = { navigateEditor(AddTransactionViewModel.NEW_TRANSACTION_ID) },
                onOpenDetail = { id -> navController.navigate(GeoDestinations.detail(id)) },
                onViewAll = { navController.navigateTopLevel(GeoDestinations.Bills) },
            )
        }
        composable(GeoDestinations.Bills) {
            BillsScreen(
                onOpenDetail = { id -> navController.navigate(GeoDestinations.detail(id)) },
                onOpenHistory = { navController.navigate(GeoDestinations.History) },
            )
        }
        composable(GeoDestinations.Settings) {
            SettingsScreen(
                onOpenPersons = { navController.navigate(GeoDestinations.Persons) },
                onOpenCategories = { navController.navigate(GeoDestinations.Categories) },
                onOpenHistory = { navController.navigate(GeoDestinations.History) },
                onNavigationLock = onNavigationLock,
            )
        }
        composable(
            route = GeoDestinations.Editor,
            arguments = listOf(
                navArgument(GeoDestinations.TransactionId) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) {
            AddTransactionScreen(
                onSaved = onOperationCompleted,
                onBack = onUserBack,
                onNavigationLock = onNavigationLock,
            )
        }
        composable(
            route = GeoDestinations.Detail,
            arguments = listOf(
                navArgument(GeoDestinations.DetailId) {
                    type = NavType.LongType
                    defaultValue = 0L
                },
            ),
        ) {
            TransactionDetailScreen(
                onEdit = { id -> navigateEditor(id) },
                onDeleted = onOperationCompleted,
                onNavigationLock = onNavigationLock,
            )
        }
        composable(GeoDestinations.Persons) {
            ManagePersonsScreen()
        }
        composable(GeoDestinations.Categories) {
            ManageCategoriesScreen()
        }
        composable(GeoDestinations.History) { com.geo.ledger.ui.history.AuditScreen() }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
