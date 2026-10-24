package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.data.repository.MileageRepository
import com.example.data.service.DriveTrackingService
import com.example.ui.auth.WelcomeScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

enum class AppScreen {
    WELCOME,
    DASHBOARD,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private lateinit var repository: MileageRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = MileageRepository(applicationContext)

        setContent {
            val userProfile by repository.userProfile.collectAsState()
            MyApplicationTheme(
                themeMode = userProfile.themeMode,
                themePreset = userProfile.themePreset,
                fontScale = userProfile.fontScale
            ) {
                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppNavHost(repository = repository)
                }
            }
        }
    }
}

@Composable
fun MainAppNavHost(repository: MileageRepository) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val userProfile by repository.userProfile.collectAsState()
    val trips by repository.allTrips.collectAsState(initial = emptyList())
    val vehicles by repository.allVehicles.collectAsState(initial = emptyList())

    val hasSeenWelcome = remember { repository.preferencesManager.hasSeenWelcome() }
    var currentScreen by remember {
        mutableStateOf(
            if (userProfile.isSignedInWithGoogle || hasSeenWelcome) {
                AppScreen.DASHBOARD
            } else {
                AppScreen.WELCOME
            }
        )
    }

    var isSigningIn by remember { mutableStateOf(false) }

    // Seed sample data on first start and initiate background tracking if enabled
    LaunchedEffect(Unit) {
        repository.seedSampleDataIfEmpty()
    }

    LaunchedEffect(userProfile.autoDriveDetectionEnabled) {
        if (userProfile.autoDriveDetectionEnabled) {
            DriveTrackingService.startService(context, manual = false)
        }
    }

    fun handleSignInWithGoogle() {
        isSigningIn = true
        coroutineScope.launch {
            val result = repository.authManager.signInWithGoogle()
            isSigningIn = false
            repository.preferencesManager.markWelcomeSeen()
            currentScreen = AppScreen.DASHBOARD
            val email = result.getOrNull() ?: "Google Account"
            snackbarHostState.showSnackbar("Signed in as $email. Google Drive backup enabled!")
        }
    }

    fun handleSignOut() {
        coroutineScope.launch {
            repository.authManager.signOut()
            currentScreen = AppScreen.WELCOME
            snackbarHostState.showSnackbar("Signed out. Switched to Guest mode.")
        }
    }

    when (currentScreen) {
        AppScreen.WELCOME -> {
            WelcomeScreen(
                onSignInWithGoogle = { handleSignInWithGoogle() },
                onContinueAsGuest = {
                    repository.authManager.continueAsGuest()
                    repository.preferencesManager.markWelcomeSeen()
                    currentScreen = AppScreen.DASHBOARD
                },
                isLoading = isSigningIn,
                repository = repository
            )
        }

        AppScreen.DASHBOARD -> {
            DashboardScreen(
                trips = trips,
                vehicles = vehicles,
                userProfile = userProfile,
                repository = repository,
                onNavigateToSettings = { currentScreen = AppScreen.SETTINGS },
                onNavigateToWelcome = { currentScreen = AppScreen.WELCOME },
                onSignInWithGoogle = { handleSignInWithGoogle() },
                onSignOut = { handleSignOut() },
                snackbarHostState = snackbarHostState
            )
        }

        AppScreen.SETTINGS -> {
            BackHandler {
                currentScreen = AppScreen.DASHBOARD
            }
            SettingsScreen(
                userProfile = userProfile,
                vehicles = vehicles,
                repository = repository,
                onBack = { currentScreen = AppScreen.DASHBOARD },
                onSignInWithGoogle = { handleSignInWithGoogle() },
                onSignOut = { handleSignOut() },
                onShowMessage = { msg ->
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(msg)
                    }
                }
            )
        }
    }
}
