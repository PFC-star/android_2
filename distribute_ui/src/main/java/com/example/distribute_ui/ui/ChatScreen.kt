package com.example.distribute_ui.ui
import android.content.Context
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.distribute_ui.R
import com.example.distribute_ui.TAG
import com.example.distribute_ui.ui.components.UserInput
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.example.distribute_ui.Events
import com.example.distribute_ui.network.ApiManager
import com.example.distribute_ui.network.ApiMessage
import io.noties.markwon.Markwon

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    index: String,
    viewModel: InferenceViewModel,
    modifier: Modifier = Modifier
) {
    val authorMe = stringResource(R.string.author_me)   // String("You")
    val scrollState = rememberLazyListState()           // 记录懒列的滚动状态
    val topBarState = rememberTopAppBarState()          // 记录TopAppBar的状态
    val scope = rememberCoroutineScope()                // 当前协程作用范围
    val uiState = viewModel.uiState.collectAsState()    // 异步更新消息历史记录

    val sampleId by viewModel.sampleId.observeAsState(0)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topBarState)

    val num_device = viewModel.num_device

    Column (modifier = Modifier.fillMaxSize()){
        Box(modifier = Modifier
            .fillMaxWidth()
            .weight(0.15f)
            .background(Color.White),
            contentAlignment = Alignment.Center){
            ModelSelection(index, num_device)
        }
        Box(modifier = Modifier
            .fillMaxWidth()
            .weight(1.2f)
            .background(Color.White),
            contentAlignment = Alignment.TopCenter){
            Messages(
                chatHistory= viewModel.chatHistory,
                scrollState = scrollState
            )
        }
        Column (modifier = Modifier.fillMaxWidth().weight(0.2f).background(color = Color.White),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            UserInput(
                onClicked = { },
                onMessageSent = { content ->
                    viewModel.addChatHistory(Messaging(authorMe, content, getCurrentFormattedTime()))
                    EventBus.getDefault().post(Events.messageSentEvent(true, content))
                },
                resetScroll = {
                    scope.launch {
                        scrollState.scrollToItem(0)
                    }
                },
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun Messages(
    chatHistory: MutableList<Messaging>,
    scrollState: LazyListState,
) {
    LazyColumn(
        state = scrollState,
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ){
        itemsIndexed(chatHistory){ index, message ->
            val isUser = index % 2 == 0
            if(isUser){
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color(0xFFF1F1F1),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(text = message.content, color = Color.Black)
                    }
                }
            }
            else{
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.Top
                ) {
                    Image(painter = painterResource(R.drawable.star2), " ",
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .border(
                                width = 1.dp,
                                color = Color.Gray,
                                shape = CircleShape
                            ))
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color.White, // AI 消息背景颜色
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        message.content.let { context ->
                            AndroidView(
                                factory = {context ->
                                    TextView(context).apply{
                                        setTextColor(Color.Black.toArgb())
                                    }
                                },
                                update = { textView ->
                                    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                                    val markwon = Markwon.create(textView.context)
                                    markwon.setMarkdown(textView, context)
                                },
                                modifier = Modifier.padding(end = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModelSelection(index: String, num_devices: Int){
    // 下拉菜单选项列表
    val options = listOf("Model:bloom560m", "Model:bloom560m-int8","Model:bloom1b1",
        "Model:bloom1b1-int8","Model:bloom1b7", "Model:bloom1b7-int8", "Model:bloom3b",
        "Model:bloom3b-int8", "Model:bloom7b", "Model:bloom7b-int8"
    )
    val str = "Model:$index"
    // 当前选中的选项（默认选择第一个）
    var selectedOption by remember { mutableStateOf(str) }
    // 下拉菜单展开状态
    var expanded by remember { mutableStateOf(false) }
    Row (modifier = Modifier.fillMaxSize().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly){
        Card (modifier = Modifier
            .requiredHeight(25.dp)
            .requiredWidth(165.dp)
            .clickable { expanded = true },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0x80FFF9C4))
        ){
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowForwardIos,
                    contentDescription = "",
                    tint = Color(0x80939393),
                    modifier = Modifier
                        .size(22.dp)
                        .padding(start = 6.dp)
                )
                // 显示当前选项的文本
                Text(text = selectedOption, color = Color.Black, fontSize = 12.sp)

                // 下拉菜单
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = {Text(text = option)},
                            onClick = {
                                selectedOption = option
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
        Text("num of devices: $num_devices")
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun getCurrentFormattedTime(): String {
    val currentTimeMillis = System.currentTimeMillis()
    val instant = Instant.ofEpochMilli(currentTimeMillis)
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}