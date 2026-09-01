package com.mdyerapis.sable.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mdyerapis.sable.core.designsystem.components.LoadingIndicator
import com.mdyerapis.sable.feature.onboarding.R as OnboardingR

@Composable
fun OnboardingScreen(
    onTokenAccepted: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.isDone) {
        if (uiState.isDone) {
            android.media.MediaPlayer.create(context, OnboardingR.raw.startup_whistle)?.apply {
                setOnCompletionListener { release() }
                start()
            }
            onTokenAccepted()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = uiState.currentFrame,
            transitionSpec = {
                (slideInHorizontally(tween(400, easing = EaseInOut)) { it } + fadeIn()) togetherWith
                    (slideOutHorizontally(tween(400, easing = EaseInOut)) { -it } + fadeOut())
            },
            label = "onboardingFrame"
        ) { frame ->
            when (frame) {
                0 -> StoryFrame(text = "Sable stays quiet.", emberIntensity = 0.15f) { viewModel.nextFrame() }
                1 -> StoryFrame(text = "Sable remembers.", emberIntensity = 0.55f) { viewModel.nextFrame() }
                2 -> StoryFrame(text = "Sable acts when you ask.", emberIntensity = 1f) { viewModel.nextFrame() }
                3 -> ConnectForm(uiState, viewModel)
            }
        }

        if (uiState.currentFrame < 3) {
            TextButton(
                onClick = { viewModel.skipToConnect() },
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Frame progress dots
        if (uiState.currentFrame < 3) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(3) { i ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (i <= uiState.currentFrame) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                                CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryFrame(text: String, emberIntensity: Float, onTap: () -> Unit) {
    // Droid breathing animation
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathScale"
    )

    // Pulsing ember glow behind the droid — intensity grows per frame
    val emberGlow by infiniteTransition.animateFloat(
        initialValue = emberIntensity * 0.6f,
        targetValue = emberIntensity,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emberGlow"
    )

    // Precompute gradient colors (can't call composable in drawBehind)
    val emberColor = MaterialTheme.colorScheme.primary
    val transparent = Color.Transparent

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onTap() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .drawBehind {
                    val radius = size.minDimension / 1.4f
                    // Layered ember halos for depth
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                emberColor.copy(alpha = emberGlow),
                                transparent
                            ),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = radius
                        ),
                        radius = radius,
                        center = Offset(size.width / 2f, size.height / 2f)
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                emberColor.copy(alpha = emberGlow * 0.5f),
                                transparent
                            ),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = radius * 0.6f
                        ),
                        radius = radius * 0.6f,
                        center = Offset(size.width / 2f, size.height / 2f)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(OnboardingR.drawable.ic_launcher_foreground),
                contentDescription = "Sable",
                modifier = Modifier
                    .size(140.dp)
                    .graphicsLayer {
                        scaleX = breathScale
                        scaleY = breathScale
                    }
            )
        }

        Spacer(Modifier.height(48.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Tap to continue",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ConnectForm(
    uiState: OnboardingUiState,
    viewModel: OnboardingViewModel,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Droid with steady ember glow
        val emberColor = MaterialTheme.colorScheme.primary
        val transparent = Color.Transparent
        Box(
            modifier = Modifier
                .size(120.dp)
                .drawBehind {
                    val radius = size.minDimension / 1.4f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(emberColor.copy(alpha = 0.25f), transparent),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = radius
                        ),
                        radius = radius,
                        center = Offset(size.width / 2f, size.height / 2f)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(OnboardingR.drawable.ic_launcher_foreground),
                contentDescription = "Sable",
                modifier = Modifier.size(72.dp)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Sable",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Quietly capable.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = uiState.baseUrl,
                    onValueChange = { viewModel.updateBaseUrl(it) },
                    label = { Text("Server URL") },
                    placeholder = { Text("https://assistant.llmclouds.au") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )

                OutlinedTextField(
                    value = uiState.token,
                    onValueChange = { viewModel.updateToken(it) },
                    label = { Text("Bearer Token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )

                uiState.error?.let { error ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Button(
                    onClick = { viewModel.submit() },
                    enabled = !uiState.isLoading && uiState.token.isNotBlank() && uiState.baseUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (uiState.isLoading) {
                        LoadingIndicator()
                    } else {
                        Text("Wake up Sable", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
