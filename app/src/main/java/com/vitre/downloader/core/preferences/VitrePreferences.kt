package com.vitre.downloader.core.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "vitre_preferences")

class VitrePreferences(private val context: Context) {

    private val KEY_QUALITY = stringPreferencesKey("default_quality")
    private val KEY_AUDIO_FORMAT = stringPreferencesKey("default_audio_format")
    private val KEY_THEME = stringPreferencesKey("app_theme")
    private val KEY_GLASS_INTENSITY = floatPreferencesKey("glass_intensity")
    private val KEY_REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
    private val KEY_ENABLE_AGSL = booleanPreferencesKey("enable_agsl")
    private val KEY_CUSTOM_DOWNLOAD_URI = stringPreferencesKey("custom_download_tree_uri")

    val defaultQuality: Flow<String> = context.dataStore.data.map { it[KEY_QUALITY] ?: "best" }
    val defaultAudioFormat: Flow<String> = context.dataStore.data.map { it[KEY_AUDIO_FORMAT] ?: "mp3" }
    val appTheme: Flow<String> = context.dataStore.data.map { it[KEY_THEME] ?: "oled_dark" }
    val glassIntensity: Flow<Float> = context.dataStore.data.map { it[KEY_GLASS_INTENSITY] ?: 0.7f }
    val reducedMotion: Flow<Boolean> = context.dataStore.data.map { it[KEY_REDUCED_MOTION] ?: false }
    val enableAgsl: Flow<Boolean> = context.dataStore.data.map { it[KEY_ENABLE_AGSL] ?: true }
    val customDownloadUri: Flow<String?> = context.dataStore.data.map { it[KEY_CUSTOM_DOWNLOAD_URI] }

    suspend fun setQuality(quality: String) {
        context.dataStore.edit { it[KEY_QUALITY] = quality }
    }

    suspend fun setAudioFormat(format: String) {
        context.dataStore.edit { it[KEY_AUDIO_FORMAT] = format }
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { it[KEY_THEME] = theme }
    }

    suspend fun setGlassIntensity(intensity: Float) {
        context.dataStore.edit { it[KEY_GLASS_INTENSITY] = intensity }
    }

    suspend fun setReducedMotion(reduced: Boolean) {
        context.dataStore.edit { it[KEY_REDUCED_MOTION] = reduced }
    }

    suspend fun setEnableAgsl(enable: Boolean) {
        context.dataStore.edit { it[KEY_ENABLE_AGSL] = enable }
    }

    suspend fun setCustomDownloadPath(uriString: String?) {
        context.dataStore.edit {
            if (uriString != null) {
                it[KEY_CUSTOM_DOWNLOAD_URI] = uriString
            } else {
                it.remove(KEY_CUSTOM_DOWNLOAD_URI)
            }
        }
    }
}
