package com.example.distribute_ui.data

object Dim{
    var width: Int = 0
    var height: Int = 0
}

var serverIp = "10.193.217.157"

val exampleModelName = listOf(
    "bloom560m",
    "bloom560m-int8",

    "bloom3b",
    "bloom3b-int8",
    "bloom7b",
    "bloom7b-int8"
)

val modelMap: HashMap<String, String> = hashMapOf(
    "Bloom" to "bloom560m"
)
