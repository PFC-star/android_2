package com.example.distribute_ui.ui

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.example.distribute_ui.Events
import com.example.distribute_ui.R
import com.example.distribute_ui.TAG
import com.example.distribute_ui.ui.components.ButtonBar
import com.example.distribute_ui.ui.theme.Distributed_inference_demoTheme
import org.greenrobot.eventbus.EventBus
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ModelSelectionScreen(
    viewModel: InferenceViewModel,
    options: List<String>?,         // 可选模型列表
    onCancelClicked: () -> Unit,    // 返回节点选择界面，重置配置
    onNextClicked: (String) -> Unit,      // 导航至聊天界面
    onBackendStarted: () -> Unit,   // onBackendStarted()
    onModelSelected: (modelName: String) -> Unit,   // onModelSelected()
    modifier: Modifier = Modifier
){
    var selectedModel by remember { mutableStateOf("") }
    var selectedValue = remember { mutableStateOf(false) }
    val nextClickedState = remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isDirEmpty by viewModel.isDirEmpty.observeAsState(initial = true)
    val sc = rememberScrollableState { 0.0f }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.padding(dimensionResource(R.dimen.padding_medium)).scrollable(sc, orientation = Orientation.Vertical)){
            Text(
                text = "Choose your chatting model:",
                textAlign = TextAlign.Left,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(10.dp))
            options?.forEach { item ->
                Row(
                    modifier = Modifier.selectable(
                        selected = selectedModel == item,
                        onClick = {
                            selectedModel = item
                            selectedValue.value = true
                            onModelSelected(item)
                        }
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ){
                    RadioButton(
                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFD4C159)),
                        selected = selectedModel == item,
                        onClick = {
                            selectedModel = item
                            Log.d(TAG, "selected Model is $selectedModel")
                            selectedValue.value = true
                            onModelSelected(item)
                        }
                    )
                    Text(item)
                }
            }
        }
        ButtonBar(
            modifier = modifier,
            onNextClicked = {
                nextClickedState.value = true
            },
            onCancelClicked = {
                nextClickedState.value = false
                selectedValue.value = false
                onCancelClicked()
            },
            selectedValue = selectedValue.value
        )

        if (nextClickedState.value && selectedValue.value) {
            Log.d(TAG, "Model Directory Path is Empty: " + isDirEmpty)
            onBackendStarted()
            HeaderProgressDialog(
                isEnabled = !isDirEmpty,
                onClicked = {
                    EventBus.getDefault().post(Events.enterChatEvent(true))
                    onNextClicked(selectedModel)
                }
            )
        }
    }
}

@Composable
fun HeaderProgressDialog(
    isEnabled: Boolean,
    onClicked: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        text = {
            Text(text = "    System is preparing, please wait...")
        },
        confirmButton = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4C159)),
                    enabled = isEnabled,  // 将true改回为isEnabled
                    onClick = onClicked
                ) {
                    Text(text = "Start")
                }
            }
        }
    )
}