package com.cecapi.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.cecapi.app.MainActivity
import com.cecapi.app.R
import com.cecapi.app.core.navigation.CecapiDestinations

/**
 * Widgets de un solo botón grande, para quien solo quiere una cosa a un toque de la pantalla de inicio: hablar,
 * ir a Actividades o abrir la cámara. Cada uno es una subclase que dice qué texto, qué ícono y qué hace.
 */
abstract class AccesoWidgetProvider(
    @StringRes private val etiqueta: Int,
    @DrawableRes private val icono: Int,
    @StringRes private val descripcion: Int,
    private val requestCode: Int,
) : AppWidgetProvider() {

    /** Lo que se abre al tocar el botón. */
    protected abstract fun intencion(context: Context): Intent

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> manager.updateAppWidget(id, vistas(context)) }
    }

    private fun vistas(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_single)
        views.setTextViewText(R.id.widget_single_label, context.getString(etiqueta))
        views.setImageViewResource(R.id.widget_single_icon, icono)
        views.setContentDescription(R.id.widget_single_root, context.getString(descripcion))
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        views.setOnClickPendingIntent(R.id.widget_single_root, PendingIntent.getActivity(context, requestCode, intencion(context), flags))
        return views
    }

    protected fun abrir(context: Context, accion: String): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(accion)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}

/** Un solo botón: abre el asistente ya escuchando. */
class MicrofonoWidgetProvider : AccesoWidgetProvider(
    R.string.widget_talk, R.drawable.ic_widget_mic, R.string.widget_talk_description, requestCode = 10,
) {
    override fun intencion(context: Context): Intent = abrir(context, MainActivity.ACTION_LISTEN)
}

/** Un solo botón: abre Actividades. Sin sesión, la app avisa que primero hay que entrar. */
class ActividadesWidgetProvider : AccesoWidgetProvider(
    R.string.widget_activities_label, R.drawable.ic_widget_activities, R.string.widget_activities_button_description, requestCode = 11,
) {
    override fun intencion(context: Context): Intent =
        abrir(context, MainActivity.ACTION_OPEN).putExtra(MainActivity.EXTRA_ROUTE, CecapiDestinations.LEARNING)
}

/** Un solo botón: abre la cámara. */
class CamaraWidgetProvider : AccesoWidgetProvider(
    R.string.widget_camera_label, R.drawable.ic_widget_camera, R.string.widget_camera_button_description, requestCode = 12,
) {
    override fun intencion(context: Context): Intent =
        abrir(context, MainActivity.ACTION_OPEN).putExtra(MainActivity.EXTRA_ROUTE, CecapiDestinations.DOCUMENT_READER)
}
