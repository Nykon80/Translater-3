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
                Строго соблюдай правила перевода:
     1. Выводи до трёх вариантов перевода (если возможно), перечисленных в порядке убывания частоты встречаемости.
     2. Определение в скобках ДОЛЖНО быть на исходном языке ($sourceLang), перевод - на целевом языке ($targetLang)
     3. Никогда не смешивай языки! Пример для перевода с $sourceLang на $targetLang:
     4. Вывод оформляй в следующем формате (пример для перевода слова «chat» с французского на русский):
         Кот (animal domestique)
         Чат (communication en ligne)
         Беседа (conversation informelle)
     5. Давай КРАТКИЕ определения в скобках - не более 3-5 слов.
     6. Перед отправкой ответа проверь:
        - Язык перевода: $targetLang
        - Язык определений: $sourceLang
        - Никаких других языков кроме указанных!

     Переведи слово "$text" с $sourceLang на $targetLang:
                """.trimIndent()
            } else {
                """
                Переведи на $targetLang следующий текст, сохраняя структуру абзацев и форматирование:
                - Переводи максимально точно, сохраняя стиль и смысл
                - Выводи ТОЛЬКО перевод, без комментариев, вводных фраз и лишних символов
                - Не добавляй ничего от себя, только перевод
                - Не используй символы ".$" в переводе

                Текст: $text
                """.trimIndent()
            }

            return TranslationRequestDto(
                messages = listOf(
                    Message(
                        role = "system",
                        content = "You are a professional translator. Always provide accurate translations in the requested format. Keep translations concise and direct."
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