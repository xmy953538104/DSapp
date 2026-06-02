package dev.chungjungsoo.gptmobile.data.repository

import android.content.ContextWrapper
import dev.chungjungsoo.gptmobile.data.context.ContextBuilder
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.MessageRequest
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.MessageResponseChunk
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponsesRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ChatCompletionChunk
import dev.chungjungsoo.gptmobile.data.dto.openai.response.Choice
import dev.chungjungsoo.gptmobile.data.dto.openai.response.Delta
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponsesStreamEvent
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.AnthropicAPI
import dev.chungjungsoo.gptmobile.data.network.OpenAIAPI
import dev.chungjungsoo.gptmobile.data.network.UploadedProviderFile
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatRepositoryImplTest {

    @Test(expected = IllegalStateException::class)
    fun `blank response input without encodable parts throws`() {
        validateResponseInputPartsOrThrow("", 0, 42)
    }

    @Test
    fun `loading is emitted before expensive request preparation finishes`() = runBlocking {
        val firstState = withTimeout(100) {
            streamPreparedApiState(
                prepare = {
                    Thread.sleep(200)
                },
                stream = {
                    flowOf(ApiState.Success("done"))
                }
            ).first()
        }

        assertEquals(ApiState.Loading, firstState)
    }

    @Test
    fun `deepseek v4 reasoning sends thinking toggle and emits reasoning content`() = runBlocking {
        val openAIAPI = RecordingOpenAIAPI(
            flowOf(
                ChatCompletionChunk(
                    choices = listOf(
                        Choice(
                            index = 0,
                            delta = Delta(reasoningContent = "Plan")
                        )
                    )
                ),
                ChatCompletionChunk(
                    choices = listOf(
                        Choice(
                            index = 0,
                            delta = Delta(content = "Answer")
                        )
                    )
                )
            )
        )
        val repository = createRepository(openAIAPI = openAIAPI)

        val states = repository.completeChat(
            userMessages = listOf(MessageV2(content = "Hi", platformType = null)),
            assistantMessages = emptyList(),
            platform = deepSeekPlatform(reasoning = true, model = "deepseek-v4-flash")
        ).toList()

        assertEquals(
            listOf(
                ApiState.Loading,
                ApiState.Thinking("Plan"),
                ApiState.Success("Answer"),
                ApiState.Done
            ),
            states
        )
        assertEquals("enabled", openAIAPI.lastRequest?.thinking?.type)
        assertEquals("high", openAIAPI.lastRequest?.reasoningEffort)
        assertNull(openAIAPI.lastRequest?.temperature)
        assertNull(openAIAPI.lastRequest?.topP)
    }

    @Test
    fun `deepseek legacy aliases switch between chat and reasoner`() {
        val thinkingRequest = createDeepSeekChatCompletionRequest(
            messages = emptyList(),
            platform = deepSeekPlatform(reasoning = true, model = "deepseek-chat")
        )
        val chatRequest = createDeepSeekChatCompletionRequest(
            messages = emptyList(),
            platform = deepSeekPlatform(reasoning = false, model = "deepseek-reasoner")
        )

        assertEquals("deepseek-reasoner", thinkingRequest.model)
        assertNull(thinkingRequest.thinking)
        assertEquals("deepseek-chat", chatRequest.model)
        assertNull(chatRequest.thinking)
    }

    private fun createRepository(
        openAIAPI: OpenAIAPI = RecordingOpenAIAPI()
    ): ChatRepositoryImpl = ChatRepositoryImpl(
        context = ContextWrapper(null),
        chatRoomDao = proxy(),
        messageDao = proxy(),
        chatRoomV2Dao = proxy(),
        messageV2Dao = proxy(),
        chatPlatformModelV2Dao = proxy(),
        chatCompactionPointV2Dao = proxy(),
        settingRepository = proxy(),
        openAIAPI = openAIAPI,
        anthropicAPI = FakeAnthropicAPI(),
        attachmentUploadCoordinator = AttachmentUploadCoordinator(
            openAIAPI,
            FakeAnthropicAPI()
        ),
        contextBuilder = ContextBuilder()
    )

    private fun deepSeekPlatform(reasoning: Boolean, model: String) = PlatformV2(
        uid = "deepseek-platform",
        name = "DeepSeek",
        compatibleType = ClientType.DEEPSEEK,
        apiUrl = "https://api.deepseek.com/",
        model = model,
        temperature = 1.0f,
        topP = 1.0f,
        stream = true,
        reasoning = reasoning
    )

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> proxy(): T {
        val handler = InvocationHandler { _, method, _ ->
            when (method.returnType) {
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                Float::class.javaPrimitiveType -> 0f
                Double::class.javaPrimitiveType -> 0.0
                Unit::class.java -> Unit
                else -> null
            }
        }

        return Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java),
            handler
        ) as T
    }

    private class RecordingOpenAIAPI(
        private val chunks: Flow<ChatCompletionChunk> = emptyFlow()
    ) : OpenAIAPI {
        var lastRequest: ChatCompletionRequest? = null

        override fun setToken(token: String?) = Unit

        override fun setAPIUrl(url: String) = Unit

        override fun streamChatCompletion(request: ChatCompletionRequest, timeoutSeconds: Int): Flow<ChatCompletionChunk> {
            lastRequest = request
            return chunks
        }

        override fun streamResponses(request: ResponsesRequest, timeoutSeconds: Int): Flow<ResponsesStreamEvent> = emptyFlow()

        override suspend fun uploadFile(filePath: String, fileName: String, mimeType: String): UploadedProviderFile =
            UploadedProviderFile(id = "file-id", mimeType = mimeType)

        override suspend fun isFileAvailable(fileId: String): Boolean = false
    }

    private class FakeAnthropicAPI : AnthropicAPI {
        override fun setToken(token: String?) = Unit

        override fun setAPIUrl(url: String) = Unit

        override fun streamChatMessage(messageRequest: MessageRequest, timeoutSeconds: Int): Flow<MessageResponseChunk> = emptyFlow()

        override suspend fun uploadFile(filePath: String, fileName: String, mimeType: String): UploadedProviderFile =
            UploadedProviderFile(id = "anthropic-file", mimeType = mimeType)

        override suspend fun isFileAvailable(fileId: String): Boolean = false
    }
}
