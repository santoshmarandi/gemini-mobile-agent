package com.ai.mobileagent.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "gemini_agent_secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback to standard private preferences if keystore is unavailable in some environments
        context.getSharedPreferences("gemini_agent_prefs", Context.MODE_PRIVATE)
    }

    var geminiApiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var selectedModel: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    var maxSteps: Int
        get() = prefs.getInt(KEY_MAX_STEPS, 15)
        set(value) = prefs.edit().putInt(KEY_MAX_STEPS, value).apply()

    var stepDelayMs: Long
        get() = prefs.getLong(KEY_STEP_DELAY, 1000L)
        set(value) = prefs.edit().putLong(KEY_STEP_DELAY, value).apply()

    var useMultimodalVision: Boolean
        get() = prefs.getBoolean(KEY_USE_VISION, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_VISION, value).apply()

    fun isConfigured(): Boolean = geminiApiKey.isNotBlank()

    companion object {
        private const val KEY_API_KEY = "key_gemini_api_key"
        private const val KEY_MODEL = "key_selected_model"
        private const val KEY_MAX_STEPS = "key_max_steps"
        private const val KEY_STEP_DELAY = "key_step_delay"
        private const val KEY_USE_VISION = "key_use_vision"

        const val MODEL_GEMINI_FLASH_LATEST = "gemini-flash-latest"
        const val MODEL_GEMINI_3_8_FLASH = "gemini-3.8-flash"
        const val MODEL_GEMINI_PRO_LATEST = "gemini-pro-latest"
        const val DEFAULT_MODEL = MODEL_GEMINI_FLASH_LATEST
    }
}
