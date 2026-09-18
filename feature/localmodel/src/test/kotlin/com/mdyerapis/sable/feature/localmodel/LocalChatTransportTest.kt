package com.mdyerapis.sable.feature.localmodel

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.mdyerapis.sable.backendclient.ChatTurnRequest
import com.mdyerapis.sable.backendclient.TransportCapabilities
import com.mdyerapis.sable.core.model.ChatEvent
import com.mdyerapis.sable.core.model.ChatMessage
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalChatTransportTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var repo: LocalModelRepository

    @Before
    fun setup() {
        filesDir = tempFolder.newFolder("files")
        context = TestContext(filesDir, InMemorySharedPreferences())
        repo = LocalModelRepository(context, OkHttpClient())
    }

    @Test
    fun capabilitiesAreOnDeviceOnly() {
        val transport = LocalChatTransport(LlmInferenceService(context, repo), repo)
        assertEquals(TransportCapabilities.ON_DEVICE, transport.capabilities)
        assertTrue(transport.capabilities.offline)
        assertTrue(transport.capabilities.reminders)
        assertTrue(!transport.capabilities.tools)
        assertTrue(transport.capabilities.google)
    }

    @Test
    fun notInstalledEmitsError() = runTest {
        val transport = LocalChatTransport(LlmInferenceService(context, repo), repo)
        val events = transport.stream(ChatTurnRequest(message = "hi")).toList()
        val error = events.single() as ChatEvent.Error
        assertTrue(error.message.contains("not installed"))
        assertTrue(error.retryable)
    }

    @Test
    fun reminderCreateWorksWithoutModelWeights() = runTest {
        val store = GatewayStore()
        val scheduler = GatewayScheduler()
        val transport = LocalChatTransport(
            LlmInferenceService(context, repo),
            repo,
            DefaultLocalReminderGateway(store, scheduler),
        )
        val events = transport.stream(
            ChatTurnRequest(message = "Remind me to stretch in 30 minutes", conversationId = "local:1"),
        ).toList()
        assertTrue(events.any { it is ChatEvent.ToolCallStarted && it.name == "create_reminder" })
        assertTrue(events.any { it is ChatEvent.MessageCompleted })
        assertEquals(1, scheduler.scheduled.size)
        assertTrue(events.none { it is ChatEvent.Error })
    }

    @Test
    fun calendarWithoutBearerIsHonestO1Failure() = runTest {
        val tokens = object : com.mdyerapis.sable.core.security.BearerTokenRepository(context) {
            override fun getToken(): String? = null
        }
        val transport = LocalChatTransport(
            LlmInferenceService(context, repo),
            repo,
            DefaultLocalReminderGateway(GatewayStore(), GatewayScheduler()),
            DefaultLocalGoogleGateway(tokens),
        )
        val events = transport.stream(
            ChatTurnRequest(message = "what's on my calendar?", conversationId = "local:1"),
        ).toList()
        assertTrue(events.any { it is ChatEvent.ToolCallStarted && it.name == "calendar" })
        assertTrue(events.any { it is ChatEvent.ToolCallFinished && !it.ok })
        val spoken = events.filterIsInstance<ChatEvent.Delta>().single().content
        assertTrue(spoken.contains("O1 relay"))
        assertTrue(spoken.contains("client_secret"))
        assertTrue(events.none { it is ChatEvent.Error && it.message.contains("not installed") })
    }

    @Test
    fun readyModelStreamsDeltasThroughChatEvents() = runTest {
        val modelFile = File(filesDir, "models/gemma-3n-E2B-it.task")
        modelFile.parentFile!!.mkdirs()
        modelFile.writeText("dummy-weights")
        repo.checkInstalledState()
        assertTrue(repo.state.value is LocalModelState.Ready)

        var capturedPrompt: String? = null
        val inference = object : LlmInferenceService(context, repo) {
            override suspend fun generate(
                prompt: String,
                replaceInput: Boolean,
                onPartial: suspend (String) -> Unit,
            ): String {
                capturedPrompt = prompt
                onPartial("Hel")
                onPartial("lo")
                return "Hello"
            }
        }
        val transport = LocalChatTransport(inference, repo)
        val events = transport.stream(
            ChatTurnRequest(
                message = "Say hello",
                conversationId = "local:1",
                history = listOf(
                    ChatMessage(id = "u1", role = "user", content = "Say hello"),
                ),
            ),
        ).toList()

        assertEquals("Hel", (events[0] as ChatEvent.Delta).content)
        assertEquals("lo", (events[1] as ChatEvent.Delta).content)
        assertTrue(events[2] is ChatEvent.MessageCompleted)
        assertTrue(capturedPrompt!!.contains(LocalPromptBuilder.SYSTEM_PREAMBLE))
        assertTrue(capturedPrompt!!.contains("Say hello"))
        assertTrue(events.none { it is ChatEvent.ToolCallStarted })
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()
        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = (data[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (data[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = EditorImpl(this)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class EditorImpl(private val prefs: InMemorySharedPreferences) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearPending = false
            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { pending[key!!] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply { pending[key!!] = values }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { pending[key!!] = value }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { pending[key!!] = value }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { pending[key!!] = value }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { pending[key!!] = value }
            override fun remove(key: String?): SharedPreferences.Editor = apply { pending[key!!] = null }
            override fun clear(): SharedPreferences.Editor = apply { clearPending = true }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearPending) prefs.data.clear()
                pending.forEach { (k, v) ->
                    if (v == null) prefs.data.remove(k) else prefs.data[k] = v
                }
            }
        }
    }

    private class TestContext(private val baseDir: File, private val prefs: SharedPreferences) : ContextWrapper(null) {
        override fun getFilesDir(): File = baseDir
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
    }

    private class GatewayStore : com.mdyerapis.sable.core.database.reminder.ReminderStore {
        private val rows = LinkedHashMap<String, com.mdyerapis.sable.core.database.reminder.LocalReminder>()
        override suspend fun insert(reminder: com.mdyerapis.sable.core.database.reminder.LocalReminder) {
            rows[reminder.id] = reminder
        }
        override suspend fun get(id: String) = rows[id]
        override suspend fun listPending() = rows.values.filter { it.isPending }.sortedBy { it.dueAtMillis }
        override suspend fun listAll() = rows.values.toList()
        override suspend fun markFired(id: String, nowMillis: Long): Boolean = false
        override suspend fun markCancelled(id: String): Boolean = false
    }

    private class GatewayScheduler : com.mdyerapis.sable.core.database.reminder.ReminderScheduler {
        val scheduled = mutableListOf<com.mdyerapis.sable.core.database.reminder.LocalReminder>()
        override fun schedule(reminder: com.mdyerapis.sable.core.database.reminder.LocalReminder) {
            scheduled += reminder
        }
        override fun cancel(id: String) {}
    }
}
