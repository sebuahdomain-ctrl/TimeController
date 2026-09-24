package com.timecontroller.app

object Const {
    const val CHANNEL_PERSISTENT = "timer_persistent"
    const val CHANNEL_ALARM = "timer_alarm"
    const val NOTIF_ID_PERSISTENT = 1001
    const val NOTIF_ID_ALARM = 1002

    const val PREFS_NAME = "timer_prefs"
    const val PREF_REMAINING_SECONDS = "remaining_seconds"
    const val PREF_TOTAL_SECONDS = "total_seconds"
    const val PREF_IS_RUNNING = "is_running"
    const val PREF_IS_PAUSED = "is_paused"

    const val ACTION_START_SERVICE = "com.timecontroller.app.action.START_SERVICE"
    const val ACTION_STOP_SERVICE = "com.timecontroller.app.action.STOP_SERVICE"
    const val ACTION_PAUSE_RESUME = "com.timecontroller.app.action.PAUSE_RESUME"
    const val ACTION_RESET = "com.timecontroller.app.action.RESET"
    const val ACTION_SET_DURATION = "com.timecontroller.app.action.SET_DURATION"
    const val ACTION_TIMER_FINISHED = "com.timecontroller.app.action.TIMER_FINISHED"
    const val ACTION_DISMISS_ALARM = "com.timecontroller.app.action.DISMISS_ALARM"
    const val ACTION_TICK_UPDATE = "com.timecontroller.app.action.TICK_UPDATE"

    const val EXTRA_SECONDS = "extra_seconds"

    const val BROADCAST_TICK = "com.timecontroller.app.broadcast.TICK"
}
