package com.example.travelpool.screens.notification

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class TripStartReminderWorker(
    ctx: Context,
    params: WorkerParameters
): CoroutineWorker(ctx, params) {
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun doWork(): Result {
        val tripName = inputData.getString("tripName") ?: return Result.failure()
        val days = inputData.getInt("days", 0)

        SystemNotify.show(
            applicationContext,
            title = "Trip coming up",
            body = "$tripName starts in $days day${if (days == 1) "" else "s"}."
        )
        return Result.success()
    }
}