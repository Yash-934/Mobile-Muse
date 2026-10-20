package com.example.agent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground execution service for active local agent tasks so the user knows when
 * a local shell process or autonomous task is running and to mitigate immediate background kill.
 */
class AgentForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf()
            return START_NOT_STICKY
        }

        val taskSummary = intent?.getStringExtra(EXTRA_TASK_SUMMARY) ?: "Local agent runtime active"
        ensureChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Muse Autonomous Agent")
            .setContentText(taskSummary)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Muse Local Agent Worker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when Muse Mobile is executing local terminal or browser automation tasks."
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "openmuse_agent_worker"
        const val NOTIFICATION_ID = 4201
        const val EXTRA_TASK_SUMMARY = "extra_task_summary"
        const val ACTION_STOP = "com.example.agent.ACTION_STOP_FOREGROUND"

        fun startOrUpdate(context: Context, summary: String) {
            runCatching {
                val intent = Intent(context, AgentForegroundService::class.java).apply {
                    putExtra(EXTRA_TASK_SUMMARY, summary)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching {
                val intent = Intent(context, AgentForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.stopService(intent)
            }
        }
    }
}
