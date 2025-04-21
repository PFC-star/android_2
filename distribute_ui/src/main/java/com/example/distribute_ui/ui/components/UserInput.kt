package com.example.distribute_ui.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.distribute_ui.R
import com.example.distribute_ui.data.Dim

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun UserInput(
    onClicked: () -> Unit,
    onMessageSent: (String) -> Unit,
    resetScroll: () -> Unit = {},
) {

    var textState by rememberSaveable(stateSaver = TextFieldValue.Saver) {  // 记录文本框的内容
        mutableStateOf(TextFieldValue())
    }
    val keyboardController = LocalSoftwareKeyboardController.current    // 获取当前的键盘控制器实例，用于显示或隐藏软键盘

    TextField(
        value = textState,
        onValueChange = { textState = it },
        placeholder = { Text(text = "Message", fontSize = 16.sp) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "",
                tint = Color.Black,
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Filled.ArrowForward,
                contentDescription = "",
                tint = Color.Black,
                modifier = Modifier.clickable{
                    if (textState.text.isNotBlank()) {
                        keyboardController?.hide()

                        onMessageSent(textState.text)
                        onClicked()
                        textState = TextFieldValue()
                        resetScroll()
                    }
                }
            )
        },
        modifier = Modifier.requiredWidth((0.85* Dim.width).dp),
        shape = RoundedCornerShape(50.dp),
        colors = TextFieldDefaults.textFieldColors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            containerColor = Color(0xFFEEECEC),
        )
    )
}