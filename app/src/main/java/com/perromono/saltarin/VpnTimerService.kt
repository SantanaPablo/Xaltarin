package com.perromono.xaltarin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import de.blinkt.openvpn.core.IOpenVPNServiceInternal
import de.blinkt.openvpn.core.OpenVPNService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class VpnTimerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val CHANNEL_ID = "saltarin_timer"
    private val NOTIF_ID = 42

    private var vpnService: IOpenVPNServiceInternal? = null

    private val vpnConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            vpnService = IOpenVPNServiceInternal.Stub.asInterface(service)
            try {
                vpnService?.stopVPN(false)
                android.util.Log.d("SALTARIN_VPN", "VPN detenida correctamente")
            } catch (e: Exception) {
                android.util.Log.e("SALTARIN_VPN", "Error deteniendo VPN", e)
            }
            try { unbindService(this) } catch (_: Exception) {}
            stopSelf()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            vpnService = null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val seconds = intent?.getLongExtra("TIMER_SECONDS", 0L) ?: 0L

        crearCanalNotificacion()

        startForeground(
            NOTIF_ID,
            crearNotificacion("VPN activa - desconectando en ${seconds}s")
        )

        if (seconds > 0) {
            iniciarConteo(seconds)
        } else {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun iniciarConteo(seconds: Long) {
        serviceScope.launch {
            delay(seconds * 1000)
            android.util.Log.d("SALTARIN_VPN", "Tiempo cumplido, desconectando VPN")

            try {
                val intent = Intent(this@VpnTimerService, OpenVPNService::class.java)
                intent.action = OpenVPNService.START_SERVICE
                bindService(intent, vpnConnection, Context.BIND_AUTO_CREATE)
            } catch (e: Exception) {
                android.util.Log.e("SALTARIN_VPN", "Error conectando al servicio VPN", e)
                stopSelf()
            }
        }
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Temporizador VPN",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun crearNotificacion(texto: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Xaltarin")
            .setContentText(texto)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        try { unbindService(vpnConnection) } catch (_: Exception) {}
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}