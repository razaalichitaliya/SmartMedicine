package com.example.smartmedicine

import android.app.Application
import com.example.smartmedicine.reminder.NotificationHelper

class SmartMedicineApp : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }
}
