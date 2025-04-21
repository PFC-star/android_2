package com.example.distribute_ui.ui

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.SecureConnection.Communication
import com.example.SecureConnection.Config
import com.example.distribute_ui.DataRepository
import com.example.distribute_ui.TAG
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter


@RequiresApi(Build.VERSION_CODES.O)
//用于存储UI数据的ViewModel类。application参数提供了访问应用上下文的能力
class InferenceViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState: MutableStateFlow<ChatUiState> = MutableStateFlow(ChatUiState())   // 持有ChatUiState类型的可变数据
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()    // 将可变数据流强转为不可变数据流，封装为只读接口
    val chatHistory: MutableList<Messaging> = _uiState.value.chatHistory    // 历史消息列表

    val isDirEmpty: LiveData<Boolean> = DataRepository.isDirEmptyLiveData   // 模型目录是否为空
    val sampleId: LiveData<Int> = DataRepository.sampleId   // 采样Id

    private var updatingDecodedString: Boolean = false      // 是否需要更新解码字符串
    private var decodedStringIndex: Int = -1                // 用于记录最后一条模型消息在消息列表中的索引号
    private var previousSampleID: Int = 0                   // 前驱节点的采样Id

    private val modelAuthor = "User"
    private var nodeId: Int = 0
    private var modelName: String = ""
    var config: Config? = null
    var com: Communication? = null
    var num_device: Int = 2

    // monitor system service
    private val _availableMemory = MutableLiveData<Long>()
    val memory: LiveData<Long> get() = _availableMemory     // 可用内存

    private val _latency = MutableLiveData<Double>()

    init {
        // 保持观察DataRepository中decodingStringLiveData的值，每当其更新时执行回调
        DataRepository.decodingStringLiveData.observeForever { decodedString -> // 即为新的值
            if (!decodedString.isNullOrEmpty()) {   // 当新的值非空时
                if (updatingDecodedString) {        // 且需要更新时，将当前消息加入消息列表，更新索引值
                    chatHistory.add(Messaging(modelAuthor, decodedString, getCurrentFormattedTime()))
                    decodedStringIndex = chatHistory.lastIndex
                    updatingDecodedString = false
                } else {    // 不需要更新时
                    if (decodedStringIndex != -1) { // 若不为第一条模型消息，则用当前字符串替换最后一个位置上的字符串
                        chatHistory[decodedStringIndex] = Messaging(modelAuthor, decodedString, getCurrentFormattedTime())
                    } else {                        // 为第一条模型消息，将该消息加入消息列表，更新索引值
                        chatHistory.add(Messaging(modelAuthor, decodedString, getCurrentFormattedTime()))
                        decodedStringIndex = chatHistory.lastIndex
                    }
                }
            }
        }

        // 保持观察sampleId的值，每当其更新时执行回调
        DataRepository.sampleId.observeForever { sampleId ->
            // 若sampleId已更新且消息列表中的最后一条为用户消息
            if (sampleId != null && sampleId != previousSampleID && chatHistory.size == decodedStringIndex + 2) {
                updatingDecodedString = true    // 需要更新，触发上面的函数
                previousSampleID = sampleId     // 更新前驱节点id
            }
        }

        Log.d(TAG, "InferenceViewModel init")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getCurrentFormattedTime(): String {     // 获取格式化的当前时间
        val currentTimeMillis = System.currentTimeMillis()
        val instant = Instant.ofEpochMilli(currentTimeMillis)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
        return formatter.format(instant)
    }

    fun updateLatency(latency: Double) {        // 更新延迟数据
        Log.d(TAG, "update latency is $latency")
        _latency.postValue(latency)
    }

    fun addChatHistory(msg: Messaging){         // 添加消息至历史消息列表
        chatHistory.add(msg)
    }

    fun resetOption() {                         // 重置节点Id和模型名称
        this.nodeId = 0
        this.modelName = ""
    }
}