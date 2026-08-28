package com.mdyerapis.assistant.feature.localmodel

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
    private var activeInference: LlmInference? = null

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
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(state.path)
                .build()

            val instance = LlmInference.createFromOptions(context, options)
            activeInference = instance
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
            activeInference = null
        }
    }

    open fun cancel() {
        activeInference = null
    }
}
