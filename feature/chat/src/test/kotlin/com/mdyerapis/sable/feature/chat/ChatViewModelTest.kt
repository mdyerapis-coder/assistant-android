package com.mdyerapis.sable.feature.chat

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.mdyerapis.sable.backendclient.ThreadsApi
import com.mdyerapis.sable.core.database.chat.ConversationStore
import com.mdyerapis.sable.feature.chat.ExternalIntake
import com.mdyerapis.sable.core.database.chat.ConversationSummary
import com.mdyerapis.sable.core.database.chat.StoredMessage
import com.mdyerapis.sable.core.security.BearerTokenRepository
import com.mdyerapis.sable.feature.localmodel.LlmInferenceService
import com.mdyerapis.sable.feature.localmodel.LocalModelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var filesDir: File
    private lateinit var context: Context

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = (data[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = (data[key] as? MutableSet<String>) ?: defValues
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

    private class FakeConversationStore : ConversationStore {
        private val _conversations = MutableStateFlow<List<ConversationSummary>>(emptyList())
        private val messages = mutableMapOf<String, MutableList<StoredMessage>>()
        // Live per-conversation flows, mirroring Room's reactive query
        // behavior — messagesFor must re-emit after replaceMessages.
        private val messageFlows = mutableMapOf<String, MutableStateFlow<List<StoredMessage>>>()
        private var counter = 1

        override val conversations: Flow<List<ConversationSummary>> = _conversations

        override suspend fun createConversation(
            modelId: String?, mode: String, title: String, serverConversationId: String?
        ): String {
            val id = "convo-${counter++}"
            val now = System.currentTimeMillis()
            _conversations.value = _conversations.value + ConversationSummary(
                id = id, title = title, preview = "", modelId = modelId,
                mode = mode, serverConversationId = serverConversationId, updatedAt = now,
            )
            messages[id] = mutableListOf()
            return id
        }

        override suspend fun setServerConversationId(id: String, serverConversationId: String?) {
            _conversations.value = _conversations.value.map {
                if (it.id == id) it.copy(serverConversationId = serverConversationId) else it
            }
        }

        override suspend fun deleteConversation(id: String) {
            _conversations.value = _conversations.value.filterNot { it.id == id }
            messages.remove(id)
            messageFlows.remove(id)
        }

        override suspend fun clearAll() {
            _conversations.value = emptyList()
            messages.clear()
            messageFlows.clear()
        }


        override fun messagesFor(conversationId: String): Flow<List<StoredMessage>> =
            flowFor(conversationId)

        override suspend fun appendMessage(
            conversationId: String, messageId: String, role: String, content: String,
            toolCallId: String?, toolName: String?, toolArgsJson: String?, toolResult: String?, isError: Boolean
        ) {
            messages.getOrPut(conversationId) { mutableListOf() }.add(
                StoredMessage(messageId, conversationId, role, content,
                    toolCallId, toolName, toolArgsJson, toolResult, isError, System.currentTimeMillis())
            )
            flowFor(conversationId).value = messages[conversationId]?.toList() ?: emptyList()
        }

        fun capturedMessages(conversationId: String): List<StoredMessage> =
            messages[conversationId] ?: emptyList()

        fun createdCount(): Int = _conversations.value.size

        fun summaries(): List<ConversationSummary> = _conversations.value

        override suspend fun cacheServerThread(
            serverConversationId: String,
            title: String,
            preview: String,
            createdAtMs: Long,
            updatedAtMs: Long,
        ): String {
            val existing = _conversations.value.firstOrNull {
                it.serverConversationId == serverConversationId
            }
            val id = existing?.id ?: serverConversationId
            _conversations.value = _conversations.value.filterNot { it.id == id } + ConversationSummary(
                id = id,
                title = title,
                preview = preview,
                modelId = existing?.modelId,
                mode = existing?.mode ?: "cloud",
                serverConversationId = serverConversationId,
                updatedAt = updatedAtMs,
            )
            messages.getOrPut(id) { mutableListOf() }
            flowFor(id)
            return id
        }

        override suspend fun replaceMessages(
            conversationId: String,
            newMessages: List<StoredMessage>,
        ) {
            messages[conversationId] = newMessages.toMutableList()
            flowFor(conversationId).value = newMessages
        }

        private fun flowFor(conversationId: String): MutableStateFlow<List<StoredMessage>> =
            messageFlows.getOrPut(conversationId) {
                MutableStateFlow(messages[conversationId] ?: emptyList())
            }
    }
    private class FakeThreadsApi(
        private val threads: ThreadsApi.ThreadsResponse = ThreadsApi.ThreadsResponse(),
        private val messagesByThread: Map<String, ThreadsApi.ThreadMessagesResponse> = emptyMap(),
        private val fails: Boolean = false,
    ) : ThreadsApi(OkHttpClient(), "http://localhost:0") {
        override fun listThreads(): ThreadsApi.ThreadsResponse {
            if (fails) throw java.io.IOException("offline")
            return threads
        }
        override fun listMessages(threadId: String): ThreadsApi.ThreadMessagesResponse =
            messagesByThread[threadId] ?: ThreadsApi.ThreadMessagesResponse(threadId)
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        filesDir = tempFolder.newFolder("files")
        context = TestContext(filesDir, InMemorySharedPreferences())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun modeSwitch_updatesAppModelMode() = runTest(testDispatcher) {
        val modelPrefs = ModelPreferenceRepository(context)
        val tokenRepo = object : BearerTokenRepository(context) {
            override fun getToken(): String? = "test_token"
            // Fast-fail endpoint: instant connection refused, so IO work
            // resolves inside a bounded settle() pump instead of leaking
            // past the test scope (the historical fatal-coroutine flake).
            override fun getBaseUrl(): String? = "http://127.0.0.1:1"
        }
        val googleManager = object : GoogleAccountManager(context, OkHttpClient()) {
            override suspend fun status(): Boolean = false
        }
        val notifier = GoogleOAuthCompletionNotifier()
        val localRepo = LocalModelRepository(context, OkHttpClient())
        val inferenceService = object : LlmInferenceService(context, localRepo) {}
        val store = FakeConversationStore()

        val viewModel = object : ChatViewModel(
            tokenRepository = tokenRepo,
            googleAccountManager = googleManager,
            googleOAuthCompletionNotifier = notifier,
            modelPreferenceRepository = modelPrefs,
            localModelRepository = localRepo,
            llmInferenceService = inferenceService,
            conversationStore = store,
            externalIntake = ExternalIntake(),
        ) {
            override fun createThreadsApi(client: OkHttpClient, baseUrl: String): ThreadsApi =
                FakeThreadsApi()
        }

        assertEquals(AppModelMode.Backend, viewModel.uiState.value.appModelMode)

        viewModel.setAppModelMode(AppModelMode.OnDevice)
        settle(viewModel)
        assertEquals(AppModelMode.OnDevice, viewModel.uiState.value.appModelMode)

        // Sending message while local model is NotInstalled triggers error and dialog
        viewModel.sendMessage("hello")
        settle(viewModel)

        assertTrue(viewModel.uiState.value.showLocalModelDialog)
        assertNotNull(viewModel.uiState.value.chatState.error)
        assertTrue(viewModel.uiState.value.chatState.error!!.contains("Local model is not installed"))
    }

    @Test
    fun clearConversation_resetsMessagesAndConversationId() = runTest(testDispatcher) {
        val modelPrefs = ModelPreferenceRepository(context)
        val tokenRepo = object : BearerTokenRepository(context) {
            override fun getToken(): String? = "test_token"
            // Fast-fail endpoint: instant connection refused, so IO work
            // resolves inside a bounded settle() pump instead of leaking
            // past the test scope (the historical fatal-coroutine flake).
            override fun getBaseUrl(): String? = "http://127.0.0.1:1"
        }
        val googleManager = object : GoogleAccountManager(context, OkHttpClient()) {
            override suspend fun status(): Boolean = false
        }
        val notifier = GoogleOAuthCompletionNotifier()
        val localRepo = LocalModelRepository(context, OkHttpClient())
        val inferenceService = object : LlmInferenceService(context, localRepo) {}
        val store = FakeConversationStore()

        val viewModel = object : ChatViewModel(
            tokenRepository = tokenRepo,
            googleAccountManager = googleManager,
            googleOAuthCompletionNotifier = notifier,
            modelPreferenceRepository = modelPrefs,
            localModelRepository = localRepo,
            llmInferenceService = inferenceService,
            conversationStore = store,
            externalIntake = ExternalIntake(),
        ) {
            override fun createThreadsApi(client: OkHttpClient, baseUrl: String): ThreadsApi =
                FakeThreadsApi()
        }

        // Set some dummy state
        viewModel.setAppModelMode(AppModelMode.OnDevice)
        viewModel.sendMessage("test")
        settle(viewModel)

        viewModel.clearConversation()
        settle(viewModel)

        assertTrue(viewModel.uiState.value.chatState.messages.isEmpty())
        assertEquals(null, viewModel.uiState.value.chatState.conversationId)
        assertEquals(null, viewModel.uiState.value.chatState.error)
    }

    @Test
    fun persistsMessagesAcrossRestart() = runTest(testDispatcher) {
        val modelPrefs = ModelPreferenceRepository(context)
        val tokenRepo = object : BearerTokenRepository(context) {
            override fun getToken(): String? = "test_token"
            // Fast-fail endpoint: instant connection refused, so IO work
            // resolves inside a bounded settle() pump instead of leaking
            // past the test scope (the historical fatal-coroutine flake).
            override fun getBaseUrl(): String? = "http://127.0.0.1:1"
        }
        val googleManager = object : GoogleAccountManager(context, OkHttpClient()) {
            override suspend fun status(): Boolean = false
        }
        val notifier = GoogleOAuthCompletionNotifier()
        val localRepo = LocalModelRepository(context, OkHttpClient())
        val inferenceService = object : LlmInferenceService(context, localRepo) {}
        val store = FakeConversationStore()

        val viewModel = object : ChatViewModel(
            tokenRepository = tokenRepo,
            googleAccountManager = googleManager,
            googleOAuthCompletionNotifier = notifier,
            modelPreferenceRepository = modelPrefs,
            localModelRepository = localRepo,
            llmInferenceService = inferenceService,
            conversationStore = store,
            externalIntake = ExternalIntake(),
        ) {
            override fun createThreadsApi(client: OkHttpClient, baseUrl: String): ThreadsApi =
                FakeThreadsApi()
        }
        settle(viewModel)

        viewModel.setAppModelMode(AppModelMode.OnDevice)
        settle(viewModel)

        viewModel.sendMessage("hello")
        settle(viewModel)

        val sessions = viewModel.uiState.value.availableSessions
        assertEquals(1, sessions.size)
        assertNotNull(sessions.first().id)
    }

    @Test
    fun backendSend_persistsUserMessage() = runTest(testDispatcher) {
        val modelPrefs = ModelPreferenceRepository(context)
        val tokenRepo = object : BearerTokenRepository(context) {
            override fun getToken(): String? = "test_token"
            // Fast-fail endpoint: instant connection refused, so IO work
            // resolves inside a bounded settle() pump instead of leaking
            // past the test scope (the historical fatal-coroutine flake).
            override fun getBaseUrl(): String? = "http://127.0.0.1:1"
        }
        val googleManager = object : GoogleAccountManager(context, OkHttpClient()) {
            override suspend fun status(): Boolean = false
        }
        val notifier = GoogleOAuthCompletionNotifier()
        val localRepo = LocalModelRepository(context, OkHttpClient())
        val inferenceService = object : LlmInferenceService(context, localRepo) {}
        val store = FakeConversationStore()

        val viewModel = object : ChatViewModel(
            tokenRepository = tokenRepo,
            googleAccountManager = googleManager,
            googleOAuthCompletionNotifier = notifier,
            modelPreferenceRepository = modelPrefs,
            localModelRepository = localRepo,
            llmInferenceService = inferenceService,
            conversationStore = store,
            externalIntake = ExternalIntake(),
        ) {
            override fun createThreadsApi(client: OkHttpClient, baseUrl: String): ThreadsApi =
                FakeThreadsApi()
        }
        settle(viewModel)

        // Backend default mode.
        viewModel.sendMessage("a backend question")
        settle(viewModel)

        // The user message must persist into the (fake) store even if the backend
        // stream fails (no server in test) — append happens before the network call.
        val convoId = viewModel.uiState.value.availableSessions.first().id
        val captured = store.capturedMessages(convoId)
        assertTrue("user message should be persisted", captured.any { it.role == "user" })
    }

    @Test
    fun switchConversation_loadsItsHistory() = runTest(testDispatcher) {
        val modelPrefs = ModelPreferenceRepository(context)
        val tokenRepo = object : BearerTokenRepository(context) {
            override fun getToken(): String? = "test_token"
            // Fast-fail endpoint: instant connection refused, so IO work
            // resolves inside a bounded settle() pump instead of leaking
            // past the test scope (the historical fatal-coroutine flake).
            override fun getBaseUrl(): String? = "http://127.0.0.1:1"
        }
        val googleManager = object : GoogleAccountManager(context, OkHttpClient()) {
            override suspend fun status(): Boolean = false
        }
        val notifier = GoogleOAuthCompletionNotifier()
        val localRepo = LocalModelRepository(context, OkHttpClient())
        val inferenceService = object : LlmInferenceService(context, localRepo) {}
        val store = FakeConversationStore()
        val threadsApi = FakeThreadsApi(
            messagesByThread = mapOf(
                "server-1" to ThreadsApi.ThreadMessagesResponse(
                    thread_id = "server-1",
                    messages = listOf(
                        ThreadsApi.ThreadMessage(
                            id = 12, role = "user", content = "Server hello",
                            created_at = "2026-08-31T10:00:00+00:00",
                        ),
                        ThreadsApi.ThreadMessage(
                            id = 15, role = "assistant", content = "Server reply",
                            created_at = "2026-08-31T10:00:02+00:00",
                        ),
                    ),
                ),
            ),
        )

        // Pre-seed a conversation with a stored message.
        val convoId = store.createConversation(
            modelId = null,
            mode = "Backend",
            title = "Old chat",
            serverConversationId = "server-1",
        )
        store.appendMessage(
            conversationId = convoId,
            messageId = "m1",
            role = "user",
            content = "Persisted hello",
        )

        val viewModel = object : ChatViewModel(
            tokenRepository = tokenRepo,
            googleAccountManager = googleManager,
            googleOAuthCompletionNotifier = notifier,
            modelPreferenceRepository = modelPrefs,
            localModelRepository = localRepo,
            llmInferenceService = inferenceService,
            conversationStore = store,
            externalIntake = ExternalIntake(),
        ) {
            override fun createThreadsApi(client: OkHttpClient, baseUrl: String): ThreadsApi =
                threadsApi
        }
        settle(viewModel)

        viewModel.switchConversation(convoId)
        settle(viewModel)

        // The server fetch runs via withContext(Dispatchers.IO) on a real
        // thread; pump the scheduler until the replacement lands.
        awaitUntil {
            viewModel.uiState.value.chatState.messages.map { it.content } ==
                listOf("Server hello", "Server reply")
        }

        // Cached messages are replaced by the server's renderable history —
        // the server is the source of truth (phase 08).
        val messages = viewModel.uiState.value.chatState.messages
        assertEquals(listOf("Server hello", "Server reply"), messages.map { it.content })
        assertEquals("server-1", viewModel.uiState.value.chatState.conversationId)
    }

    @Test
    fun syncThreads_cachesServerThreadsAsConversations() = runTest(testDispatcher) {
        val modelPrefs = ModelPreferenceRepository(context)
        val tokenRepo = object : BearerTokenRepository(context) {
            override fun getToken(): String? = "test_token"
            // Fast-fail endpoint: instant connection refused, so IO work
            // resolves inside a bounded settle() pump instead of leaking
            // past the test scope (the historical fatal-coroutine flake).
            override fun getBaseUrl(): String? = "http://127.0.0.1:1"
        }
        val googleManager = object : GoogleAccountManager(context, OkHttpClient()) {
            override suspend fun status(): Boolean = false
        }
        val notifier = GoogleOAuthCompletionNotifier()
        val localRepo = LocalModelRepository(context, OkHttpClient())
        val inferenceService = object : LlmInferenceService(context, localRepo) {}
        val store = FakeConversationStore()
        val threadsApi = FakeThreadsApi(
            threads = ThreadsApi.ThreadsResponse(
                threads = listOf(
                    ThreadsApi.ThreadSummary(
                        id = "srv-9",
                        title = "what's on my calendar today",
                        preview = "You have nothing scheduled.",
                        created_at = "2026-08-31T10:00:00+00:00",
                        last_message_at = "2026-08-31T10:00:02+00:00",
                        message_count = 3,
                    ),
                ),
            ),
        )

        val viewModel = object : ChatViewModel(
            tokenRepository = tokenRepo,
            googleAccountManager = googleManager,
            googleOAuthCompletionNotifier = notifier,
            modelPreferenceRepository = modelPrefs,
            localModelRepository = localRepo,
            llmInferenceService = inferenceService,
            conversationStore = store,
            externalIntake = ExternalIntake(),
        ) {
            override fun createThreadsApi(client: OkHttpClient, baseUrl: String): ThreadsApi =
                threadsApi
        }
        settle(viewModel)

        // initClient at construction time syncs threads — a fresh install
        // with an empty cache sees the server's thread list (phase 08).
        // Same real-IO pumping as above.
        awaitUntil { store.summaries().any { it.serverConversationId == "srv-9" } }
        val synced = store.summaries().firstOrNull { it.serverConversationId == "srv-9" }
        assertNotNull(synced)
        assertEquals("what's on my calendar today", synced!!.title)
        assertEquals("You have nothing scheduled.", synced.preview)
        assertTrue(viewModel.uiState.value.availableSessions.any { it.id == "srv-9" })
    }
    @Test
    fun sendWhileStreaming_queuesAndDrains() = runTest(testDispatcher) {
        val modelPrefs = ModelPreferenceRepository(context)
        val tokenRepo = object : BearerTokenRepository(context) {
            override fun getToken(): String? = "test_token"
            // Fast-fail endpoint: instant connection refused, so the IO
            // failure path completes inside a bounded awaitUntil pump.
            override fun getBaseUrl(): String? = "http://127.0.0.1:1"
        }
        val googleManager = object : GoogleAccountManager(context, OkHttpClient()) {
            override suspend fun status(): Boolean = false
        }
        val notifier = GoogleOAuthCompletionNotifier()
        val localRepo = LocalModelRepository(context, OkHttpClient())
        val inferenceService = object : LlmInferenceService(context, localRepo) {}
        val store = FakeConversationStore()

        val viewModel = object : ChatViewModel(
            tokenRepository = tokenRepo,
            googleAccountManager = googleManager,
            googleOAuthCompletionNotifier = notifier,
            modelPreferenceRepository = modelPrefs,
            localModelRepository = localRepo,
            llmInferenceService = inferenceService,
            conversationStore = store,
            externalIntake = ExternalIntake(),
        ) {
            override fun createThreadsApi(client: OkHttpClient, baseUrl: String): ThreadsApi =
                FakeThreadsApi()
        }
        settle(viewModel)

        viewModel.sendMessage("first")
        // isLoading flips synchronously before the coroutine starts,
        // so the second send must queue instead of firing concurrently.
        assertTrue(
            "first send must enter loading state synchronously (mode=%s)".format(
                viewModel.uiState.value.appModelMode
            ),
            viewModel.uiState.value.chatState.isLoading,
        )
        viewModel.sendMessage("second")
        assertEquals(listOf("second"), viewModel.uiState.value.pendingMessages)

        // The failure path completes on Dispatchers.IO, outside the test
        // scheduler — pump until the queue drains (same idiom as the
        // thread-sync tests above).
        awaitUntil { viewModel.uiState.value.pendingMessages.isEmpty() }
        awaitUntil {
            store.summaries().flatMap { store.capturedMessages(it.id) }
                .count { it.role == "user" } >= 2
        }
        // Leave no in-flight IO behind: a dangling stream coroutine fails
        // after this test's scope closes and corrupts the NEXT test.
        awaitUntil {
            !viewModel.uiState.value.chatState.isLoading &&
                viewModel.uiState.value.pendingMessages.isEmpty()
        }
        val captured = store.summaries().flatMap { store.capturedMessages(it.id) }
        assertTrue(captured.any { it.role == "user" && it.content == "first" })
        assertTrue(
            "captured=" + captured.joinToString { "${it.role}:${it.content}" } +
                ", sessions=" + store.summaries().joinToString { it.id },
            captured.any { it.role == "user" && it.content == "second" },
        )
    }

    @Test
    fun stopGenerating_cancelsStreamAndClearsQueue() = runTest(testDispatcher) {
        val modelPrefs = ModelPreferenceRepository(context)
        val tokenRepo = object : BearerTokenRepository(context) {
            override fun getToken(): String? = "test_token"
            // Fast-fail endpoint: instant connection refused, so the IO
            // failure path completes inside a bounded awaitUntil pump.
            override fun getBaseUrl(): String? = "http://127.0.0.1:1"
        }
        val googleManager = object : GoogleAccountManager(context, OkHttpClient()) {
            override suspend fun status(): Boolean = false
        }
        val notifier = GoogleOAuthCompletionNotifier()
        val localRepo = LocalModelRepository(context, OkHttpClient())
        val inferenceService = object : LlmInferenceService(context, localRepo) {}
        val store = FakeConversationStore()

        val viewModel = object : ChatViewModel(
            tokenRepository = tokenRepo,
            googleAccountManager = googleManager,
            googleOAuthCompletionNotifier = notifier,
            modelPreferenceRepository = modelPrefs,
            localModelRepository = localRepo,
            llmInferenceService = inferenceService,
            conversationStore = store,
            externalIntake = ExternalIntake(),
        ) {
            override fun createThreadsApi(client: OkHttpClient, baseUrl: String): ThreadsApi =
                FakeThreadsApi()
        }
        settle(viewModel)

        viewModel.sendMessage("first")
        viewModel.sendMessage("second")
        viewModel.stopGenerating()

        assertTrue(viewModel.uiState.value.pendingMessages.isEmpty())
        assertFalse(viewModel.uiState.value.chatState.isLoading)
        // Cancelled stream leaves an in-flight IO continuation briefly;
        // settle it before this test's scope closes.
        awaitUntil { !viewModel.uiState.value.chatState.isLoading }
        settle(viewModel)
    }

    @Test
    fun syncThreadsFailure_marksSessionsOffline() = runTest(testDispatcher) {
        val modelPrefs = ModelPreferenceRepository(context)
        val tokenRepo = object : BearerTokenRepository(context) {
            override fun getToken(): String? = "test_token"
            override fun getBaseUrl(): String? = "http://127.0.0.1:1"
        }
        val googleManager = object : GoogleAccountManager(context, OkHttpClient()) {
            override suspend fun status(): Boolean = false
        }
        val notifier = GoogleOAuthCompletionNotifier()
        val localRepo = LocalModelRepository(context, OkHttpClient())
        val inferenceService = object : LlmInferenceService(context, localRepo) {}
        val store = FakeConversationStore()

        val viewModel = object : ChatViewModel(
            tokenRepository = tokenRepo,
            googleAccountManager = googleManager,
            googleOAuthCompletionNotifier = notifier,
            modelPreferenceRepository = modelPrefs,
            localModelRepository = localRepo,
            llmInferenceService = inferenceService,
            conversationStore = store,
            externalIntake = ExternalIntake(),
        ) {
            override fun createThreadsApi(client: OkHttpClient, baseUrl: String): ThreadsApi =
                FakeThreadsApi(fails = true)
        }

        // initClient ran syncThreads at construction; the failed fetch must
        // surface as sessionsOffline rather than a silent empty list.
        awaitUntil { viewModel.uiState.value.sessionsOffline }
        assertTrue(viewModel.uiState.value.sessionsOffline)
        settle(viewModel)
    }

    /**
     * Pump until the ViewModel has no in-flight work (model catalog, thread
     * sync, chat streams, queues). Replaces advanceUntilIdle in this file:
     * scheduler-only pumping misses real Dispatchers.IO continuations and
     * those leak past teardown as fatal machinery crashes.
     */
    private fun kotlinx.coroutines.test.TestScope.settle(viewModel: ChatViewModel) {
        awaitUntil {
            !viewModel.uiState.value.isLoadingModels &&
                !viewModel.uiState.value.chatState.isLoading &&
                viewModel.uiState.value.pendingMessages.isEmpty()
        }
        testScheduler.runCurrent()
    }

    /**
     * withContext(Dispatchers.IO) escapes the test scheduler; poll the
     * condition while churning the scheduler so the IO continuation's
     * resumption gets processed. Bounded so a real failure still fails.
     */
    private fun kotlinx.coroutines.test.TestScope.awaitUntil(
        iterations: Int = 500,
        condition: () -> Boolean,
    ) {
        repeat(iterations) {
            if (condition()) return
            testScheduler.runCurrent()
            testScheduler.advanceTimeBy(10)
            Thread.sleep(1) // real-IO continuations (Dispatchers.IO) need wall time
        }
        // Bounded: if the condition still doesn't hold, the caller's
        // assert fails with a clear message.
    }
}
