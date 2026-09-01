# Task 7: SettingsScreen — Cloud Providers & Expanded Local Models

**Files:**
- Modify: `feature/chat/src/main/kotlin/com/mdyerapis/sable/feature/chat/SettingsScreen.kt`
- Create: `feature/localmodel/src/main/assets/models.json`

**Interfaces:**
- Consumes: `SableTheme` tokens, `MonoFontFamily`, `LocalModelRepository`.
- Produces: Cloud Providers section, expanded Local Models with device info, bundled model catalog.

**Steps:**

1. **Create bundled models.json**
   Create `feature/localmodel/src/main/assets/models.json` with at least two GGUF model entries:
   ```json
   [
     {
       "id": "qwen2.5-1.5b-instruct-q4",
       "name": "Qwen2.5 1.5B Instruct Q4",
       "hfUrl": "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF",
       "sizeBytes": 1000000000,
       "quantization": "Q4_K_M",
       "minRamBytes": 3000000000,
       "recommended": true
     },
     {
       "id": "phi-3-mini-4k-q4",
       "name": "Phi-3 Mini 4K Q4",
       "hfUrl": "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf",
       "sizeBytes": 2200000000,
       "quantization": "Q4_K_M",
       "minRamBytes": 5000000000,
       "recommended": false
     }
   ]
   ```

2. **Add Cloud Providers section to SettingsScreen**
   Add a new `Card` section above Local Models in the settings list:
   ```kotlin
   Card(
       shape = RoundedCornerShape(20.dp),
       colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
       modifier = Modifier.fillMaxWidth()
   ) {
       Column(modifier = Modifier.padding(16.dp)) {
           Text("Cloud API Keys", style = MaterialTheme.typography.titleMedium)
           Spacer(Modifier.height(8.dp))
           // List providers with status dots
           ProviderRow("OpenAI", hasKey = true)
           ProviderRow("Anthropic", hasKey = false)
           ProviderRow("Google", hasKey = true)
           Spacer(Modifier.height(8.dp))
           TextButton(onClick = { /* open add provider form */ }) {
               Text("Add provider")
           }
       }
   }
   ```
   - `ProviderRow` is a composable that shows a label and a status dot (green = key present, gray = key missing).
   - Colors from `MaterialTheme.colorScheme` exclusively — no hardcoded hex.

3. **Add Device info card**
   ```kotlin
   Card(...) {
       Column(modifier = Modifier.padding(16.dp)) {
           Text("Device info", style = MaterialTheme.typography.titleMedium)
           Spacer(Modifier.height(8.dp))
           InfoRow("RAM", "${totalRamGb} GB", MonoFontFamily)
           InfoRow("SoC", Build.SOC_MODEL, MonoFontFamily)
           InfoRow("Available", "${availableStorageGb} GB", MonoFontFamily)
       }
   }
   ```
   - `InfoRow` shows a label-value pair with `MonoFontFamily` for the data value.
   - If `MonoFontFamily` is not accessible, fall back to `FontFamily.Monospace`.
   - RAM and storage values derived from `ActivityManager.MemoryInfo` and `Context.getExternalFilesDirs()`.

4. **Add panel-line dividers between sections**
   Same `HorizontalDivider` pattern as ChatScreen:
   ```kotlin
   HorizontalDivider(
       color = MaterialTheme.colorScheme.outlineVariant,
       thickness = 1.dp
   )
   ```

5. **Verify builds**
   Run: `./gradlew testDebugUnitTest --no-daemon -q && ./gradlew :app:assembleDebug --no-daemon -q`
   Expected: both green.

6. **Commit**
   ```bash
   git add feature/chat/src/main/kotlin/com/mdyerapis/sable/feature/chat/SettingsScreen.kt feature/localmodel/src/main/assets/models.json
   git commit -m "feat: SettingsScreen — Cloud Providers, Device info, bundled models.json"
   ```