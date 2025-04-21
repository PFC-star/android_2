package com.example.distribute_ui.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.distribute_ui.data.Dim
import kotlin.math.roundToInt

@Composable
fun FinetuneScreen(){
    Column (modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally){
        Card (modifier = Modifier.requiredWidth((0.935*Dim.width).dp).requiredHeight((0.33*Dim.height).dp).padding(top = 10.dp),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(2.dp, Color(0xFFBEBEBE))
        ){
            ChoseModel()
        }
        Spacer(modifier = Modifier.height(10.dp))
        Card (modifier = Modifier.requiredWidth((0.935*Dim.width).dp).requiredHeight((0.45*Dim.height).dp),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(2.dp, Color(0xFFBEBEBE))
        ){
            ChoseData()
        }
    }
}

@Composable
fun ChoseModel(){
    Column (modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally){
        Spacer(modifier = Modifier.height(5.dp))
        Text("Choose the model you want to finetune:", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        CardRow()
        Row (modifier = Modifier.fillMaxSize()){
            Card(
                shape = RoundedCornerShape(50.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFB9B8BA)),
                modifier = Modifier
                    .requiredWidth((0.35*Dim.width).dp).requiredHeight((0.048*Dim.height).dp)
                    .padding(start = 60.dp, bottom = 4.dp)
            ){
                Column (modifier = Modifier.fillMaxSize().clickable {  },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally){
                    Text("Cancel",
                        fontSize = 13.sp,
                        color = Color.White,
                    )
                }
            }
            Spacer(modifier = Modifier.width(65.dp))
            Card(
                shape = RoundedCornerShape(50.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFD4C159)),
                modifier = Modifier
                    .requiredWidth((0.4*Dim.width).dp).requiredHeight((0.048*Dim.height).dp)
                    .padding(bottom = 4.dp)
            ){
                Column (modifier = Modifier.fillMaxSize().clickable {  },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally){
                    Text("Download the submodel",
                        fontSize = 12.sp,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
fun ChoseData(){
    Column (modifier = Modifier.fillMaxSize()){
        ModelSelection_2()
        Text("Choose data for finetuning:", fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Row {
            Column (modifier = Modifier.fillMaxHeight().requiredWidth((0.4675*Dim.width).dp).background(Color.White),
                horizontalAlignment = Alignment.CenterHorizontally){
                Text("Chat History", fontSize = 10.sp, color = Color(0xFFAAA7A7),
                    modifier = Modifier.align(Alignment.Start).padding(start = 6.dp))
                RoundedScrollableVerticalOptionBar()
            }
            Column (modifier = Modifier.fillMaxHeight().requiredWidth((0.4675*Dim.width).dp).background(Color.White),
                horizontalAlignment = Alignment.CenterHorizontally){
                Row (modifier = Modifier.padding(top = 10.dp)){
                    Icon(Icons.Default.Add, "")
                    Text("upload your data")
                }
                Row (modifier = Modifier.padding(top = 4.dp)){
                    Icon(Icons.Default.Add, "")
                    Text("choose other file")
                }
                Spacer(modifier = Modifier.height(10.dp))
                RingProgressIndicatorWithButton(
                    progress = 0.27f,
                    onButtonClick = { },
                    modifier = Modifier.size(150.dp)
                )
                Text("Progress: 27%", color = Color.Black, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    shape = RoundedCornerShape(50.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFB9B8BA)),
                    modifier = Modifier
                        .requiredWidth((0.25*Dim.width).dp).requiredHeight((0.048*Dim.height).dp)
                ){
                    Column (modifier = Modifier.fillMaxSize().clickable {  },
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally){
                        Text("Cancel",
                            fontSize = 13.sp,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CardRow() {
    var selectedCardIndex by remember { mutableStateOf(1) } // 初始选中第二张卡片

    Column (
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 15.dp, end = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GradientCard(
                text = "Model_1",
                isSelected = selectedCardIndex == 0,
                gradientColors = listOf(Color(0xFF686D6F), Color(0xFFFFFFFF)),
                onClick = { selectedCardIndex = 0 }
            )
            GradientCard(
                text = "Model_2",
                isSelected = selectedCardIndex == 1,
                gradientColors = listOf(Color(0xFFFFFFFF), Color(0xFF686D6F)),
                onClick = { selectedCardIndex = 1 }
            )
            GradientCard(
                text = "Model_3",
                isSelected = selectedCardIndex == 2,
                gradientColors = listOf(Color(0xFF686D6F), Color(0xFFFFFFFF)),
                onClick = { selectedCardIndex = 2 }
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        IndicatorDots(
            totalDots = 3,
            selectedIndex = selectedCardIndex
        )
    }
}

@Composable
fun GradientCard(text: String, isSelected: Boolean, gradientColors: List<Color>, onClick: () -> Unit) {
    val borderModifier = if (isSelected) {
        Modifier.border(
            width = 2.dp,
            color = Color.White,
            shape = RoundedCornerShape(16.dp) // 保持与 Card 的圆角一致
        )
    } else {
        Modifier
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (isSelected) 20.dp else 4.dp), // 选中时提升阴影效果
        modifier = Modifier
            .requiredWidth(if(isSelected) (0.2625*Dim.width).dp else (0.25*Dim.width).dp).
            requiredHeight(if(isSelected) (0.186*Dim.height).dp else (0.1557*Dim.height).dp)
            .clickable { onClick() }
            .then(borderModifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(gradientColors) // 渐变背景色
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = Color.Black,
                fontSize = if (isSelected) 18.sp else 14.sp, // 选中时字体变大
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun IndicatorDots(totalDots: Int, selectedIndex: Int) {
    Row(
        modifier = Modifier.wrapContentWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(totalDots) { index ->
            androidx.compose.foundation.Canvas(modifier = Modifier
                .size(15.dp)
                .padding(horizontal = 4.dp)) {
                drawCircle(color = if (index == selectedIndex) Color(0xFF000000) else Color.LightGray,
                    radius = if (index == selectedIndex) size.minDimension / 2 else size.minDimension / 2.5f)
            }
        }
    }
}

@Composable
fun ModelSelection_2(){
    // 下拉菜单选项列表
    val options = listOf("Model_1", "Model_2", "Model_3")
    // 当前选中的选项（默认选择第一个）
    var selectedOption by remember { mutableStateOf(options[1]) }
    // 下拉菜单展开状态
    var expanded by remember { mutableStateOf(false) }
    Card (modifier = Modifier.padding(start = 340.dp)
        .requiredHeight((0.03*Dim.height).dp).requiredWidth((0.3375*Dim.width).dp).clickable { expanded = true },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ){
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowForwardIos,
                contentDescription = "",
                tint = Color(0x80939393),
                modifier = Modifier.size(22.dp).padding(start = 6.dp)
            )
            // 显示当前选项的文本
            Text(text = selectedOption, color = Color(0xFF939393), fontSize = 12.sp)

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
}

@Composable
fun RoundedScrollableVerticalOptionBar() {
    // 定义7个选项
    val options = listOf("Chat1", "Chat2", "Chat3", "Chat4", "Chat5", "Chat6", "Chat7")
    // 用于保存每个选项是否选中的状态（可根据需要调整为单选或多选）
    val checkedStates = remember { mutableStateListOf(false, true, false, true, true, false, false) }
    // 竖直滚动状态
    val scrollState = rememberScrollState()

    // 外部容器：圆角白色背景
    Box(
        modifier = Modifier
            .padding(top= 6.dp, start = 10.dp, end = 10.dp, bottom = 15.dp)
            .clip(RoundedCornerShape(16.dp, 0.dp, 0.dp, 16.dp))
            .background(Color.White)
            .fillMaxWidth()
            .height((0.36*Dim.height).dp)
            .border(1.dp, color = Color(0x80AAA7A7), RoundedCornerShape(16.dp, 0.dp, 0.dp, 16.dp))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(8.dp)
            ) {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { checkedStates[index] = !checkedStates[index] }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 左侧文本
                        Text(text = option, color = Color.Black)
                        // 右侧正方形勾选框
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color(0xFFD4C159)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (checkedStates[index]) {
                                Text(text = "√", color = Color.White, fontSize = 16.sp)
                            }
                        }
                    }
                    // 分割线（除最后一项外）
                    if (index < options.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.Gray)
                        )
                    }
                }
            }
            // 滑动指示器部分：竖直显示当前滑动位置
            VerticalScrollIndicator(scrollState = scrollState)
        }
    }
}

@Composable
fun VerticalScrollIndicator(scrollState: androidx.compose.foundation.ScrollState) {
    // 定义指示器容器宽度
    val indicatorWidth = 16.dp
    // 用于记录指示器容器的高度（以像素为单位）
    var indicatorHeightPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current

    // 根据 scrollState 计算滚动进度（0~1）
    val progressFraction = if (scrollState.maxValue > 0)
        scrollState.value.toFloat() / scrollState.maxValue.toFloat() else 0f

    Box(
        modifier = Modifier
            .width(indicatorWidth).background(Color.White)
            .fillMaxHeight()
            .onGloballyPositioned { coordinates ->
                indicatorHeightPx = coordinates.size.height.toFloat()
            },
        contentAlignment = Alignment.TopEnd
    ) {
        // 绘制轨道：一条竖直的浅灰色条
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(Color.LightGray)
        )
        // 滑块：一个深灰色的小条，根据滚动进度垂直偏移
        val thumbHeight = 20.dp
        val thumbHeightPx = with(density) { thumbHeight.toPx() }
        val maxOffset = indicatorHeightPx - thumbHeightPx
        val offsetPx = progressFraction * maxOffset

        Box(
            modifier = Modifier
                .offset { IntOffset(x = 0, y = offsetPx.roundToInt()) }
                .width(4.dp)
                .height(thumbHeight)
                .background(Color.DarkGray)
        )
    }
}

@Composable
fun RingProgressIndicatorWithButton(
    progress: Float,               // 进度值：0f ~ 1f
    onButtonClick: () -> Unit,     // 按钮点击回调
    modifier: Modifier = Modifier,
    progressColor: Color = Color(0xFF65558F),  // 深色，代表已完成进度
    trackColor: Color = Color(0xFFE8DEF8),    // 浅色，代表轨道
    strokeWidth: Dp = 6.dp,
    buttonSize: Dp = 125.dp,        // 内部圆形按钮的尺寸
    buttonColor: Color = Color(0xFFE9FCFF)  // 按钮背景色
) {
    // 使用 Box 叠加绘制进度条和内部按钮
    androidx.compose.foundation.layout.Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // 绘制完整轨道（底层），使用官方 CircularProgressIndicator
        CircularProgressIndicator(
            progress = 1f,
            modifier = Modifier.fillMaxSize(),
            color = trackColor,
            strokeWidth = strokeWidth,
        )
        // 绘制已完成进度（顶层）
        CircularProgressIndicator(
            progress = 0.27f,
            modifier = Modifier.fillMaxSize(),
            color = progressColor,
            strokeWidth = strokeWidth,
        )
        // 中间的圆形按钮
        Surface(
            modifier = Modifier
                .clickable { onButtonClick() }
                .clip(CircleShape)
                .then(Modifier.size(buttonSize)),
            shape = CircleShape,
            color = buttonColor,
            contentColor = Color.White,
            // 可选：添加阴影效果
            // elevation = 4.dp,  // 如果使用 Material2，可指定 elevation；Material3 使用 shadowElevation
            shadowElevation = 4.dp
        ) {
            // 按钮内部内容居中显示，可替换为 Icon 等
            androidx.compose.foundation.layout.Box(
                modifier = Modifier,
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Go!", color = Color.Black, fontSize = 35.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}