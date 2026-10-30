package com.cecapi.app.core.navigation

import androidx.lifecycle.ViewModel
import com.cecapi.app.core.voice.VoiceEngine
import com.cecapi.app.feature.modulo1_aplicacionprincipal.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Lleva la voz al cambiar de pantalla: lo que se decía antes se corta, y al regresar se dice a dónde. */
@HiltViewModel
class NavVoiceViewModel @Inject constructor(
    val voiceEngine: VoiceEngine,
    val sessionRepository: SessionRepository,
) : ViewModel()
