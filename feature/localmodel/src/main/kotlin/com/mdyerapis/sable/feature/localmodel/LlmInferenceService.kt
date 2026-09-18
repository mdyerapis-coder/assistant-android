package com.mdyerapis.sable.feature.localmodel

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class LlmInferenceService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localModelRepository: LocalModelRepository
) {
    @Volatile
    private var cachedInference: LlmInference? = null

    @Volatile
    private var cachedPath: String? = null

    open suspend fun generate(
        prompt: String,
        replaceInput: Boolean = false,
        onPartial: suspend (String) -> Unit
    ): String = withContext(Dispatchers.Default) {
        val state = localModelRepository.state.value
        if (state !is LocalModelState.Ready) {
            return@withContext "Error: Local model is not installed or ready."
        }

        val channel = Channel<String>(Channel.UNLIMITED)
        val consumerJob = launch {
            for (token in channel) {
                onPartial(token)
            }
        }

        try {
            val instance = instanceFor(state.path)
            val future = instance.generateResponseAsync(
                prompt,
                ProgressListener<String> { partialResult, _ ->
                    if (!partialResult.isNullOrEmpty()) {
                        channel.trySend(partialResult)
                    }
                },
            )
            // Block until the whole turn completes. Streaming is delivered via the channel.
            future.get()
        } catch (exc: Exception) {
            "Error running local inference: ${exc.message ?: exc.toString()}"
        } finally {
            channel.close()
            consumerJob.join()
        }
    }

    open fun cancel() {
        releaseEngine()
    }

    @Synchronized
    private fun instanceFor(path: String): LlmInference {
        val hit = cachedInference
        if (hit != null && cachedPath == path) return hit
        closeQuietly(hit)
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(path)
            .build()
        val created = LlmInference.createFromOptions(context, options)
        cachedInference = created
        cachedPath = path
        return created
    }

    @Synchronized
    private fun releaseEngine() {
        closeQuietly(cachedInference)
        cachedInference = null
        cachedPath = null
    }

    private fun closeQuietly(instance: LlmInference?) {
        if (instance == null) return
        try {
            instance.close()
        } catch (_: Exception) {
            // Engine close is best-effort; a later createFromOptions recovers.
        }
    }
}
