package com.mdyerapis.sable

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mdyerapis.sable.backendclient.DeviceTokenRegistrar
import com.mdyerapis.sable.core.database.automation.AutomationScheduler
import com.mdyerapis.sable.core.database.automation.AutomationStore
import com.mdyerapis.sable.core.database.reminder.ReminderScheduler
import com.mdyerapis.sable.core.database.reminder.ReminderStore
import com.mdyerapis.sable.core.security.BearerTokenRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), Configuration.Provider {

    @Inject
    lateinit var deviceTokenRegistrar: DeviceTokenRegistrar

    @Inject
    lateinit var bearerTokenRepository: BearerTokenRepository

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var reminderStore: ReminderStore

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    @Inject
    lateinit var automationStore: AutomationStore

    @Inject
    lateinit var automationScheduler: AutomationScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Register the FCM device token with the backend on startup.
        // Only if onboarding is already complete (bearer token saved).
        // Fresh installs register the token after onboarding completes
        // (see OnboardingViewModel.submit).
        // The token is also re-registered automatically whenever Firebase
        // issues a new one (see AssistantMessagingService.onNewToken).
        if (bearerTokenRepository.getToken() != null) {
            deviceTokenRegistrar.registerCurrentToken()
        }
        // Re-bind pending on-device reminders after process death / update.
        // WorkManager also persists, but unique REPLACE keeps the two in sync.
        appScope.launch {
            reminderStore.listPending().forEach { reminderScheduler.schedule(it) }
            automationStore.listEnabled().forEach { automationScheduler.schedule(it) }
        }
    }
}
