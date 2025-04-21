package com.example.distribute_ui.Log

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val mTAG = "monitor"

// 通过companion object实现的单例类，用于将日志信息写入设备外部存储中的文件
class Logger private constructor() {    // 私有构造函数，无法从外部实例化类

    fun log(context: Context, message: String) {
        val time = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getCurrentTime()    // 获取当前时间
        } else {
            "no_valid_time"
        }
//        val dirPath = Environment.getExternalStorageDirectory().absolutePath + "/LinguaLinked"
        val dirPath = context.getExternalFilesDir(null)     // 获取应用的外部文件存储目录
        if (dirPath == null) {
            Log.d(mTAG, "app log dir path is null")
            return
        }
        Log.d(mTAG, "dirPath is $dirPath")

        val monitorDir = dirPath.absolutePath + "/Monitor"  // 记录日志的子目录路径
        val logFilePath = "$monitorDir/${time}_monitor.txt" // 具体日志文件的路径
        try {
            val directory = File(monitorDir)    // 根据路径创建一个File(目录或文件)类对象
            if (!directory.exists()) {          // 检查对象代表的目录是否真正存在，若不存在则创建目录
                directory.mkdirs()
            }
            Log.d(mTAG, "dir is ready")
            val logFile = File(logFilePath)
            if (!logFile.exists()) {
                logFile.createNewFile()
            }
            Log.d(mTAG, "filePath is ready")

            val writer = FileWriter(logFile, true)  // 创建对象用于对文件执行追加写操作
            writer.append(message + "\n")                   // 将message追加写入日志文件
            writer.flush()                                  // 将数据写入磁盘
            writer.close()                                  // 关闭文件流
            Log.d(mTAG, "writing is ready")
        } catch (e: IOException) {
            Log.d(mTAG, e.toString())
        }
    }

    // 获取当前时间，并格式化为yyyy-MM-dd-H-m格式
    @RequiresApi(Build.VERSION_CODES.O)
    private fun getCurrentTime(): String {
        val current = LocalDateTime.now()

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-H-m")
        return current.format(formatter)
    }

    companion object {  // 单例模式
        // The single instance of Logger
        val instance: Logger by lazy { Logger() }   // 唯一的Logger类实例instance，在第一次访问时懒加载
    }
}