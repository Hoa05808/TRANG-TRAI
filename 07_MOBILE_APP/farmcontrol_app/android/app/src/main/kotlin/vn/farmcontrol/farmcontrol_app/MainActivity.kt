package vn.farmcontrol.farmcontrol_app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity

class MainActivity : FlutterActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createAlertChannel()
    }

    /** Kênh "farm_alerts" mức cao: hiện banner và kêu chuông khi có cảnh báo trang trại. */
    private fun createAlertChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            "farm_alerts",
            "Cảnh báo trang trại",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Nhiệt độ cao, mực nước thấp, mất kết nối PLC/Gateway..."
            enableVibration(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
            )
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
