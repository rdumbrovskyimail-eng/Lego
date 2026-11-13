package com.legode.friedrich

import android.app.Application
import com.legode.friedrich.core.Logx
import com.legode.friedrich.di.graph
import com.legode.friedrich.notify.Jobs
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch

@HiltAndroidApp
class FriedrichApp : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            val graph = graph()
            graph.notifier().ensureChannels()
            if (graph.settings().value.onboarded) {
                // Процесс мог быть запущен системой (будильник, перезагрузка) — сразу проверяем расписание «смс».
                Jobs.watchdog(this)
                graph.scope().launch {
                    try {
                        graph.brain().ensureScheduled()
                    } catch (t: Throwable) {
                        Logx.w("ensureScheduled failed", t)
                    }
                }
            }
        } catch (t: Throwable) {
            Logx.e("app init failed", t)
        }
    }
}
