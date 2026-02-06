package com.example.travelpool.screens.notification

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    fun scheduleTripStartReminders(
        context: Context,
        tripId: String,
        tripName: String,
        startDateMillis: Long
    ) {
        schedule(context, tripId, tripName, startDateMillis, daysBefore = 7)
        schedule(context, tripId, tripName, startDateMillis, daysBefore = 1)
    }

    private fun schedule(
        context: Context,
        tripId: String,
        tripName: String,
        startDateMillis: Long,
        daysBefore: Int
    ) {
        val triggerAt = startDateMillis - TimeUnit.DAYS.toMillis(daysBefore.toLong())
        val delay = triggerAt - System.currentTimeMillis()
        if (delay <= 0) return

        val req = OneTimeWorkRequestBuilder<TripStartReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    "tripId" to tripId,
                    "tripName" to tripName,
                    "days" to daysBefore
                )
            )
            .addTag("trip_reminder$tripId$daysBefore")
            .build()

        WorkManager.getInstance(context)
            .enqueue(req)
    }
}