package com.mdyerapis.sable.feature.chat

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.mdyerapis.sable.core.designsystem.theme.SableTheme
import org.junit.Rule
import org.junit.Test

class SettingsComponentsShotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        theme = "android:Theme.Material.Light.NoActionBar",
        renderingMode = SessionParams.RenderingMode.SHRINK,
    )

    @Test
    fun providerStatusRow_configured() {
        paparazzi.snapshot {
            SableTheme {
                ProviderStatusRow(
                    name = "gemini",
                    note = "Strongest pure-reasoning option, GA since Feb 2026.",
                    configured = true,
                )
            }
        }
    }

    @Test
    fun providerStatusRow_needsKey() {
        paparazzi.snapshot {
            SableTheme {
                ProviderStatusRow(
                    name = "openrouter",
                    note = "Router, not a single model — flexible catch-all/fallback.",
                    configured = false,
                )
            }
        }
    }

    @Test
    fun modelOptionCard_selected() {
        paparazzi.snapshot {
            SableTheme {
                ModelOptionCard(
                    displayName = "Gemini 3.1 Pro",
                    rawModel = "gemini-3.1-pro",
                    isSelected = true,
                    isEnabled = true,
                    onClick = {},
                )
            }
        }
    }

    @Test
    fun modelOptionCard_disabledUnconfigured() {
        paparazzi.snapshot {
            SableTheme {
                ModelOptionCard(
                    displayName = "Claude Sonnet 4.6",
                    rawModel = "anthropic/claude-sonnet-4-6",
                    isSelected = false,
                    isEnabled = false,
                    onClick = {},
                )
            }
        }
    }
}
