package com.example.distribute_ui.network

data class OpenAiRequest(
    val model: String? = null,
    val messages: List<ApiMessage>? = null,
    val max_tokens: Int? = 5000,
    val temperature: Double? = 0.8,
    val stream: Boolean = false
)

data class ApiMessage(
    val role: String? = null,
    val content: String? = null
)

data class OpenAIResponse(
    val id: String? = null,
    val choices: List<Choice>? = null,
    val created: String? = null,
    val model: String? = null,
//    val object: String? = null,
    val service_tier: String?= null,
    val system_fingerprint: String?= null,
    val usage: Usage? = null
)

data class Choice(
    val finish_reason: String? = null,
    val index: Int? = null,
//    val logprobs: Double? = null,
    val message: Message_res? = null
)

data class Message_res(
    val content: String? = null,
    val role: String? = null,
//    val function_call: String?= null,
//    val tool_calls: String? = null
)

data class Usage(
    val completion_tokens: Int? = null,
    val prompt_tokens: Int? = null,
    val total_tokens: Int? = null
)
