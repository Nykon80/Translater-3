package com.example.translator.data.remote.dto

import com.google.gson.annotations.SerializedName
import java.util.Locale

/**
 * DTO для запроса перевода
 */
data class TranslationRequestDto(
    val model: String = "qwen-plus",
    val messages: List<Message>
) {
    data class Message(
        val role: String,
        val content: String
    )

    companion object {
        fun create(text: String, sourceLang: String, targetLang: String): TranslationRequestDto {
            val isWord = text.trim().matches(Regex("^[\\p{L}'-]+$"))
            
            // Получаем язык системы для пояснений в скобках
            val systemLanguage = Locale.getDefault().language
            
            val prompt = if (isWord) {
                """
                Translate the word '$text' from $sourceLang to $targetLang.
                For words, provide multiple translations with brief context or explanations in parentheses.
                Important: 
                - Always provide the context/explanations in parentheses in ${getLanguageName(systemLanguage)} language, NOT in $targetLang.
                - Keep explanations in parentheses brief (2-3 words max).
                - Format each translation on a new line like this:
                translation1 (brief context in ${getLanguageName(systemLanguage)})
                translation2 (brief context in ${getLanguageName(systemLanguage)})
                Only return the translations without any additional text or explanations outside of the specified format.
                """.trimIndent()
            } else {
                """
                Translate the following text from $sourceLang to $targetLang.
                Return only the translation without any additional text or explanations.
                Text to translate: $text
                """.trimIndent()
            }

            return TranslationRequestDto(
                messages = listOf(
                    Message(
                        role = "system",
                        content = "You are a professional translator. Always provide accurate translations in the requested format. For words, always provide brief context/explanations in parentheses in the specified language, not in the target language."
                    ),
                    Message(
                        role = "user",
                        content = prompt
                    )
                )
            )
        }
        
        /**
         * Получает полное название языка по коду
         */
        private fun getLanguageName(languageCode: String): String {
            return when (languageCode) {
                "ru" -> "Russian"
                "en" -> "English"
                "fr" -> "French"
                "de" -> "German"
                "es" -> "Spanish"
                "it" -> "Italian"
                "pl" -> "Polish"
                "uk" -> "Ukrainian"
                else -> "English" // По умолчанию английский
            }
        }
    }
} 