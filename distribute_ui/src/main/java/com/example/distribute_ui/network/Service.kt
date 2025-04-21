package com.example.distribute_ui.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface Service {
    @POST("completions")
    fun generateText(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String,
        @Body request: OpenAiRequest
    ) : Call<OpenAIResponse>
}