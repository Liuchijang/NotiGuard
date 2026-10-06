package com.local.notiguard.fcmguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the guard after reboot / app update when the user left it enabled. */
class FcmGuardBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (FcmGuard.prefs(context).enabled) FcmGuardService.start(context)
    }
}
