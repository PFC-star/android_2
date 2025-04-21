package com.example.distribute_ui.network

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ApiManager {
    private val _openaiResponse = mutableStateOf(OpenAIResponse())
    val openaiResponse: State<OpenAIResponse>
        get() = _openaiResponse

    var messageList : List<ApiMessage> = listOf(ApiMessage(role = "system", content = "You are a helpful assistant.") )

    fun getResponse(messages : List<ApiMessage>){
        val request : OpenAiRequest = OpenAiRequest(model = "qwen-max", messages = messages)
        val service = OpenaiApi.retrofitService.generateText(
            "Bearer ${OpenaiApi.API_KEY}",
            "application/json",
            request
        )

        service.enqueue(object : Callback<OpenAIResponse>{
            override fun onResponse(
                call: Call<OpenAIResponse>,
                response: Response<OpenAIResponse>
            ) {
                if(response.isSuccessful){
                    Log.d("api", "Successful")
                    Log.d("api_response","${response.body()}")
                    _openaiResponse.value = response.body()!!
//                    Log.d("api_value1", "${_openaiResponse.value}")
//                    Log.d("api_value2", "${openaiResponse.value}")
                }
                else{
                    Log.d("api_error", "errorBody:${response.errorBody()?.toString()}")
                    Log.d("api_error", "errorCode:${response.code()}")
                }
            }

            override fun onFailure(call: Call<OpenAIResponse>, t: Throwable) {
                Log.d("api_failure", "error:${t.printStackTrace()}")
            }
        })
    }
}