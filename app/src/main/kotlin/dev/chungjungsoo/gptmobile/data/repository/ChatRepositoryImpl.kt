package dev.chungjungsoo.gptmobile.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.data.context.ContextBuilder
import dev.chungjungsoo.gptmobile.data.context.ConversationTurn
import dev.chungjungsoo.gptmobile.data.context.ProviderContextPolicy
import dev.chungjungsoo.gptmobile.data.database.dao.ChatCompactionPointV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatPlatformModelV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomDao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageDao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageV2Dao
import dev.chungjungsoo.gptmobile.data.database.entity.ChatCompactionPointV2
import dev.chungjungsoo.gptmobile.data.database.entity.ChatPlatformModelV2
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.Message
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveContent
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.ImageContent as AnthropicImageContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.ImageSource
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.MediaType
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.MessageContent as AnthropicMessageContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.MessageRole
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.TextContent as AnthropicTextContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.InputMessage
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.MessageRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.common.ImageContent as OpenAIImageContent
import dev.chungjungsoo.gptmobile.data.dto.openai.common.ImageUrl
import dev.chungjungsoo.gptmobile.data.dto.openai.common.MessageContent as OpenAIMessageContent
import dev.chungjungsoo.gptmobile.data.dto.openai.common.Role as OpenAIRole
import dev.chungjungsoo.gptmobile.data.dto.openai.common.TextContent as OpenAITextContent
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatMessage
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ReasoningConfig
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseContentPart
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseInputContent
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseInputMessage
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponsesRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ThinkingConfig
import dev.chungjungsoo.gptmobile.data.dto.openai.response.OutputTextDeltaEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ReasoningSummaryTextDeltaEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseErrorEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseFailedEvent
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.AnthropicAPI
import dev.chungjungsoo.gptmobile.data.network.OpenAIAPI
import dev.chungjungsoo.gptmobile.util.AppLogger
import dev.chungjungsoo.gptmobile.util.AttachmentPayloadCache
import dev.chungjungsoo.gptmobile.util.ChatTokenStats
import dev.chungjungsoo.gptmobile.util.FileUtils
import dev.chungjungsoo.gptmobile.util.TokenUsageStats
import dev.chungjungsoo.gptmobile.util.estimateMessagesTokens
import dev.chungjungsoo.gptmobile.util.estimateTextTokens
import dev.chungjungsoo.gptmobile.util.stripAssistantErrorNote
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext

private const val AUTO_CONTEXT_COMPRESSION_MIN_OMITTED_TURNS = 2
private const val AUTO_CONTEXT_COMPRESSION_MAX_MESSAGE_CHARS = 1200
private const val AUTO_CONTEXT_COMPRESSION_TRIGGER_TOKENS = 6000
private const val AUTO_CONTEXT_COMPRESSION_SUMMARY_MAX_TOKENS = 1200

private data class PreparedContext(
    val turns: List<ConversationTurn>,
    val systemPrompt: String?
)

class ChatRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val chatRoomDao: ChatRoomDao,
    private val messageDao: MessageDao,
    private val chatRoomV2Dao: ChatRoomV2Dao,
    private val messageV2Dao: MessageV2Dao,
    private val chatPlatformModelV2Dao: ChatPlatformModelV2Dao,
    private val chatCompactionPointV2Dao: ChatCompactionPointV2Dao,
    private val settingRepository: SettingRepository,
    private val openAIAPI: OpenAIAPI,
    private val anthropicAPI: AnthropicAPI,
    private val attachmentUploadCoordinator: AttachmentUploadCoordinator,
    private val contextBuilder: ContextBuilder
) : ChatRepository {

    private fun isImageFile(extension: String): Boolean = extension in setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff", "svg")

    private fun isDocumentFile(extension: String): Boolean = extension in setOf("pdf", "txt", "doc", "docx", "xls", "xlsx")

    private fun getMimeType(extension: String): String = when (extension) {
        // Images
        "jpg", "jpeg" -> "image/jpeg"

        "png" -> "image/png"

        "gif" -> "image/gif"

        "bmp" -> "image/bmp"

        "webp" -> "image/webp"

        "tiff" -> "image/tiff"

        "svg" -> "image/svg+xml"

        // Documents
        "pdf" -> "application/pdf"

        "txt" -> "text/plain"

        "doc" -> "application/msword"

        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

        "xls" -> "application/vnd.ms-excel"

        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

        else -> "application/octet-stream"
    }

    override suspend fun completeChat(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2
    ): Flow<ApiState> = when (platform.compatibleType) {
        ClientType.OPENAI -> {
            // Use Responses API for OpenAI (supports reasoning/thinking)
            completeChatWithOpenAIResponses(userMessages, assistantMessages, platform)
        }

        ClientType.DEEPSEEK -> {
            completeChatWithDeepSeek(userMessages, assistantMessages, platform)
        }

        ClientType.CUSTOM -> {
            // Use Chat Completions API for OpenAI-compatible services
            completeChatWithOpenAIChatCompletions(userMessages, assistantMessages, platform)
        }

        ClientType.ANTHROPIC -> {
            completeChatWithAnthropic(userMessages, assistantMessages, platform)
        }

    }

    private suspend fun completeChatWithDeepSeek(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2
    ): Flow<ApiState> = try {
        openAIAPI.setToken(platform.token)
        openAIAPI.setAPIUrl(platform.apiUrl)

        streamPreparedApiState(
            prepare = {
                val normalizedPlatform = platform.withDeepSeekThinkingModel()
                val preparedContext = prepareContextForCompletion(userMessages, assistantMessages, normalizedPlatform)
                val contextTurns = preparedContext.turns
                validateInlineBudgetIfNeeded(contextTurns, normalizedPlatform)
                val messages = buildOpenAIChatMessages(contextTurns, preparedContext.systemPrompt)

                createDeepSeekChatCompletionRequest(messages, normalizedPlatform)
            },
            stream = { request ->
                flow {
                    val parser = ReasoningStreamParser()
                    openAIAPI.streamChatCompletion(request, platform.timeout).collect { chunk ->
                        when {
                            chunk.error != null -> {
                                AppLogger.error(context, "ChatRepository", "DeepSeek API error for ${platform.name}: ${chunk.error.message}")
                                emit(ApiState.Error(chunk.error.message))
                            }

                            else -> {
                                val delta = chunk.choices?.firstOrNull()?.delta
                                parser.append(
                                    reasoningChunk = delta?.reasoningContent,
                                    contentChunk = delta?.content
                                ).forEach { emit(it) }
                            }
                        }
                    }

                    parser.flush().forEach { emit(it) }
                }
            }
        ).catch { e ->
            AppLogger.error(context, "ChatRepository", "DeepSeek request preparation failed for ${platform.name}", e)
            emit(ApiState.Error(e.message ?: "Unknown error"))
        }.onCompletion {
            emit(ApiState.Done)
        }
    } catch (e: Exception) {
        AppLogger.error(context, "ChatRepository", "DeepSeek completion failed for ${platform.name}", e)
        flowOf(ApiState.Error(e.message ?: "Failed to complete chat"))
    }

    private suspend fun completeChatWithOpenAIResponses(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2
    ): Flow<ApiState> = try {
        openAIAPI.setToken(platform.token)
        openAIAPI.setAPIUrl(platform.apiUrl)

        streamPreparedApiState(
            prepare = {
                val preparedContext = prepareContextForCompletion(userMessages, assistantMessages, platform)
                val contextTurns = preparedContext.turns
                val inputMessages = buildResponsesInputMessages(contextTurns, platform.uid)

                ResponsesRequest(
                    model = platform.model,
                    input = inputMessages,
                    stream = true,
                    instructions = preparedContext.systemPrompt?.takeIf { it.isNotBlank() },
                    temperature = if (platform.reasoning) null else platform.temperature,
                    topP = if (platform.reasoning) null else platform.topP,
                    reasoning = if (platform.reasoning) {
                        ReasoningConfig(
                            effort = "medium",
                            summary = "auto"
                        )
                    } else {
                        null
                    }
                )
            },
            stream = { request ->
                flow {
                    openAIAPI.streamResponses(request, platform.timeout).collect { event ->
                        when (event) {
                            is ReasoningSummaryTextDeltaEvent -> emit(ApiState.Thinking(event.delta))

                            is OutputTextDeltaEvent -> emit(ApiState.Success(event.delta))

                            is ResponseFailedEvent -> {
                                val errorMessage = event.response.error?.message ?: "Response failed"
                                AppLogger.error(context, "ChatRepository", "OpenAI response failed for ${platform.name}: $errorMessage")
                                emit(ApiState.Error(errorMessage))
                            }

                            is ResponseErrorEvent -> {
                                AppLogger.error(context, "ChatRepository", "OpenAI API error for ${platform.name}: ${event.message}")
                                emit(ApiState.Error(event.message))
                            }

                            else -> {}
                        }
                    }
                }
            }
        ).catch { e ->
            AppLogger.error(context, "ChatRepository", "OpenAI request preparation failed for ${platform.name}", e)
            emit(ApiState.Error(e.message ?: "Unknown error"))
        }.onCompletion {
            emit(ApiState.Done)
        }
    } catch (e: Exception) {
        AppLogger.error(context, "ChatRepository", "OpenAI completion failed for ${platform.name}", e)
        flowOf(ApiState.Error(e.message ?: "Failed to complete chat"))
    }

    private suspend fun completeChatWithOpenAIChatCompletions(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2
    ): Flow<ApiState> = try {
        openAIAPI.setToken(platform.token)
        openAIAPI.setAPIUrl(platform.apiUrl)

        streamPreparedApiState(
            prepare = {
                val preparedContext = prepareContextForCompletion(userMessages, assistantMessages, platform)
                val contextTurns = preparedContext.turns
                validateInlineBudgetIfNeeded(contextTurns, platform)
                val messages = buildOpenAIChatMessages(contextTurns, preparedContext.systemPrompt)

                ChatCompletionRequest(
                    model = platform.model,
                    messages = messages,
                    stream = platform.stream,
                    temperature = platform.temperature,
                    topP = platform.topP
                )
            },
            stream = { request ->
                flow {
                    openAIAPI.streamChatCompletion(request, platform.timeout).collect { chunk ->
                        when {
                            chunk.error != null -> {
                                AppLogger.error(context, "ChatRepository", "OpenAI-compatible API error for ${platform.name}: ${chunk.error.message}")
                                emit(ApiState.Error(chunk.error.message))
                            }

                            chunk.choices?.firstOrNull()?.delta?.content != null -> {
                                emit(ApiState.Success(chunk.choices.first().delta.content!!))
                            }
                        }
                    }
                }
            }
        ).catch { e ->
            AppLogger.error(context, "ChatRepository", "OpenAI-compatible request preparation failed for ${platform.name}", e)
            emit(ApiState.Error(e.message ?: "Unknown error"))
        }.onCompletion {
            emit(ApiState.Done)
        }
    } catch (e: Exception) {
        AppLogger.error(context, "ChatRepository", "OpenAI-compatible completion failed for ${platform.name}", e)
        flowOf(ApiState.Error(e.message ?: "Failed to complete chat"))
    }

    private suspend fun prepareContextForCompletion(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2
    ): PreparedContext {
        val compactionPoint = maybeRunAutoContextCompaction(userMessages, assistantMessages, platform)
        val effectiveCompactionPoint = compactionPoint?.takeIf { point ->
            userMessages.any { it.id == point.boundaryMessageId }
        }
        val contextTurns = buildContextTurns(userMessages, assistantMessages, platform, effectiveCompactionPoint)
        return PreparedContext(
            turns = contextTurns,
            systemPrompt = buildSystemPromptWithCompactionSummary(platform.systemPrompt, effectiveCompactionPoint?.summary)
        )
    }

    private suspend fun buildContextTurns(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2,
        compactionPoint: ChatCompactionPointV2? = null
    ): List<ConversationTurn> {
        val policy = ProviderContextPolicy.forClientType(platform.compatibleType)
        val boundaryIndex = compactionPoint?.boundaryMessageId
            ?.takeIf { it > 0 }
            ?.let { boundaryId -> userMessages.indexOfFirst { it.id == boundaryId } }
            ?: -1
        val dropCount = if (boundaryIndex >= 0) boundaryIndex + 1 else 0
        val scopedUserMessages = userMessages.drop(dropCount)
        val scopedAssistantMessages = assistantMessages.drop(dropCount)

        val contextTurns = contextBuilder.build(scopedUserMessages, scopedAssistantMessages, platform, policy)
        if (!policy.preferProviderFileRefs || contextTurns.isEmpty()) {
            return contextTurns
        }

        return ensureProviderReferencesForTurns(contextTurns, platform)
    }

    private suspend fun maybeRunAutoContextCompaction(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2
    ): ChatCompactionPointV2? {
        val chatId = userMessages.firstOrNull()?.chatId ?: return null
        val latestPoint = if (chatId > 0) {
            chatCompactionPointV2Dao.getLatestForPlatform(chatId, platform.uid)
        } else {
            null
        }

        if (chatId <= 0 || !isAutoContextCompressionEnabled()) return latestPoint
        if (userMessages.size < AUTO_CONTEXT_COMPRESSION_MIN_OMITTED_TURNS + 1) return latestPoint

        val latestBoundaryIndex = latestPoint?.boundaryMessageId
            ?.takeIf { it > 0 }
            ?.let { boundaryId -> userMessages.indexOfFirst { it.id == boundaryId } }
            ?: -1
        val candidateBoundaryIndex = userMessages.lastIndex - 1
        if (candidateBoundaryIndex <= latestBoundaryIndex) return latestPoint

        val newTurnCount = candidateBoundaryIndex - latestBoundaryIndex
        if (newTurnCount < AUTO_CONTEXT_COMPRESSION_MIN_OMITTED_TURNS) return latestPoint

        val historyMessages = collectMessagesThroughBoundary(userMessages, assistantMessages, platform.uid, candidateBoundaryIndex)
        val historyTokens = estimateMessagesTokens(historyMessages)
        val policy = ProviderContextPolicy.forClientType(platform.compatibleType)
        val hasMoreThanRecentWindow = candidateBoundaryIndex + 1 > policy.recentTurnWindow + AUTO_CONTEXT_COMPRESSION_MIN_OMITTED_TURNS
        if (historyTokens < AUTO_CONTEXT_COMPRESSION_TRIGGER_TOKENS && !hasMoreThanRecentWindow) {
            return latestPoint
        }

        val startIndex = latestBoundaryIndex + 1
        val entries = buildCompactionEntries(userMessages, assistantMessages, platform.uid, startIndex, candidateBoundaryIndex)
        if (entries.isBlank()) return latestPoint

        val summary = try {
            generateCompactionSummary(platform, latestPoint?.summary, entries)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            AppLogger.error(context, "ContextCompaction", "Failed to compact chat=$chatId platform=${platform.name}", e)
            null
        } ?: return latestPoint

        val boundaryMessage = userMessages[candidateBoundaryIndex]
        if (boundaryMessage.id <= 0) return latestPoint

        val messagesAfterBoundary = collectMessagesAfterBoundary(userMessages, assistantMessages, platform.uid, candidateBoundaryIndex)
        val tokensAfter = estimateTextTokens(summary) + estimateMessagesTokens(messagesAfterBoundary)
        val point = ChatCompactionPointV2(
            chatId = chatId,
            platformUid = platform.uid,
            summary = summary,
            boundaryMessageId = boundaryMessage.id,
            tokensBefore = historyTokens,
            tokensAfter = tokensAfter
        )
        chatCompactionPointV2Dao.addCompactionPoint(point)
        return point
    }

    private fun buildSystemPromptWithCompactionSummary(
        baseSystemPrompt: String?,
        summary: String?
    ): String? {
        return listOfNotNull(
            baseSystemPrompt?.trim()?.takeIf { it.isNotBlank() },
            summary?.trim()?.takeIf { it.isNotBlank() }?.let {
                "Earlier conversation summary (auto-compressed). Use this as background and prioritize recent messages:\n$it"
            }
        ).joinToString("\n\n").ifBlank { null }
    }

    private fun collectMessagesThroughBoundary(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platformUid: String,
        boundaryIndex: Int
    ): List<MessageV2> = buildList {
        for (index in 0..boundaryIndex.coerceAtMost(userMessages.lastIndex)) {
            add(userMessages[index])
            assistantMessages.getOrNull(index)
                ?.firstOrNull { it.platformType == platformUid && it.hasSendableAssistantPayload() }
                ?.let(::add)
        }
    }

    private fun collectMessagesAfterBoundary(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platformUid: String,
        boundaryIndex: Int
    ): List<MessageV2> = buildList {
        val startIndex = (boundaryIndex + 1).coerceAtLeast(0)
        for (index in startIndex..userMessages.lastIndex) {
            add(userMessages[index])
            assistantMessages.getOrNull(index)
                ?.firstOrNull { it.platformType == platformUid && it.hasSendableAssistantPayload() }
                ?.let(::add)
        }
    }

    private fun buildCompactionEntries(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platformUid: String,
        startIndex: Int,
        boundaryIndex: Int
    ): String = buildString {
        for (index in startIndex..boundaryIndex) {
            val userMessage = userMessages.getOrNull(index)
            val assistantMessage = assistantMessages.getOrNull(index)
                ?.firstOrNull { it.platformType == platformUid && it.hasSendableAssistantPayload() }
            if (userMessage != null && assistantMessage != null) {
                append("Turn ")
                append(index + 1)
                append(":\nUser: ")
                append(userMessage.contentForCompaction())
                append("\nAssistant: ")
                append(assistantMessage.contentForCompaction())
                append("\n\n")
            }
        }
    }.trim()

    private suspend fun generateCompactionSummary(
        platform: PlatformV2,
        previousSummary: String?,
        entries: String
    ): String? {
        val instructions = "Summarize the conversation for future context. Keep durable facts, user preferences, decisions, code details, constraints, and unresolved tasks. Omit filler. Stay concise."
        val prompt = buildString {
            previousSummary?.takeIf { it.isNotBlank() }?.let {
                append("Previous summary:\n")
                append(it)
                append("\n\n")
            }
            append("New conversation turns to merge into the summary:\n")
            append(entries)
        }

        return when (platform.compatibleType) {
            ClientType.OPENAI -> generateOpenAIResponsesSummary(platform, instructions, prompt)
            ClientType.ANTHROPIC -> generateAnthropicSummary(platform, instructions, prompt)
            ClientType.DEEPSEEK, ClientType.CUSTOM -> generateOpenAICompatibleSummary(platform.copy(reasoning = false), instructions, prompt)
        }
    }

    private suspend fun generateOpenAIResponsesSummary(
        platform: PlatformV2,
        instructions: String,
        prompt: String
    ): String {
        val output = StringBuilder()
        val request = ResponsesRequest(
            model = platform.model,
            input = listOf(
                ResponseInputMessage(
                    role = "user",
                    content = ResponseInputContent.text(prompt)
                )
            ),
            stream = true,
            instructions = instructions,
            maxOutputTokens = AUTO_CONTEXT_COMPRESSION_SUMMARY_MAX_TOKENS,
            temperature = if (platform.reasoning) null else 0.2f,
            topP = if (platform.reasoning) null else 1.0f,
            reasoning = null
        )

        openAIAPI.streamResponses(request, platform.timeout).collect { event ->
            when (event) {
                is OutputTextDeltaEvent -> output.append(event.delta)
                is ResponseFailedEvent -> throw IllegalStateException(event.response.error?.message ?: "Summary generation failed")
                is ResponseErrorEvent -> throw IllegalStateException(event.message)
                else -> {}
            }
        }

        return output.toString()
    }

    private suspend fun generateOpenAICompatibleSummary(
        platform: PlatformV2,
        instructions: String,
        prompt: String
    ): String {
        val output = StringBuilder()
        val request = ChatCompletionRequest(
            model = platform.model,
            messages = listOf(
                ChatMessage(role = OpenAIRole.SYSTEM, content = listOf(OpenAITextContent(text = instructions))),
                ChatMessage(role = OpenAIRole.USER, content = listOf(OpenAITextContent(text = prompt)))
            ),
            stream = true,
            temperature = 0.2f,
            topP = 1.0f
        )

        openAIAPI.streamChatCompletion(request, platform.timeout).collect { chunk ->
            chunk.error?.let { throw IllegalStateException(it.message) }
            chunk.choices?.firstOrNull()?.delta?.content?.let { output.append(it) }
        }

        return output.toString()
    }

    private suspend fun generateAnthropicSummary(
        platform: PlatformV2,
        instructions: String,
        prompt: String
    ): String {
        val output = StringBuilder()
        val request = MessageRequest(
            model = platform.model,
            messages = listOf(
                InputMessage(
                    role = MessageRole.USER,
                    content = listOf(AnthropicTextContent(text = prompt))
                )
            ),
            maxTokens = AUTO_CONTEXT_COMPRESSION_SUMMARY_MAX_TOKENS,
            stream = true,
            systemPrompt = instructions,
            temperature = 0.2f,
            topP = 1.0f,
            thinking = null
        )

        anthropicAPI.streamChatMessage(request, platform.timeout).collect { chunk ->
            when (chunk) {
                is dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentDeltaResponseChunk -> {
                    if (chunk.delta.type == dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentBlockType.DELTA) {
                        chunk.delta.text?.let { output.append(it) }
                    }
                }
                is dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ErrorResponseChunk -> {
                    throw IllegalStateException(chunk.error.message)
                }
                else -> {}
            }
        }

        return output.toString()
    }

    private suspend fun isAutoContextCompressionEnabled(): Boolean = try {
        settingRepository.getAutoContextCompression()
    } catch (_: Exception) {
        false
    }

    private suspend fun ensureProviderReferencesForTurns(
        turns: List<ConversationTurn>,
        platform: PlatformV2
    ): List<ConversationTurn> {
        val preparedUserMessages = prepareMessagesForPlatform(turns.map { it.userMessage }, platform)
        return turns.mapIndexed { index, turn ->
            turn.copy(userMessage = preparedUserMessages[index])
        }
    }

    private suspend fun validateInlineBudgetIfNeeded(
        contextTurns: List<ConversationTurn>,
        platform: PlatformV2
    ) {
        val maxInlineBytes = ProviderContextPolicy.forClientType(platform.compatibleType).maxInlineAttachmentBytes ?: return
        attachmentUploadCoordinator.validateInlineAttachmentBudget(contextTurns, maxInlineBytes)
    }

    private suspend fun buildOpenAIChatMessages(
        contextTurns: List<ConversationTurn>,
        systemPrompt: String?
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()

        systemPrompt?.takeIf { it.isNotBlank() }?.let { prompt ->
            messages.add(
                ChatMessage(
                    role = OpenAIRole.SYSTEM,
                    content = listOf(OpenAITextContent(text = prompt))
                )
            )
        }

        contextTurns.forEach { turn ->
            if (hasRenderableMessageContent(turn.userMessage, isUser = true)) {
                messages.add(transformMessageV2ToChatMessage(turn.userMessage, isUser = true))
            }
            turn.assistantMessage?.takeIf { hasRenderableMessageContent(it, isUser = false) }?.let { assistantMessage ->
                messages.add(transformMessageV2ToChatMessage(assistantMessage, isUser = false))
            }
        }

        return messages
    }

    private suspend fun buildResponsesInputMessages(
        contextTurns: List<ConversationTurn>,
        platformUid: String
    ): List<ResponseInputMessage> {
        val inputMessages = mutableListOf<ResponseInputMessage>()

        contextTurns.forEach { turn ->
            if (hasRenderableMessageContent(turn.userMessage, isUser = true)) {
                inputMessages.add(
                    transformMessageV2ToResponsesInput(
                        turn.userMessage,
                        isUser = true,
                        platformUid = platformUid
                    )
                )
            }
            turn.assistantMessage?.takeIf { hasRenderableMessageContent(it, isUser = false) }?.let { assistantMessage ->
                inputMessages.add(
                    transformMessageV2ToResponsesInput(
                        assistantMessage,
                        isUser = false,
                        platformUid = platformUid
                    )
                )
            }
        }

        return inputMessages
    }

    private suspend fun buildAnthropicInputMessages(
        contextTurns: List<ConversationTurn>,
        platformUid: String
    ): List<InputMessage> {
        val messages = mutableListOf<InputMessage>()

        contextTurns.forEach { turn ->
            if (hasRenderableMessageContent(turn.userMessage, isUser = true)) {
                messages.add(transformMessageV2ToAnthropic(turn.userMessage, MessageRole.USER, platformUid))
            }
            turn.assistantMessage?.takeIf { hasRenderableMessageContent(it, isUser = false) }?.let { assistantMessage ->
                messages.add(transformMessageV2ToAnthropic(assistantMessage, MessageRole.ASSISTANT, platformUid))
            }
        }

        return messages
    }

    private fun hasRenderableMessageContent(message: MessageV2, isUser: Boolean): Boolean {
        val messageContent = if (isUser) message.content else message.sendableAssistantContent()
        return messageContent.isNotBlank() || message.attachments.isNotEmpty()
    }

    private suspend fun transformMessageV2ToChatMessage(message: MessageV2, isUser: Boolean): ChatMessage {
        val content = mutableListOf<OpenAIMessageContent>()
        val messageContent = if (isUser) message.content else message.sendableAssistantContent()

        // Add text content
        if (messageContent.isNotBlank()) {
            content.add(OpenAITextContent(text = messageContent))
        }

        // Add file content (images)
        message.attachments.forEach { attachment ->
            val filePath = attachment.preparedFilePath.ifBlank { attachment.localFilePath }
            val mimeType = attachment.mimeType.ifBlank { FileUtils.getMimeType(context, filePath) }
            val encodedImage = getEncodedAttachment(filePath, mimeType)
            if (encodedImage != null) {
                content.add(
                    OpenAIImageContent(
                        imageUrl = ImageUrl(url = "data:${encodedImage.mimeType};base64,${encodedImage.base64Data}")
                    )
                )
            }
        }

        return ChatMessage(
            role = if (isUser) OpenAIRole.USER else OpenAIRole.ASSISTANT,
            content = content
        )
    }

    private suspend fun transformMessageV2ToResponsesInput(message: MessageV2, isUser: Boolean, platformUid: String): ResponseInputMessage {
        val role = if (isUser) "user" else "assistant"
        val messageContent = if (isUser) message.content else message.sendableAssistantContent()

        // Check if there are any image files
        val imageAttachments = message.attachments.filter { attachment ->
            val filePath = attachment.preparedFilePath.ifBlank { attachment.localFilePath }
            val mimeType = attachment.mimeType.ifBlank { FileUtils.getMimeType(context, filePath) }
            FileUtils.isImage(mimeType)
        }

        // If no images, use simple text content
        if (imageAttachments.isEmpty()) {
            return ResponseInputMessage(
                role = role,
                content = ResponseInputContent.text(messageContent)
            )
        }

        // Build content parts for text + images
        val parts = mutableListOf<ResponseContentPart>()

        // Add text content if not blank
        if (messageContent.isNotBlank()) {
            parts.add(ResponseContentPart.text(messageContent))
        }

        // Add image content
        imageAttachments.forEach { attachment ->
            val providerRef = attachment.providerRefFor(platformUid)
            if (providerRef?.remoteType == dev.chungjungsoo.gptmobile.data.model.AttachmentRemoteType.OPENAI_FILE) {
                parts.add(ResponseContentPart.imageFile(providerRef.remoteId))
            } else {
                val filePath = attachment.preparedFilePath.ifBlank { attachment.localFilePath }
                val mimeType = attachment.mimeType.ifBlank { FileUtils.getMimeType(context, filePath) }
                val encodedImage = getEncodedAttachment(filePath, mimeType)
                if (encodedImage != null) {
                    parts.add(
                        ResponseContentPart.image(
                            "data:${encodedImage.mimeType};base64,${encodedImage.base64Data}"
                        )
                    )
                }
            }
        }

        validateResponseInputPartsOrThrow(messageContent, parts.size, message.id)

        return ResponseInputMessage(
            role = role,
            content = ResponseInputContent.parts(parts)
        )
    }

    private suspend fun completeChatWithAnthropic(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2
    ): Flow<ApiState> = try {
        anthropicAPI.setToken(platform.token)
        anthropicAPI.setAPIUrl(platform.apiUrl)

        streamPreparedApiState(
            prepare = {
                val preparedContext = prepareContextForCompletion(userMessages, assistantMessages, platform)
                val contextTurns = preparedContext.turns
                val messages = buildAnthropicInputMessages(contextTurns, platform.uid)

                MessageRequest(
                    model = platform.model,
                    messages = messages,
                    maxTokens = if (platform.reasoning) 16000 else 4096,
                    stream = platform.stream,
                    systemPrompt = preparedContext.systemPrompt,
                    temperature = if (platform.reasoning) null else platform.temperature,
                    topP = if (platform.reasoning) null else platform.topP,
                    thinking = if (platform.reasoning) {
                        dev.chungjungsoo.gptmobile.data.dto.anthropic.request.ThinkingConfig(
                            type = "enabled",
                            budgetTokens = 10000
                        )
                    } else {
                        null
                    }
                )
            },
            stream = { request ->
                flow {
                    anthropicAPI.streamChatMessage(request, platform.timeout).collect { chunk ->
                        when (chunk) {
                            is dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentDeltaResponseChunk -> {
                                when (chunk.delta.type) {
                                    dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentBlockType.THINKING_DELTA -> {
                                        chunk.delta.thinking?.let { emit(ApiState.Thinking(it)) }
                                    }

                                    dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentBlockType.DELTA -> {
                                        chunk.delta.text?.let { emit(ApiState.Success(it)) }
                                    }

                                    else -> {}
                                }
                            }

                            is dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ErrorResponseChunk -> {
                                AppLogger.error(context, "ChatRepository", "Claude API error for ${platform.name}: ${chunk.error.message}")
                                emit(ApiState.Error(chunk.error.message))
                            }

                            else -> {}
                        }
                    }
                }
            }
        ).catch { e ->
            AppLogger.error(context, "ChatRepository", "Claude request preparation failed for ${platform.name}", e)
            emit(ApiState.Error(e.message ?: "Unknown error"))
        }.onCompletion {
            emit(ApiState.Done)
        }
    } catch (e: Exception) {
        AppLogger.error(context, "ChatRepository", "Claude completion failed for ${platform.name}", e)
        flowOf(ApiState.Error(e.message ?: "Failed to complete chat"))
    }

    private suspend fun transformMessageV2ToAnthropic(message: MessageV2, role: MessageRole, platformUid: String): InputMessage {
        val content = mutableListOf<AnthropicMessageContent>()
        val messageContent = if (role == MessageRole.USER) message.content else message.sendableAssistantContent()

        // Add text content
        if (messageContent.isNotBlank()) {
            content.add(AnthropicTextContent(text = messageContent))
        }

        // Add file content (images)
        message.attachments.forEach { attachment ->
            val providerRef = attachment.providerRefFor(platformUid)
            if (providerRef?.remoteType == dev.chungjungsoo.gptmobile.data.model.AttachmentRemoteType.ANTHROPIC_FILE) {
                content.add(AnthropicImageContent(source = ImageSource.file(providerRef.remoteId)))
            } else {
                val filePath = attachment.preparedFilePath.ifBlank { attachment.localFilePath }
                val mimeType = attachment.mimeType.ifBlank { FileUtils.getMimeType(context, filePath) }
                val encodedImage = getEncodedAttachment(filePath, mimeType)
                if (encodedImage != null) {
                    val mediaType = when {
                        encodedImage.mimeType.contains("jpeg") || encodedImage.mimeType.contains("jpg") -> MediaType.JPEG
                        encodedImage.mimeType.contains("png") -> MediaType.PNG
                        encodedImage.mimeType.contains("gif") -> MediaType.GIF
                        encodedImage.mimeType.contains("webp") -> MediaType.WEBP
                        else -> MediaType.JPEG
                    }

                    content.add(
                        AnthropicImageContent(
                            source = ImageSource.base64(mediaType, encodedImage.base64Data)
                        )
                    )
                }
            }
        }

        return InputMessage(role = role, content = content)
    }

    private suspend fun getEncodedAttachment(filePath: String, mimeType: String): FileUtils.EncodedImage? {
        if (!FileUtils.isSupportedUploadMimeType(mimeType)) return null
        AttachmentPayloadCache.get(filePath)?.let { return it }

        return withContext(Dispatchers.IO) {
            FileUtils.encodeFileForUpload(context, filePath, mimeType)?.also { encodedImage ->
                AttachmentPayloadCache.put(filePath, encodedImage)
            }
        }
    }

    private suspend fun prepareMessagesForPlatform(
        messages: List<MessageV2>,
        platform: PlatformV2
    ): List<MessageV2> {
        val updatedMessages = messages.map { attachmentUploadCoordinator.ensureMessageAttachmentsForPlatform(it, platform) }
        val changedMessages = updatedMessages
            .zip(messages)
            .mapNotNull { (updated, original) -> updated.takeIf { it != original } }

        if (changedMessages.isNotEmpty()) {
            messageV2Dao.editMessages(*changedMessages.toTypedArray())
        }

        return updatedMessages
    }

    override suspend fun fetchChatList(): List<ChatRoom> = chatRoomDao.getChatRooms()

    override suspend fun fetchChatListV2(): List<ChatRoomV2> = chatRoomV2Dao.getChatRooms()

    override suspend fun searchChatsV2(query: String): List<ChatRoomV2> {
        if (query.isBlank()) {
            return chatRoomV2Dao.getChatRooms()
        }

        // Search by title
        val titleMatches = chatRoomV2Dao.searchChatRoomsByTitle(query)

        // Search by message content and get chat IDs
        val messageMatchChatIds = messageV2Dao.searchMessagesByContent(query)

        // Get all chat rooms and filter by message match IDs
        val allChatRooms = chatRoomV2Dao.getChatRooms()
        val messageMatches = allChatRooms.filter { it.id in messageMatchChatIds }

        // Combine results and remove duplicates, maintaining order by updatedAt
        return (titleMatches + messageMatches)
            .distinctBy { it.id }
            .sortedByDescending { it.updatedAt }
    }

    override suspend fun fetchMessages(chatId: Int): List<Message> = messageDao.loadMessages(chatId)

    override suspend fun fetchMessagesV2(chatId: Int): List<MessageV2> = messageV2Dao.loadMessages(chatId)

    override suspend fun fetchChatPlatformModels(chatId: Int): Map<String, String> = chatPlatformModelV2Dao.getByChatId(chatId).associate {
        it.platformUid to it.model
    }

    override suspend fun saveChatPlatformModels(chatId: Int, models: Map<String, String>) {
        val rows = models
            .filterKeys { it.isNotBlank() }
            .map { (platformUid, model) ->
                ChatPlatformModelV2(
                    chatId = chatId,
                    platformUid = platformUid,
                    model = model.trim()
                )
            }

        if (rows.isNotEmpty()) {
            chatPlatformModelV2Dao.upsertAll(*rows.toTypedArray())
        }
    }

    override suspend fun getTokenUsageStats(): TokenUsageStats {
        val chats = chatRoomV2Dao.getChatRooms()
        val compactionPointsByChat = chatCompactionPointV2Dao.getAll().groupBy { it.chatId }
        val chatStats = chats.map { chat ->
            val messages = messageV2Dao.loadMessages(chat.id)
            val compactedTokensSaved = compactionPointsByChat[chat.id]
                .orEmpty()
                .groupBy { it.platformUid }
                .values
                .mapNotNull { points ->
                    points.maxWithOrNull(compareBy<ChatCompactionPointV2> { it.createdAt }.thenBy { it.id })
                }
                .sumOf { (it.tokensBefore - it.tokensAfter).coerceAtLeast(0) }
            ChatTokenStats(
                chatId = chat.id,
                title = chat.title,
                messageCount = messages.size,
                estimatedTokens = estimateMessagesTokens(messages),
                compactedTokensSaved = compactedTokensSaved
            )
        }

        return TokenUsageStats(
            totalChats = chatStats.size,
            totalMessages = chatStats.sumOf { it.messageCount },
            totalEstimatedTokens = chatStats.sumOf { it.estimatedTokens },
            totalCompactedTokensSaved = chatStats.sumOf { it.compactedTokensSaved },
            chats = chatStats.sortedByDescending { it.estimatedTokens }
        )
    }

    override suspend fun migrateToChatRoomV2MessageV2() {
        val leftOverChatRoomV2s = chatRoomV2Dao.getChatRooms()
        leftOverChatRoomV2s.forEach { chatPlatformModelV2Dao.deleteByChatId(it.id) }
        chatRoomV2Dao.deleteChatRooms(*leftOverChatRoomV2s.toTypedArray())

        val chatList = fetchChatList()
        val platforms = settingRepository.fetchPlatformV2s()
        val apiTypeMap = mutableMapOf<ApiType, String>()
        val modelByPlatformUid = mutableMapOf<String, String>()

        platforms.forEach { platform ->
            modelByPlatformUid[platform.uid] = platform.model
            when (platform.name) {
                "OpenAI" -> apiTypeMap[ApiType.OPENAI] = platform.uid
                "Anthropic", "Claude" -> apiTypeMap[ApiType.ANTHROPIC] = platform.uid
            }
        }

        chatList.forEach { chatRoom ->
            val messages = messageDao.loadMessages(chatRoom.id).map { m ->
                MessageV2(
                    id = m.id,
                    chatId = m.chatId,
                    content = m.content,
                    attachments = listOf(),
                    revisions = listOf(),
                    linkedMessageId = m.linkedMessageId,
                    platformType = m.platformType?.let { apiTypeMap[it] },
                    createdAt = m.createdAt
                )
            }

            val enabledPlatformUids = chatRoom.enabledPlatform.mapNotNull { apiTypeMap[it] }.filter { it.isNotBlank() }
            chatRoomV2Dao.addChatRoom(
                ChatRoomV2(
                    id = chatRoom.id,
                    title = chatRoom.title,
                    enabledPlatform = enabledPlatformUids,
                    createdAt = chatRoom.createdAt,
                    updatedAt = chatRoom.createdAt
                )
            )

            val modelRows = enabledPlatformUids.map { platformUid ->
                ChatPlatformModelV2(
                    chatId = chatRoom.id,
                    platformUid = platformUid,
                    model = modelByPlatformUid[platformUid] ?: ""
                )
            }

            if (modelRows.isNotEmpty()) {
                chatPlatformModelV2Dao.upsertAll(*modelRows.toTypedArray())
            }

            messageV2Dao.addMessages(*messages.toTypedArray())
        }
    }

    override fun generateDefaultChatTitle(messages: List<MessageV2>): String? = messages.sortedBy { it.createdAt }.firstOrNull { it.platformType == null }?.content?.replace('\n', ' ')?.take(50)

    override suspend fun updateChatTitle(chatRoom: ChatRoomV2, title: String) {
        chatRoomV2Dao.editChatRoom(chatRoom.copy(title = title.replace('\n', ' ').take(50)))
    }

    override suspend fun saveChat(chatRoom: ChatRoomV2, messages: List<MessageV2>, chatPlatformModels: Map<String, String>): ChatRoomV2 {
        if (chatRoom.id == 0) {
            // New Chat
            val chatId = chatRoomV2Dao.addChatRoom(chatRoom)
            val updatedMessages = messages.map { it.copy(chatId = chatId.toInt()) }
            messageV2Dao.addMessages(*updatedMessages.toTypedArray())
            saveChatPlatformModels(
                chatId = chatId.toInt(),
                models = chatPlatformModels.filterKeys { it in chatRoom.enabledPlatform }
            )

            val savedChatRoom = chatRoom.copy(id = chatId.toInt())
            updateChatTitle(savedChatRoom, updatedMessages[0].content)

            return savedChatRoom.copy(title = updatedMessages[0].content.replace('\n', ' ').take(50))
        }

        val savedMessages = fetchMessagesV2(chatRoom.id)
        val updatedMessages = messages.map { it.copy(chatId = chatRoom.id) }

        val shouldBeDeleted = savedMessages.filter { m ->
            updatedMessages.firstOrNull { it.id == m.id } == null
        }
        val shouldBeUpdated = updatedMessages.filter { m ->
            savedMessages.firstOrNull { it.id == m.id && it != m } != null
        }
        val shouldBeAdded = updatedMessages.filter { m ->
            savedMessages.firstOrNull { it.id == m.id } == null
        }

        chatRoomV2Dao.editChatRoom(chatRoom)
        if (shouldBeDeleted.isNotEmpty() || shouldBeUpdated.isNotEmpty()) {
            chatCompactionPointV2Dao.deleteByChatId(chatRoom.id)
        }
        messageV2Dao.deleteMessages(*shouldBeDeleted.toTypedArray())
        messageV2Dao.editMessages(*shouldBeUpdated.toTypedArray())
        messageV2Dao.addMessages(*shouldBeAdded.toTypedArray())
        saveChatPlatformModels(
            chatId = chatRoom.id,
            models = chatPlatformModels.filterKeys { it in chatRoom.enabledPlatform }
        )

        return chatRoom
    }

    override suspend fun duplicateChatV2(chatRoom: ChatRoomV2): ChatRoomV2 {
        val duplicatedTitle = "${chatRoom.title} (copy)".take(50)
        val duplicatedChatId = chatRoomV2Dao.addChatRoom(
            ChatRoomV2(
                title = duplicatedTitle,
                enabledPlatform = chatRoom.enabledPlatform
            )
        ).toInt()

        val messages = fetchMessagesV2(chatRoom.id).map { message ->
            message.copy(
                id = 0,
                chatId = duplicatedChatId,
                linkedMessageId = 0
            )
        }
        if (messages.isNotEmpty()) {
            messageV2Dao.addMessages(*messages.toTypedArray())
        }

        val chatPlatformModels = fetchChatPlatformModels(chatRoom.id)
        saveChatPlatformModels(duplicatedChatId, chatPlatformModels)

        return chatRoom.copy(
            id = duplicatedChatId,
            title = duplicatedTitle,
            createdAt = System.currentTimeMillis() / 1000,
            updatedAt = System.currentTimeMillis() / 1000
        )
    }

    override suspend fun deleteChats(chatRooms: List<ChatRoom>) {
        chatRoomDao.deleteChatRooms(*chatRooms.toTypedArray())
    }

    override suspend fun deleteChatsV2(chatRooms: List<ChatRoomV2>) {
        chatRooms.forEach { chatCompactionPointV2Dao.deleteByChatId(it.id) }
        chatRoomV2Dao.deleteChatRooms(*chatRooms.toTypedArray())
    }
}

private fun MessageV2.contentForCompaction(): String {
    val rawContent = if (platformType == null) content else sendableAssistantContent()
    val compactedContent = rawContent
        .trim()
        .replace(Regex("\\s+"), " ")
        .let { content ->
            if (content.length <= AUTO_CONTEXT_COMPRESSION_MAX_MESSAGE_CHARS) {
                content
            } else {
                content.take(AUTO_CONTEXT_COMPRESSION_MAX_MESSAGE_CHARS).trimEnd() + "..."
            }
        }

    return when {
        compactedContent.isNotBlank() -> compactedContent
        attachments.isNotEmpty() -> "[attachments omitted]"
        else -> ""
    }
}

private fun List<String>.keepMostRecentWithin(maxChars: Int): String {
    val selectedEntries = mutableListOf<String>()
    var totalChars = 0

    for (entry in asReversed()) {
        val addedLength = entry.length + if (selectedEntries.isEmpty()) 0 else 2
        if (totalChars + addedLength > maxChars) {
            if (selectedEntries.isEmpty()) {
                selectedEntries.add(entry.takeLast(maxChars))
            }
            break
        }

        selectedEntries.add(0, entry)
        totalChars += addedLength
    }

    return selectedEntries.joinToString("\n\n")
}

internal fun createDeepSeekChatCompletionRequest(
    messages: List<ChatMessage>,
    platform: PlatformV2
): ChatCompletionRequest {
    val normalizedPlatform = platform.withDeepSeekThinkingModel()
    val isThinkingEnabled = normalizedPlatform.reasoning
    val usesV4ThinkingToggle = isDeepSeekV4Model(normalizedPlatform.model)

    return ChatCompletionRequest(
        model = normalizedPlatform.model,
        messages = messages,
        stream = normalizedPlatform.stream,
        temperature = if (isThinkingEnabled) null else normalizedPlatform.temperature,
        topP = if (isThinkingEnabled) null else normalizedPlatform.topP,
        reasoningEffort = if (isThinkingEnabled && usesV4ThinkingToggle) "high" else null,
        thinking = if (usesV4ThinkingToggle) {
            ThinkingConfig(type = if (isThinkingEnabled) "enabled" else "disabled")
        } else {
            null
        }
    )
}

internal fun PlatformV2.withDeepSeekThinkingModel(): PlatformV2 {
    if (compatibleType != ClientType.DEEPSEEK) return this

    return when {
        reasoning && model.equals("deepseek-chat", ignoreCase = true) -> copy(model = "deepseek-reasoner")
        !reasoning && model.equals("deepseek-reasoner", ignoreCase = true) -> copy(model = "deepseek-chat")
        else -> this
    }
}

internal fun isDeepSeekV4Model(model: String): Boolean = model.startsWith("deepseek-v4-", ignoreCase = true)

internal fun MessageV2.sendableAssistantContent(): String {
    val strippedContent = stripAssistantErrorNote(effectiveContent()).trim()
    return if (strippedContent.startsWith("Error: ")) "" else strippedContent
}

internal fun MessageV2.hasSendableAssistantPayload(): Boolean = sendableAssistantContent().isNotBlank() || attachments.isNotEmpty()

internal fun validateResponseInputPartsOrThrow(messageContent: String, partCount: Int, messageId: Int) {
    if (messageContent.isBlank() && partCount == 0) {
        throw IllegalStateException("No encodable message content for messageId=$messageId")
    }
}

internal fun <T> streamPreparedApiState(
    prepare: suspend () -> T,
    stream: suspend (T) -> Flow<ApiState>
): Flow<ApiState> = flow {
    emit(ApiState.Loading)
    val preparedRequest = withContext(Dispatchers.Default) { prepare() }
    emitAll(stream(preparedRequest))
}
