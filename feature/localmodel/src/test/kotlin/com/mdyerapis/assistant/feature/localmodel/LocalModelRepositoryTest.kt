package com.mdyerapis.assistant.feature.localmodel

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class LocalModelRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockWebServer: MockWebServer
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var context: Context
    private lateinit var filesDir: File

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

    private class TestContext(private val testFilesDir: File, private val prefs: SharedPreferences) : ContextWrapper(null) {
        override fun getFilesDir(): File = testFilesDir
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
    }

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        okHttpClient = OkHttpClient()

        filesDir = tempFolder.newFolder("files")
        context = TestContext(filesDir, InMemorySharedPreferences())
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun install_downloadsModelAndTransitionsToReady() = runTest {
        val payload = "dummy-model-binary-data".toByteArray()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", payload.size.toString())
                .setBody(okio.Buffer().write(payload))
        )

        val repo = LocalModelRepository(context, okHttpClient)
        assertEquals(LocalModelState.NotInstalled, repo.state.value)

        val url = mockWebServer.url("/model.task").toString()
        repo.install(url, modelId = "gemma-3n-E2B-it")

        val state = repo.state.value
        assertTrue(state is LocalModelState.Ready)
        val file = repo.getModelFile("gemma-3n-E2B-it")
        assertEquals(file.absolutePath, (state as LocalModelState.Ready).path)
        assertTrue(file.exists())
        assertEquals(payload.size.toLong(), file.length())
        assertEquals(1, repo.installedModels.value.size)
        assertEquals("gemma-3n-E2B-it", repo.installedModels.value.first().id)
    }

    @Test
    fun install_verifiesSha256Successfully() = runTest {
        val payload = "gemma-test-bytes-content".toByteArray()
        val digest = MessageDigest.getInstance("SHA-256")
        val sha256 = digest.digest(payload).joinToString("") { "%02x".format(it) }

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", payload.size.toString())
                .setBody(okio.Buffer().write(payload))
        )

        val repo = LocalModelRepository(context, okHttpClient)
        repo.install(mockWebServer.url("/model.task").toString(), expectedSha256 = sha256, modelId = "gemma-3n-E2B-it")

        assertTrue(repo.state.value is LocalModelState.Ready)
    }

    @Test
    fun install_failsOnSha256Mismatch() = runTest {
        val payload = "some-payload".toByteArray()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(okio.Buffer().write(payload))
        )

        val repo = LocalModelRepository(context, okHttpClient)
        repo.install(mockWebServer.url("/model.task").toString(), expectedSha256 = "invalid_sha256_hash", modelId = "gemma-3n-E2B-it")

        val state = repo.state.value
        assertTrue(state is LocalModelState.Error)
        assertTrue((state as LocalModelState.Error).message.contains("SHA-256 mismatch"))
        assertTrue(!repo.getModelFile("gemma-3n-E2B-it").exists())
    }

    @Test
    fun deleteModel_removesFileAndSetsNotInstalled() = runTest {
        val repo = LocalModelRepository(context, okHttpClient)
        val file = repo.getModelFile("gemma-3n-E2B-it")
        file.parentFile?.mkdirs()
        file.writeText("existing model content")
        repo.checkInstalledState()

        assertTrue(repo.state.value is LocalModelState.Ready)

        repo.deleteModel("gemma-3n-E2B-it")
        assertEquals(LocalModelState.NotInstalled, repo.state.value)
        assertTrue(!file.exists())
    }

    @Test
    fun multiModel_selectSwitchesActiveModel() = runTest {
        val repo = LocalModelRepository(context, okHttpClient)
        val file1 = repo.getModelFile("gemma-3n-E2B-it")
        val file2 = repo.getModelFile("phi2-cpu")
        file1.parentFile?.mkdirs()
        file1.writeText("gemma content")
        file2.writeText("phi content")
        repo.checkInstalledState()

        assertEquals(2, repo.installedModels.value.size)
        val ready = repo.state.value as LocalModelState.Ready
        assertEquals("gemma-3n-E2B-it", ready.activeModelId)

        repo.selectModel("phi2-cpu")
        val ready2 = repo.state.value as LocalModelState.Ready
        assertEquals("phi2-cpu", ready2.activeModelId)
        assertEquals(file2.absolutePath, ready2.path)
    }
}
