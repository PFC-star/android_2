package com.example.distribute_ui.data

import android.content.Context

object PreferenceHelper {
    // 保存 server_ip
    fun saveServerIp(context: Context, ip: String) {
        val sharedPreferences = context.getSharedPreferences("AppConfig", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("server_ip", ip).apply()
    }

    // 读取 server_ip（默认值可设为空或特定IP）
    fun loadServerIp(context: Context): String {
        val sharedPreferences = context.getSharedPreferences("AppConfig", Context.MODE_PRIVATE)
        return sharedPreferences.getString("server_ip", "") ?: ""
    }
}