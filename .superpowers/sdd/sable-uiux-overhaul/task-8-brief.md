# Task 8: HF API Model Refresh

**Files:**
- Modify: `feature/localmodel/src/main/kotlin/com/mdyerapis/sable/feature/localmodel/LocalModelRepository.kt`

**Interfaces:**
- Consumes: bundled `models.json`, device RAM/SoC info.
- Produces: `refreshAvailableModels()` method that queries HF API, merges with bundled, filters by device capability.

**Steps:**

1. **Add HF API query method**
   Add to `LocalModelRepository`:
   ```kotlin
   suspend fun refreshAvailableModels(): List<LocalModelSpec> {
       val bundled = loadBundledModels()
       return try {
           val hfModels = fetchHfModels()
           val merged = (bundled + hfModels).distinctBy { it.id }
           merged.filter { it.minRamBytes <= deviceRamBytes }
       } catch (e: Exception) {
           bundled.filter { it.minRamBytes <= deviceRamBytes }
       }
   }

   private suspend fun fetchHfModels(): List<LocalModelSpec> {
       // GET https://huggingface.co/api/models?filter=gguf&sort=downloads&limit=20
       // Parse response, filter for mobile/llama.cpp compatible, map to LocalModelSpec
       val request = Request.Builder()
           .url("https://huggingface.co/api/models?filter=gguf&sort=downloads&limit=20")
           .build()
       val response = okHttpClient.newCall(request).execute()
       if (!response.isSuccessful) throw RuntimeException("HF API failed")
       val json = JSONObject(response.body?.string() ?: "[]")
       val models = mutableListOf<LocalModelSpec>()
       val items = json.getJSONArray("models")
       for (i in 0 until items.length()) {
           val item = items.getJSONObject(i)
           val id = item.getString("id")
           val name = item.getString("name")
           // Extract GGUF filename from tag or filename field
           val ggufFile = extractGgufFile(item)
           models.add(LocalModelSpec(
               id = id,
               name = name,
               defaultUrl = "https://huggingface.co/$id/resolve/main/$ggufFile",
               sizeLabel = /* compute from headers */ "",
               isUncensored = false,
               category = "GENERAL",
           ))
       }
       return models
   }

   private fun extractGgufFile(item: JSONObject): String {
       return if (item.has("gguf")) item.getString("gguf") else
              if (item.has("filename")) item.getString("filename") else "model.gguf"
   }
   ```

2. **Add device RAM detection**
   ```kotlin
   private val deviceRamBytes: Long
       get() {
           val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
           val memInfo = ActivityManager.MemoryInfo()
           activityManager.getMemoryInfo(memInfo)
           return memInfo.totalMem
       }
   ```

3. **Update SettingsScreen to show refresh state**
   Add "Updated just now" / "Offline — showing cached" text below "Available for your device" header in the Local Models section.
   - If `refreshAvailableModels()` succeeded recently, show "Updated just now".
   - If offline or failed, show "Offline — showing cached models".

4. **Verify builds**
   Run: `./gradlew testDebugUnitTest --no-daemon -q && ./gradlew :app:assembleDebug --no-daemon -q`
   Expected: both green.

5. **Commit**
   ```bash
   git add feature/localmodel/src/main/kotlin/com/mdyerapis/sable/feature/localmodel/LocalModelRepository.kt
   git commit -m "feat: HF API model refresh with device RAM filtering"
   ```