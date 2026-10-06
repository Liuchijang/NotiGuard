package com.local.notiguard

import android.app.Application
import com.local.notiguard.shizuku.ShizukuManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        I18n.init(this) // before anything formats text (activity, boot-started FCM Guard service)
        ShizukuManager.init()
    }
}
