package com.example.distribute_ui
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

object DataRepository {
    private val _isDirEmptyLiveData = MutableLiveData<Boolean>()    // 私有的LiveData变量，用于记录目录是否为空
    val isDirEmptyLiveData: LiveData<Boolean> = _isDirEmptyLiveData // 外部可访问的只读LiveData

    private val _decodingStringLiveData = MutableLiveData<String>() // 记录解码得到的字符串
    val decodingStringLiveData: LiveData<String> = _decodingStringLiveData

    // sample指的从模型的输出概率分布中抽取一个具体的输出
    private val _sampleId = MutableLiveData<Int>()  // 记录执行采样设备的ID
    val sampleId: LiveData<Int> = _sampleId

    fun updateSampleId(sampleId: Int) {
        _sampleId.postValue(sampleId)   // 更新sampleId的值并通知所有观察者，postValue方法会在主线程之外的线程上异步更新LiveData的值
    }

    fun updateDecodingString(updatedString: String) {
//        val responsePosition: Int = updatedString.indexOf("Response:")
//        val decodedStringAfterResponse: String = updatedString.substring(responsePosition + 9)
        _decodingStringLiveData.postValue(updatedString)    // 更新decodingString的值并通知所有观察者
    }

    fun setIsDirEmpty(isEmpty: Boolean) {       // 设置isDirEmptyLiveData的值
        _isDirEmptyLiveData.postValue(isEmpty)
    }
}