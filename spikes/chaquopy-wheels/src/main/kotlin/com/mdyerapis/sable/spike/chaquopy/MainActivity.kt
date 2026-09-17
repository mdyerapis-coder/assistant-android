package com.mdyerapis.sable.spike.chaquopy

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import com.chaquo.python.Python
import java.io.File

/**
 * ADR-012 step 1 spike UI: start Chaquopy (via [com.chaquo.python.android.PyApplication])
 * and dump the import-check JSON. Not shipped in the product app.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val json = runCatching {
            Python.getInstance()
                .getModule("wheel_import_check")
                .callAttr("run")
                .toString()
        }.getOrElse { error ->
            """{"ok":false,"error":${error.message.toJsonString()}}"""
        }
        File(filesDir, "chaquopy-wheel-import-report.json").writeText(json)
        Log.i(TAG, json)

        val textView = TextView(this).apply {
            text = json
            textSize = 12f
            setPadding(32, 48, 32, 48)
            setTextIsSelectable(true)
        }
        setContentView(ScrollView(this).apply { addView(textView) })
    }

    private fun String?.toJsonString(): String {
        val escaped = (this ?: "unknown").replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$escaped\""
    }

    companion object {
        private const val TAG = "ChaquopySpike"
    }
}
