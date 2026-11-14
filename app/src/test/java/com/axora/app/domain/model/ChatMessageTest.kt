package com.axora.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageTest {

    @Test
    fun userMessageIsUser() {
        val message = ChatMessage(role = Role.USER, text = "кот")
        assertTrue(message.isUser())
        assertFalse(message.isAssistant())
    }

    @Test
    fun imageMessageDetectedByUrl() {
        val message = ChatMessage(role = Role.ASSISTANT, text = "готово", imageUrl = "/tmp/a.jpg")
        assertTrue(message.isImageMessage())
        assertFalse(message.isVideoMessage())
        assertFalse(message.isAudioMessage())
    }

    @Test
    fun emptyUrlIsNotMedia() {
        val message = ChatMessage(role = Role.ASSISTANT, text = "x", imageUrl = "")
        assertFalse(message.isImageMessage())
    }

    @Test
    fun mediaMessageDetected() {
        val message = ChatMessage(role = Role.ASSISTANT, text = "x", audioUrl = "/tmp/a.mp3")
        assertTrue(message.hasMedia())
    }

    @Test
    fun errorMessageDetected() {
        val message = ChatMessage(role = Role.ASSISTANT, text = "Ошибка: сеть недоступна")
        assertTrue(message.isError())
    }

    @Test
    fun generatedTextStartingWithErrorWordIsNotError() {
        val answer = "Ошибка выжившего — это когнитивное искажение"
        val message = ChatMessage(
            role = Role.ASSISTANT,
            text = answer,
            textContent = answer,
            generationType = GenerationType.TEXT
        )
        assertFalse(message.isError())
    }

    @Test
    fun userMessageIsNotError() {
        val message = ChatMessage(role = Role.USER, text = "Ошибка: что-то")
        assertFalse(message.isError())
    }

    @Test
    fun defaultGenerationTypeIsImage() {
        assertEquals(GenerationType.IMAGE, ChatMessage(role = Role.USER, text = "x").generationType)
    }
}