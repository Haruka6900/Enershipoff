package com.enship.travel.bluetooth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.enship.travel.MainActivity
import com.enship.travel.R
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Service de premier plan : maintient la liaison Bluetooth active meme lorsque
 * l'application passe en arriere-plan (exigence d'un systeme de monitoring
 * embarque). Affiche une notification persistante avec l'etat de la liaison.
 *
 * Le [BluetoothManager] etant un singleton, le service ne fait que garantir la
 * survie du process et refleter l'etat ; toute la logique reste centralisee.
 */
class BluetoothLinkService : LifecycleService() {

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundCompat(buildNotification("Demarrage...", ConnectionState.CONNECTING))
        observeState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val address = intent?.getStringExtra(EXTRA_ADDRESS)
        if (address != null) {
            BluetoothManager.connect(address)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun observeState() {
        lifecycleScope.launch {
            combine(
                BluetoothManager.state,
                BluetoothManager.statusMessage,
            ) { state, message -> state to message }
                .collect { (state, message) ->
                    val title = when (state) {
                        ConnectionState.CONNECTED -> "EnerShip - Connecte"
                        ConnectionState.CONNECTING -> "EnerShip - Connexion"
                        ConnectionState.RECONNECTING -> "EnerShip - Reconnexion"
                        ConnectionState.ERROR -> "EnerShip - Erreur"
                        ConnectionState.DISCONNECTED -> "EnerShip - Deconnecte"
                    }
                    notify(title, message, state)
                    if (state == ConnectionState.DISCONNECTED &&
                        BluetoothManager.connectedDevice.value == null
                    ) {
                        stopSelf()
                    }
                }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Liaison Bluetooth EnerShip",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Etat de la liaison avec la centrale Arduino"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        message: String,
        state: ConnectionState,
    ): Notification {
        val pending = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("EnerShip Travel")
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun notify(title: String, message: String, state: ConnectionState) {
        val n = buildNotification(message, state).let {
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        }
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
    }

    companion object {
        private const val CHANNEL_ID = "enership_bt_link"
        private const val NOTIF_ID = 4201
        const val EXTRA_ADDRESS = "extra_address"

        fun start(context: Context, address: String) {
            val intent = Intent(context, BluetoothLinkService::class.java).apply {
                putExtra(EXTRA_ADDRESS, address)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BluetoothLinkService::class.java))
        }
    }
}
