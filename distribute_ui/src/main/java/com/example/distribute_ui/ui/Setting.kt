package com.example.distribute_ui.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.ArrowDropDownCircle
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.distribute_ui.R
import com.example.distribute_ui.data.Dim
import com.example.distribute_ui.data.PreferenceHelper
import com.example.distribute_ui.data.serverIp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: InferenceViewModel,
    onHeaderClicked: () -> Unit,
    onWorkerClicked: () -> Unit,
    onCancelClicked: () -> Unit,
    onNextClicked: (id: Int) -> Unit,
    onBackendStarted: () -> Unit,
    onMonitorStarted: () -> Unit,
    modifier: Modifier = Modifier
){
    val selectedValue = remember { mutableStateOf(false) }
    // 0 for worker, 1 for header
    val selectionNode = remember { mutableStateOf(0) }
    val nextClickedState = remember { mutableStateOf(false) }
    val buttonEnable = remember {
        mutableStateOf(false)
    }

    var showDialog by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    val context = LocalContext.current

    val pingFont = FontFamily(
        Font(R.font.pingfang_regular)
    )
    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xB3F5F5FF)),
        contentAlignment = Alignment.TopCenter){
        Column(modifier = Modifier.fillMaxSize()) {
            Profile()
            Information()
            Others()
            Card(
                shape = RoundedCornerShape(50.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFD4C159)),
                modifier = Modifier
                    .fillMaxWidth().requiredHeight(65.dp)
                    .padding(top = 25.dp, start = 16.dp, end = 16.dp)
            ){
                Column (modifier = Modifier.fillMaxSize().clickable {
                    selectedValue.value = true
                    selectionNode.value = 0
                    onWorkerClicked()

                    nextClickedState.value = true
                    onNextClicked(selectionNode.value)
                },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally){
                    Text("Contribute your device and get credits",
                        fontSize = 16.sp,
                        color = Color.White,
                    )
                }
            }
            Card(
                shape = RoundedCornerShape(50.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth().requiredHeight(60.dp)
                    .padding(top = 20.dp, start = 16.dp, end = 16.dp)
            ){
                Column (modifier = Modifier.fillMaxSize().clickable {
                    inputText = serverIp
                    showDialog = true
                },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally){
                    Text("Sever ip: $serverIp",
                        fontSize = 16.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
    if (nextClickedState.value && selectionNode.value == 0) {
        onBackendStarted()
        WorkerProgressDialog(
            enable = buttonEnable.value,
            onClicked = { }
        )
    }

    if(showDialog){
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = "输入服务器IP") },
            text = {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("请输入IP地址") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        serverIp = inputText
                        PreferenceHelper.saveServerIp(context, inputText)
                        showDialog = false
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }
}


@Composable
fun Profile(){
    Column (modifier = Modifier.fillMaxWidth()){
        Text("Profile",
            modifier = Modifier.align(Alignment.Start).padding(start = 10.dp, top = 2.dp),
            fontSize = 14.sp,
            color = Color(0xFFAAAAAA)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(painter = painterResource(R.drawable.cute), " ",
                        modifier = Modifier.size(50.dp).clip(CircleShape).
                        border(width = 1.dp, color = Color.Gray, shape = CircleShape))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "User_0",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ArrowForwardIos,
                    contentDescription = "箭头",
                    tint = Color(0x80939393),
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

@Composable
fun Information(){
    Column (modifier = Modifier.fillMaxWidth()){
        Text("Information",
            modifier = Modifier.align(Alignment.Start).padding(start = 10.dp, top = 2.dp),
            fontSize = 14.sp,
            color = Color(0xFFAAAAAA)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth().requiredHeight((0.17 * Dim.height).dp)
                .padding(top = 4.dp, start = 16.dp, end = 16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Smartphone, "")
                    Text("Available Devices: 20", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width((0.04 * Dim.width).dp))
                    Icon(Icons.Outlined.AcUnit, "")
                    Text("Models: 3", fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Calculate, "")
                    Text("Total Flops: 1000", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(43.dp))
                    Icon(Icons.Outlined.Paid, "")
                    Text("Credits: 20", fontWeight = FontWeight.Bold)
                }
                Card (shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEEEEF1)),
                    modifier = Modifier
                        .requiredHeight((0.053*Dim.height).dp).requiredWidth((0.4*Dim.width).dp)
                        .padding(top = 4.dp).align(Alignment.CenterHorizontally)
                ){
                    Row (verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxSize()
                    ){
                        Icon(Icons.Outlined.ArrowDropDownCircle, "",
                            modifier = Modifier.padding(start = 10.dp).clickable {  }
                        )
                        Text("Details of devices", fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun Others(){
    val scrollState = rememberScrollState()
    Column (modifier = Modifier.fillMaxWidth()){
        Text("Settings",
            modifier = Modifier.align(Alignment.Start).padding(start = 10.dp, top = 2.dp),
            fontSize = 14.sp,
            color = Color(0xFFAAAAAA)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth().requiredHeight((0.28 * Dim.height).dp)
                .padding(top = 4.dp, start = 16.dp, end = 16.dp)
        ) {
            Column (modifier = Modifier.verticalScroll(scrollState)){
                for(i in 1..6){
                    val text = "Settings_$i"
                    Other(text)
                    if(i != 6){
                        Divider(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
fun Other(text : String){
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Tune, "", modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
        Icon(
            imageVector = Icons.Filled.ArrowForwardIos,
            contentDescription = "箭头",
            tint = Color(0x80939393),
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
fun WorkerProgressDialog(
    enable: Boolean,
    onClicked: () ->  Unit
) {
    AlertDialog(
        shape = RoundedCornerShape(20.dp),
        onDismissRequest = {},
        text = {
            Text(text = "        The system is working...")
        },
        confirmButton = {
        }
    )
}