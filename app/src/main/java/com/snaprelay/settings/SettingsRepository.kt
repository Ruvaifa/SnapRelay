package com.snaprelay.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "snaprelay_settings")

class SettingsRepository(private val context: Context) {

    private object PreferenceKeys {
        val BOT_TOKEN = stringPreferencesKey("telegram_bot_token")
        val CHAT_ID = stringPreferencesKey("telegram_chat_id")
        val DELETE_AFTER_UPLOAD = booleanPreferencesKey("delete_after_upload")
        val MAX_UPLOAD_RETRIES = intPreferencesKey("max_upload_retries")
        val ROTATION_DEGREES = intPreferencesKey("rotation_degrees")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            botToken = preferences[PreferenceKeys.BOT_TOKEN] ?: "",
            chatId = preferences[PreferenceKeys.CHAT_ID] ?: "",
            deleteAfterUpload = preferences[PreferenceKeys.DELETE_AFTER_UPLOAD] ?: false,
            maxUploadRetries = preferences[PreferenceKeys.MAX_UPLOAD_RETRIES] ?: 3,
            rotationDegrees = preferences[PreferenceKeys.ROTATION_DEGREES] ?: 0
        )
    }

    suspend fun updateBotToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.BOT_TOKEN] = token.trim()
        }
    }

    suspend fun updateChatId(chatId: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.CHAT_ID] = chatId.trim()
        }
    }

    suspend fun updateDeleteAfterUpload(delete: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.DELETE_AFTER_UPLOAD] = delete
        }
    }

    suspend fun updateRotationDegrees(degrees: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.ROTATION_DEGREES] = degrees
        }
    }
}
