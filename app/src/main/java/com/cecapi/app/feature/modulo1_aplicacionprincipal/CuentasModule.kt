package com.cecapi.app.feature.modulo1_aplicacionprincipal

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Qué implementación de [ServicioDeCuentas] usa la app: hoy la local; aquí se cambia por la del servidor. */
@Module
@InstallIn(SingletonComponent::class)
abstract class CuentasModule {
    @Binds
    @Singleton
    abstract fun cuentas(local: CuentasLocales): ServicioDeCuentas
}
