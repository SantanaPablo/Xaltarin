package com.perromono.saltarin

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import de.blinkt.openvpn.core.GlobalPreferences

class SaltarinApp : Application() {
    override fun onCreate() {
        super.onCreate()
        GlobalPreferences.setInstance(false, false, false)
        crearCanalesNotificacion()
    }
gh auth login
    private fun crearCanalesNotificacion() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val canales = listOf(
            NotificationChannel(
                "openvpn_bg",
                "VPN Background",
                NotificationManager.IMPORTANCE_LOW
            ),
            NotificationChannel(
                "openvpn_newstat",
                "VPN Status",
                NotificationManager.IMPORTANCE_LOW
            ),
            NotificationChannel(
                "openvpn_userreq",
                "VPN User Requests",
                NotificationManager.IMPORTANCE_HIGH
            )
        )

        canales.forEach {
            nm.createNotificationChannel(it)
        }
    }
}