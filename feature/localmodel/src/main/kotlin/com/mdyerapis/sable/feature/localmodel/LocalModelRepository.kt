package com.mdyerapis.sable.feature.localmodel

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class LocalModelRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    val availableSpecs: List<LocalModelSpec> = listOf(
        // Preset entries — verified MediaPipe-compatible downloads
        LocalModelSpec(
            id = "gemma-3n-E2B-it",
            name = "Gemma 3n E2B IT (int4)",
            description = "Google Gemma 3n mobile-optimized int4 MediaPipe-compatible on-device model. (Verified download: abhianand1093/llminferencedemo)",
            defaultUrl = "https://huggingface.co/abhianand1093/llminferencedemo/resolve/main/gemma-3n-E2B-it-int4.task",
            sizeLabel = "~2.9 GB",
            isUncensored = false,
            category = "GENERAL",
        ),
        LocalModelSpec(
            id = "phi2-cpu",
            name = "Phi-2 2.7B (CPU)",
            description = "Microsoft Phi-2 reasoning on-device model. (Verified download: siddhantchalke/phi2-cpu-mediapipe-llm-inference)",
            defaultUrl = "https://huggingface.co/siddhantchalke/phi2-cpu-mediapipe-llm-inference/resolve/main/phi2_cpu.bin",
            sizeLabel = "~2.6 GB",
            isUncensored = false,
            category = "GENERAL",
        ),

        LocalModelSpec(
            id = "custom",
            name = "Custom Model (.task / .bin)",
            description = "Bring your own MediaPipe-compatible on-device model.",
            defaultUrl = "",
            sizeLabel = "user-defined",
            isUncensored = false,
            category = "CUSTOM",
        ),
    )

    private val prefs = context.getSharedPreferences("local_models_prefs", Context.MODE_PRIVATE)

    private val downloadClient: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _installedModels = MutableStateFlow<List<LocalModelInfo>>(emptyList())
    val installedModels: StateFlow<List<LocalModelInfo>> = _installedModels.asStateFlow()

    private val _state = MutableStateFlow<LocalModelState>(LocalModelState.NotInstalled)
    val state: StateFlow<LocalModelState> = _state.asStateFlow()

    private val _downloadState = MutableStateFlow<LocalModelDownloadState>(LocalModelDownloadState.Idle)
    val downloadState: StateFlow<LocalModelDownloadState> = _downloadState.asStateFlow()

    val modelDir: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    init {
        checkInstalledState()
    }

    fun getModelFile(id: String): File = File(modelDir, "$id.task")

    fun checkInstalledState() {
        val selectedId = prefs.getString("selected_local_model_id", null)
        val list = mutableListOf<LocalModelInfo>()

        // Check catalog specs
        for (spec in availableSpecs) {
            val file = getModelFile(spec.id)
            if (file.exists() && file.length() > 0) {
                list.add(
                    LocalModelInfo(
                        id = spec.id,
                        name = spec.name,
                        path = file.absolutePath,
                        fileSizeBytes = file.length(),
                        isSelected = spec.id == selectedId,
                    )
                )
            }
        }

        // Check any extra custom model files on disk
        modelDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".task")) {
                val id = file.nameWithoutExtension
                if (list.none { it.id == id }) {
                    list.add(
                        LocalModelInfo(
                            id = id,
                            name = id.replace("-", " ").replaceFirstChar { it.uppercase() },
                            path = file.absolutePath,
                            fileSizeBytes = file.length(),
                            isSelected = id == selectedId,
                        )
                    )
                }
            }
        }

        // If nothing is selected but we have models, select the first one
        if (list.isNotEmpty() && list.none { it.isSelected }) {
            val first = list.first()
            prefs.edit().putString("selected_local_model_id", first.id).apply()
            val updated = list.map { it.copy(isSelected = it.id == first.id) }
            _installedModels.value = updated
            _state.value = LocalModelState.Ready(first.path, first.id)
        } else {
            _installedModels.value = list
            val selected = list.firstOrNull { it.isSelected }
            if (selected != null) {
                _state.value = LocalModelState.Ready(selected.path, selected.id)
            } else {
                _state.value = LocalModelState.NotInstalled
            }
        }
    }

    fun selectModel(id: String) {
        val target = _installedModels.value.firstOrNull { it.id == id } ?: return
        prefs.edit().putString("selected_local_model_id", id).apply()
        _installedModels.value = _installedModels.value.map { it.copy(isSelected = it.id == id) }
        _state.value = LocalModelState.Ready(target.path, target.id)
    }

    suspend fun install(
        modelUrl: String,
        expectedSha256: String? = null,
        modelId: String = "gemma-3n-E2B-it"
    ) = withContext(Dispatchers.IO) {
        if (modelUrl.isBlank()) {
            val err = "Please enter a valid model download URL"
            _state.value = LocalModelState.Error(err)
            _downloadState.value = LocalModelDownloadState.Error(modelId, err)
            return@withContext
        }

        _state.value = LocalModelState.Downloading(0f)
        _downloadState.value = LocalModelDownloadState.Downloading(modelId, 0f)

        val targetFile = getModelFile(modelId)
        val tempFile = File(modelDir, "$modelId.task.tmp")

        try {
            val request = Request.Builder().url(modelUrl.trim()).build()
            val response = downloadClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val err = "Download failed: HTTP ${response.code}"
                _state.value = LocalModelState.Error(err)
                _downloadState.value = LocalModelDownloadState.Error(modelId, err)
                return@withContext
            }

            val body = response.body ?: run {
                val err = "Empty response body"
                _state.value = LocalModelState.Error(err)
                _downloadState.value = LocalModelDownloadState.Error(modelId, err)
                return@withContext
            }

            val totalBytes = body.contentLength()
            val digest = if (expectedSha256 != null) MessageDigest.getInstance("SHA-256") else null

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        digest?.update(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        if (totalBytes > 0) {
                            val progress = (totalRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            _state.value = LocalModelState.Downloading(progress)
                            _downloadState.value = LocalModelDownloadState.Downloading(modelId, progress)
                        }
                    }
                }
            }

            if (expectedSha256 != null && digest != null) {
                val hex = digest.digest().joinToString("") { "%02x".format(it) }
                if (!hex.equals(expectedSha256.trim(), ignoreCase = true)) {
                    tempFile.delete()
                    val err = "SHA-256 mismatch: expected $expectedSha256, got $hex"
                    _state.value = LocalModelState.Error(err)
                    _downloadState.value = LocalModelDownloadState.Error(modelId, err)
                    return@withContext
                }
            }

            if (tempFile.renameTo(targetFile) || (tempFile.copyTo(targetFile, overwrite = true).also { tempFile.delete() }).exists()) {
                _downloadState.value = LocalModelDownloadState.Completed(modelId)
                checkInstalledState()
                selectModel(modelId)
            } else {
                val err = "Failed to finalize downloaded model file"
                _state.value = LocalModelState.Error(err)
                _downloadState.value = LocalModelDownloadState.Error(modelId, err)
            }
        } catch (exc: Exception) {
            tempFile.delete()
            val err = "Download failed: ${exc.message ?: exc.toString()}"
            _state.value = LocalModelState.Error(err)
            _downloadState.value = LocalModelDownloadState.Error(modelId, err)
        }
    }

    fun deleteModel(id: String) {
        val file = getModelFile(id)
        if (file.exists()) {
            file.delete()
        }
        checkInstalledState()
    }

    /** Bundled-and-verified presets only — no API scraping; HF has no usable
     *  MediaPipe `.task`/`.bin` catalog and the phone's engine can't run GGUF. */
    suspend fun refreshAvailableModels(): List<LocalModelSpec> = availableSpecs

    private fun getDeviceRamBytes(): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        return memInfo.totalMem
    }
}