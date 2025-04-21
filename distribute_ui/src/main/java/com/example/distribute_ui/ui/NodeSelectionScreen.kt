package com.example.distribute_ui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.distribute_ui.R
import com.example.distribute_ui.data.Dim
import com.example.distribute_ui.ui.components.ButtonBar

@Composable
fun NodeSelectionScreen(
    viewModel: InferenceViewModel,
    onHeaderClicked: () -> Unit,    // { }
    onWorkerClicked: () -> Unit,    // { }
    onCancelClicked: () -> Unit,    // 重置节点编号和模型名称
    onNextClicked: (id: Int) -> Unit,   // 执行onRolePassed(),若为头结点，则跳转至模型选择界面
    onBackendStarted: () -> Unit,       // onBackendStarted()
    onMonitorStarted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mediumPadding = dimensionResource(R.dimen.padding_medium)

    val selectedValue = remember { mutableStateOf(false) }      // 记录是否至少选中了一个节点类型
    val selectionNode = remember { mutableStateOf(0) }          // 记录节点类型。头结点标记为1，参与节点标记为0
    val nextClickedState = remember { mutableStateOf(false) }   // 记录next按钮是否被触发
    val buttonEnable = remember { mutableStateOf(false)  }      // 记录按钮是否可点击

    Column (modifier = Modifier.fillMaxSize()){
        Box (modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
            contentAlignment = Alignment.Center){

            Card(
                modifier = Modifier.requiredWidth((0.9* Dim.width).dp).requiredHeight((0.215* Dim.height).dp).padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFe5e496))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Become header and start chatting",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Card(
                        shape = RoundedCornerShape(50.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFD4C159)),
                        modifier = Modifier
                            .requiredWidth((0.3* Dim.width).dp).requiredHeight((0.072* Dim.height).dp)
                            .padding(top = 10.dp)
                    ){
                        Column (modifier = Modifier.fillMaxSize().clickable {
                            selectedValue.value = true
                            selectionNode.value = 1
                            onHeaderClicked()

                            nextClickedState.value = true
                            onNextClicked(selectionNode.value)
                        },
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally){
                            Text("Next",
                                fontSize = 14.sp,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}
