package com.example.distribute_ui.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.toMutableStateList

// 用于记录聊天的消息列表
class ChatUiState(
    initialMessages: MutableList<Messaging> = mutableListOf()   // 可变消息列表，初始为空
    ) {
    // 将MutableList类型转为MutableStateList，使得其变化能触发UI更新
    private val _chatHistory: MutableList<Messaging> = initialMessages.toMutableStateList()
    val chatHistory = _chatHistory  // 对外的读取接口
    var modelName: String = ""      // 当前模型名称
}

// 消息类，包含作者、内容、时间戳、标号
@Immutable  // 创建后无法更改属性
data class Messaging(
    val author: String,
    val content: String,
    val timestamp: String,
    val image: Int? = null
)