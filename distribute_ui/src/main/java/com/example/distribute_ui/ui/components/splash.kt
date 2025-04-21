package com.example.distribute_ui.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.distribute_ui.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onAnimationEnd: () -> Unit){
    val moFont = FontFamily(
        Font(R.font.mono)
    )
    val pingFont = FontFamily(
        Font(R.font.pingfang_regular)
    )

    var startAnimation by remember { mutableStateOf(false) }


    LaunchedEffect(Unit) {
        startAnimation = true
        delay(100) // 停留时间
        onAnimationEnd() // 动画结束后回调
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0A0C))
            .padding(top = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 50.dp) // 图片距离屏幕顶部 100dp
        ) {
            Image(
                painter = painterResource(id = R.drawable.icon_1),  // 替换为你的图片资源
                contentDescription = "",
                modifier = Modifier
                    .padding(start = 50.dp, end = 50.dp)
                    .size(475.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 365.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "StarDust",
                    color = Color.White,
                    fontSize = 42.sp, // 字号 20sp
                    fontFamily = moFont,
                    letterSpacing = 6.sp,
                    fontWeight = FontWeight.Bold // 加粗
                )
                Spacer(modifier = Modifier.height(20.dp)) // 第二行文本间隔 20dp
                Text(
                    text = "让世界没有推不起来的模型",
                    color = Color.White,
                    fontSize = 20.sp, // 字号 16sp
                    fontWeight = FontWeight.Normal,
                    fontFamily = pingFont,
                    letterSpacing = 4.sp
                )
            }
        }
    }
}