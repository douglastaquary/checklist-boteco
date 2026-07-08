package com.checklistboteco.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.checklistboteco.domain.model.ActivityWithCompletion
import com.checklistboteco.domain.model.ChecklistTiming
import com.checklistboteco.domain.model.ChecklistSchedule
import java.util.concurrent.TimeUnit

@Composable
actual fun ChecklistNotificationEffect(items: List<ActivityWithCompletion>, userRemoteId: String?, schedule: ChecklistSchedule) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(items, userRemoteId, schedule) {
        val work = WorkManager.getInstance(context)
        items.forEach { item ->
            val name = "checklist-reminder-${item.activity.syncId}"
            val assigned = item.activity.assigneeIds.isEmpty() || userRemoteId == null || userRemoteId in item.activity.assigneeIds
            if (item.isCompleted || !assigned) { work.cancelUniqueWork(name); return@forEach }
            val timing = ChecklistTiming.forToday(item.activity, item.completion, schedule = schedule)
            val delay = (timing.recommendedStartAt - System.currentTimeMillis()).coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<ChecklistReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("title" to item.activity.name))
                .build()
            work.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

class ChecklistReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Checklist", NotificationManager.IMPORTANCE_DEFAULT))
        val title = inputData.getString("title") ?: return Result.failure()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Hora de iniciar uma atividade")
            .setContentText(title)
            .setAutoCancel(true)
            .build()
        manager.notify(title.hashCode(), notification)
        return Result.success()
    }
    companion object { const val CHANNEL = "checklist-deadlines" }
}
