package com.example.translator.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "language_preferences")

@Singleton
class LanguagePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sourceLanguageFavorites = stringSetPreferencesKey("source_language_favorites")
    private val targetLanguageFavorites = stringSetPreferencesKey("target_language_favorites")
    private val lastSourceLanguage = stringSetPreferencesKey("last_source_language")
    private val lastTargetLanguage = stringSetPreferencesKey("last_target_language")

    val sourceLanguageFavoritesFlow: Flow<Set<String>> = context.dataStore.data
        .map { preferences -> preferences[sourceLanguageFavorites] ?: emptySet() }

    val targetLanguageFavoritesFlow: Flow<Set<String>> = context.dataStore.data
        .map { preferences -> preferences[targetLanguageFavorites] ?: emptySet() }

    suspend fun toggleSourceLanguageFavorite(languageCode: String) {
        context.dataStore.edit { preferences ->
            val currentFavorites = preferences[sourceLanguageFavorites] ?: emptySet()
            preferences[sourceLanguageFavorites] = if (languageCode in currentFavorites) {
                currentFavorites - languageCode
            } else {
                currentFavorites + languageCode
            }
        }
    }

    suspend fun toggleTargetLanguageFavorite(languageCode: String) {
        context.dataStore.edit { preferences ->
            val currentFavorites = preferences[targetLanguageFavorites] ?: emptySet()
            preferences[targetLanguageFavorites] = if (languageCode in currentFavorites) {
                currentFavorites - languageCode
            } else {
                currentFavorites + languageCode
            }
        }
    }

    suspend fun setLastSourceLanguage(languageCode: String) {
        context.dataStore.edit { preferences ->
            preferences[lastSourceLanguage] = setOf(languageCode)
        }
    }

    suspend fun setLastTargetLanguage(languageCode: String) {
        context.dataStore.edit { preferences ->
            preferences[lastTargetLanguage] = setOf(languageCode)
        }
    }

    suspend fun getSourceLanguageFavorites(): List<String> {
        return context.dataStore.data.map { preferences ->
            preferences[sourceLanguageFavorites]?.toList() ?: emptyList()
        }.first()
    }

    suspend fun getTargetLanguageFavorites(): List<String> {
        return context.dataStore.data.map { preferences ->
            preferences[targetLanguageFavorites]?.toList() ?: emptyList()
        }.first()
    }

    /**
     * Получает последний использованный исходный язык
     */
    suspend fun getLastSourceLanguage(): String? {
        return context.dataStore.data.map { preferences ->
            preferences[lastSourceLanguage]?.firstOrNull()
        }.first()
    }

    /**
     * Получает последний использованный целевой язык
     */
    suspend fun getLastTargetLanguage(): String? {
        return context.dataStore.data.map { preferences ->
            preferences[lastTargetLanguage]?.firstOrNull()
        }.first()
    }
} 