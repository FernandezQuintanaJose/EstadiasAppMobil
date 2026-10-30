package com.cecapi.app.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cecapi.app.core.ui.WakeScope
import com.cecapi.app.feature.modulo1_aplicacionprincipal.DashboardScreen
import com.cecapi.app.feature.modulo1_aplicacionprincipal.EditAccountScreen
import com.cecapi.app.feature.modulo1_aplicacionprincipal.HomeScreen
import com.cecapi.app.feature.modulo1_aplicacionprincipal.LoginScreen
import com.cecapi.app.feature.modulo1_aplicacionprincipal.ChatsScreen
import com.cecapi.app.feature.modulo1_aplicacionprincipal.GestionScreen
import com.cecapi.app.feature.modulo1_aplicacionprincipal.PersonalizationScreen
import com.cecapi.app.feature.modulo1_aplicacionprincipal.RankingScreen
import com.cecapi.app.feature.modulo1_aplicacionprincipal.RegisterScreen
import com.cecapi.app.feature.modulo1_aplicacionprincipal.SoporteScreen
import com.cecapi.app.feature.modulo1_aplicacionprincipal.SettingsScreen
import com.cecapi.app.feature.modulo2_asistentevoz.VoiceAssistantScreen
import com.cecapi.app.feature.modulo3_asistenteinteligente.AiAssistantScreen
import com.cecapi.app.feature.modulo4_lectordocumentos.DocumentReaderScreen
import com.cecapi.app.feature.modulo5_solicitudes.ConsultasScreen
import com.cecapi.app.feature.modulo6_aprendizaje.LearningScreen
import com.cecapi.app.feature.modulo7_entorno.EnvironmentScreen

@Composable
fun CecapiNavGraph(navController: NavHostController = rememberNavController()) {
    val navVoice: NavVoiceViewModel = hiltViewModel()
    // Una puerta para todo: si algo (un toque o un comando de voz desde cualquier pantalla) lleva a una pantalla
    // que necesita cuenta y no hay sesión, se regresa y se dice por qué.
    val usuario by navVoice.sessionRepository.currentUser.collectAsState()
    val rutaActual = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(rutaActual, usuario) {
        if (rutaActual in CecapiDestinations.REQUIEREN_SESION && usuario == null) {
            navController.popBackStack()
            navVoice.voiceEngine.speak("Esto necesita una cuenta. Di iniciar sesión, o crear cuenta.")
        }
    }
    DisposableEffect(navController) {
        navVoice.voiceEngine.activeRoute = navController.currentDestination?.route
        var profundidad = navController.currentBackStack.value.size
        val listener = NavController.OnDestinationChangedListener { controller, destination, _ ->
            navVoice.voiceEngine.activeRoute = destination.route
            val nueva = controller.currentBackStack.value.size
            navVoice.voiceEngine.stopSpeaking()
            if (nueva < profundidad) {
                val menu = destination.route == CecapiDestinations.DASHBOARD
                navVoice.voiceEngine.speak(if (menu) "Volviendo al menú." else "Volviendo.")
            }
            profundidad = nueva
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
    NavHost(navController = navController, startDestination = CecapiDestinations.HOME) {
        composable(CecapiDestinations.HOME) {
            HomeScreen(
                onNavigateToLogin = { navController.navigate(CecapiDestinations.LOGIN) },
                onNavigateToRegister = { navController.navigate(CecapiDestinations.REGISTER) },
                onNavigateToModule = { route -> navController.navigate(route) },
                onNavigateToSettings = { navController.navigate(CecapiDestinations.SETTINGS) },
                onSessionActive = {
                    navController.navigate(CecapiDestinations.DASHBOARD) {
                        popUpTo(CecapiDestinations.HOME) { inclusive = true }
                    }
                },
            )
        }
        composable(CecapiDestinations.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(CecapiDestinations.DASHBOARD) {
                        popUpTo(CecapiDestinations.HOME) { inclusive = true }
                    }
                },
                onNavigateToRegister = { navController.navigate(CecapiDestinations.REGISTER) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(CecapiDestinations.REGISTER) {
            WakeScope {
                RegisterScreen(
                    onRegisterSuccess = {
                        navController.navigate(CecapiDestinations.DASHBOARD) {
                            popUpTo(CecapiDestinations.HOME) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(CecapiDestinations.DASHBOARD) {
            DashboardScreen(
                onModuleClick = { route -> navController.navigate(route) },
                onLoggedOut = {
                    navController.navigate(CecapiDestinations.HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
        composable(CecapiDestinations.SETTINGS) {
            WakeScope {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onLoggedOut = {
                        navController.navigate(CecapiDestinations.HOME) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onOpenAccount = { navController.navigate(CecapiDestinations.EDIT_ACCOUNT) },
                )
            }
        }
        composable(CecapiDestinations.PERSONALIZATION) {
            WakeScope { PersonalizationScreen(onBack = { navController.popBackStack() }) }
        }
        composable(CecapiDestinations.GESTION) {
            WakeScope { GestionScreen(onBack = { navController.popBackStack() }) }
        }
        composable(CecapiDestinations.SOPORTE) {
            WakeScope { SoporteScreen(onBack = { navController.popBackStack() }) }
        }
        composable(CecapiDestinations.RANKING) {
            WakeScope { RankingScreen(onBack = { navController.popBackStack() }) }
        }
        composable(CecapiDestinations.EDIT_ACCOUNT) {
            WakeScope {
                EditAccountScreen(
                    onBack = { navController.popBackStack() },
                    onLoggedOut = {
                        navController.navigate(CecapiDestinations.HOME) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }
        }
        composable(CecapiDestinations.CHATS) {
            WakeScope {
                ChatsScreen(
                    onBack = { navController.popBackStack() },
                    onOpen = { route -> navController.navigate(route) },
                )
            }
        }
        composable(CecapiDestinations.VOICE_ASSISTANT) {
            WakeScope {
                VoiceAssistantScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToModule = { route -> navController.navigate(route) },
                )
            }
        }
        composable(CecapiDestinations.AI_ASSISTANT) {
            WakeScope { AiAssistantScreen(onBack = { navController.popBackStack() }) }
        }
        composable(CecapiDestinations.DOCUMENT_READER) {
            WakeScope {
                DocumentReaderScreen(
                    onBack = { navController.popBackStack() },
                    onOpen = { route ->
                        navController.popBackStack()
                        navController.navigate(route)
                    },
                )
            }
        }
        composable(CecapiDestinations.REQUESTS) {
            WakeScope {
                ConsultasScreen(
                    onBack = { navController.popBackStack() },
                    onOpenChats = { navController.navigate(CecapiDestinations.CHATS) },
                )
            }
        }
        composable(CecapiDestinations.LEARNING) {
            WakeScope { LearningScreen(onBack = { navController.popBackStack() }) }
        }
        composable(CecapiDestinations.ENVIRONMENT) {
            WakeScope {
                EnvironmentScreen(
                    onBack = { navController.popBackStack() },
                    onOpen = { route ->
                        navController.popBackStack()
                        navController.navigate(route)
                    },
                )
            }
        }
    }
}
