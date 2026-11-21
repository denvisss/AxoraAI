package com.axora.app.ui.viewmodel

import com.axora.app.R
import com.axora.app.data.repository.ChatRepository
import com.axora.app.domain.model.ChatMessage
import com.axora.app.domain.model.GenerationType
import com.axora.app.domain.model.ImageFormat
import com.axora.app.domain.model.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeRepository : ChatRepository {
        val inserted = mutableListOf<ChatMessage>()
        val deletedIds = mutableListOf<Long>()
        val autoSaved = mutableListOf<ChatMessage>()
        val deletedMedia = mutableListOf<ChatMessage>()
        val history = MutableStateFlow<List<ChatMessage>>(emptyList())
        var failWith: Exception? = null
        var hangForever = false
        var textAnswer = "ответ модели"
        var lastTextHistory: List<ChatMessage> = emptyList()
        var format = ImageFormat.SQUARE

        override fun getChatHistory(type: GenerationType): Flow<List<ChatMessage>> = history

        override suspend fun insertMessage(message: ChatMessage): Long {
            inserted += message
            return inserted.size.toLong()
        }

        override suspend fun updateMessage(messageId: Long, newText: String, newOriginal: String?) {}

        override suspend fun deleteMessage(messageId: Long) {
            deletedIds += messageId
        }

        override suspend fun clearChat(type: GenerationType) {}

        override suspend fun generateContent(prompt: String, type: GenerationType): File {
            if (hangForever) suspendCancellableCoroutine<Unit> { }
            failWith?.let { throw it }
            return File("generated.jpg")
        }

        override suspend fun generateText(prompt: String, history: List<ChatMessage>): String {
            lastTextHistory = history
            failWith?.let { throw it }
            return textAnswer
        }

        override suspend fun editImage(source: File, instruction: String): File {
            failWith?.let { throw it }
            return File("edited.jpg")
        }

        override suspend fun translateToEnglish(text: String): String = text

        override suspend fun autoSaveToGallery(message: ChatMessage) {
            autoSaved += message
        }

        override suspend fun deleteMediaOf(message: ChatMessage) {
            deletedMedia += message
        }

        override fun getImageFormat(): ImageFormat = format

        override fun setImageFormat(format: ImageFormat) {
            this.format = format
        }
    }

    private lateinit var repository: FakeRepository
    private lateinit var viewModel: ChatViewModel

    private val strings = StringResolver { resId, _ -> "res:$resId" }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeRepository()
        viewModel = ChatViewModel(repository, strings)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun sendMessageInsertsUserAndAssistantMessages() = runTest(dispatcher) {
        viewModel.sendMessage("кот")
        advanceUntilIdle()

        assertEquals(2, repository.inserted.size)
        val user = repository.inserted[0]
        assertEquals(Role.USER, user.role)
        assertEquals("кот", user.text)
        assertEquals("кот", user.originalPrompt)
        val assistant = repository.inserted[1]
        assertEquals(Role.ASSISTANT, assistant.role)
        assertEquals(File("generated.jpg").absolutePath, assistant.imageUrl)
        assertEquals("кот", assistant.originalPrompt)
        assertFalse(assistant.isError)
        assertEquals(listOf(assistant), repository.autoSaved)
        assertTrue(viewModel.uiState.value.generatingTypes.isEmpty())
    }

    @Test
    fun blankPromptIsIgnored() = runTest(dispatcher) {
        viewModel.sendMessage("   ")
        advanceUntilIdle()
        assertTrue(repository.inserted.isEmpty())
    }

    @Test
    fun offlineErrorInsertsFlaggedMessageAndBanner() = runTest(dispatcher) {
        repository.failWith = UnknownHostException("no network")
        viewModel.sendMessage("кот")
        advanceUntilIdle()

        val error = repository.inserted.last()
        assertTrue(error.isError)
        assertEquals("res:${R.string.error_offline}", error.text)
        assertEquals("кот", error.originalPrompt)
        assertEquals(error.text, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.generatingTypes.isEmpty())
    }

    @Test
    fun rateLimitHttpErrorIsMapped() = runTest(dispatcher) {
        repository.failWith = httpError(429, "{\"errors\":[[429,\"rate limited\"]]}")
        viewModel.sendMessage("кот")
        advanceUntilIdle()

        assertEquals("res:${R.string.error_rate_limited}", viewModel.uiState.value.error)
    }

    @Test
    fun invalidKeyHttpErrorIsMapped() = runTest(dispatcher) {
        repository.failWith = httpError(401, "unauthorized")
        viewModel.sendMessage("кот")
        advanceUntilIdle()

        assertEquals("res:${R.string.error_invalid_key}", viewModel.uiState.value.error)
    }

    @Test
    fun serviceUnavailableHttpErrorIsMapped() = runTest(dispatcher) {
        repository.failWith = httpError(503, "{\"status\":503,\"error\":{\"code\":\"SERVICE_UNAVAILABLE\"}}")
        viewModel.sendMessage("кот")
        advanceUntilIdle()

        assertEquals("res:${R.string.error_service_unavailable}", viewModel.uiState.value.error)
    }

    @Test
    fun httpErrorDetailUsesServerMessageFromJson() = runTest(dispatcher) {
        val argStrings = StringResolver { resId, args -> "res:$resId:${args.joinToString("|")}" }
        val vm = ChatViewModel(repository, argStrings)
        repository.failWith = httpError(500, "{\"error\":{\"code\":\"BAD\",\"message\":\"Human readable\"}}")
        vm.sendMessage("кот")
        advanceUntilIdle()

        assertEquals("res:${R.string.error_http_detail}:500|Human readable", vm.uiState.value.error)
    }

    @Test
    fun cancelGenerationStopsJobWithoutResult() = runTest(dispatcher) {
        repository.hangForever = true
        viewModel.sendMessage("кот")
        advanceUntilIdle()
        assertTrue(GenerationType.IMAGE in viewModel.uiState.value.generatingTypes)

        viewModel.cancelGeneration()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.generatingTypes.isEmpty())
        assertEquals(1, repository.inserted.size)
        assertEquals(Role.USER, repository.inserted.single().role)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun regenerateReusesOriginalPromptWithoutUserMessage() = runTest(dispatcher) {
        val source = ChatMessage(
            role = Role.ASSISTANT,
            text = "готово",
            imageUrl = "old.jpg",
            originalPrompt = "рыжий кот"
        )
        viewModel.regenerate(source)
        advanceUntilIdle()

        assertEquals(1, repository.inserted.size)
        assertEquals(Role.ASSISTANT, repository.inserted.single().role)
        assertEquals("рыжий кот", repository.inserted.single().originalPrompt)
    }

    @Test
    fun retryDeletesErrorMessageAndRegenerates() = runTest(dispatcher) {
        val error = ChatMessage(
            id = 7,
            role = Role.ASSISTANT,
            text = "ошибка",
            originalPrompt = "кот",
            isError = true
        )
        viewModel.retry(error)
        advanceUntilIdle()

        assertEquals(listOf(7L), repository.deletedIds)
        assertEquals(1, repository.inserted.size)
        assertEquals(Role.ASSISTANT, repository.inserted.single().role)
    }

    @Test
    fun textGenerationPassesNonErrorHistory() = runTest(dispatcher) {
        viewModel.setGenerationType(GenerationType.TEXT)
        advanceUntilIdle()
        repository.history.value = listOf(
            ChatMessage(id = 1, role = Role.USER, text = "привет", generationType = GenerationType.TEXT),
            ChatMessage(id = 2, role = Role.ASSISTANT, text = "сбой", generationType = GenerationType.TEXT, isError = true)
        )
        advanceUntilIdle()

        viewModel.sendMessage("как дела?")
        advanceUntilIdle()

        assertEquals(listOf(1L), repository.lastTextHistory.map { it.id })
        val assistant = repository.inserted.last()
        assertEquals("ответ модели", assistant.textContent)
        assertEquals(GenerationType.TEXT, assistant.generationType)
    }

    @Test
    fun editImageInsertsInstructionAndEditedResult() = runTest(dispatcher) {
        val source = ChatMessage(role = Role.ASSISTANT, text = "готово", imageUrl = "source.jpg")
        viewModel.editImage(source, "сделай небо закатным")
        advanceUntilIdle()

        assertEquals(2, repository.inserted.size)
        assertEquals(Role.USER, repository.inserted[0].role)
        assertEquals("сделай небо закатным", repository.inserted[0].text)
        val edited = repository.inserted[1]
        assertEquals(File("edited.jpg").absolutePath, edited.imageUrl)
        assertEquals("res:${R.string.image_edited}", edited.text)
    }

    @Test
    fun editImageFailureShowsBannerWithoutErrorBubble() = runTest(dispatcher) {
        repository.failWith = UnknownHostException("no network")
        val source = ChatMessage(role = Role.ASSISTANT, text = "готово", imageUrl = "source.jpg")
        viewModel.editImage(source, "убери фон")
        advanceUntilIdle()

        assertEquals(1, repository.inserted.size)
        assertEquals(Role.USER, repository.inserted.single().role)
        assertEquals("res:${R.string.error_offline}", viewModel.uiState.value.error)
    }

    @Test
    fun imageFormatIsPersistedAndExposed() = runTest(dispatcher) {
        viewModel.setImageFormat(ImageFormat.PORTRAIT)
        advanceUntilIdle()

        assertEquals(ImageFormat.PORTRAIT, viewModel.uiState.value.imageFormat)
        assertEquals(ImageFormat.PORTRAIT, repository.format)
    }

    private fun httpError(code: Int, body: String): HttpException =
        HttpException(
            Response.error<Any>(code, body.toResponseBody("text/plain".toMediaType()))
        )
}