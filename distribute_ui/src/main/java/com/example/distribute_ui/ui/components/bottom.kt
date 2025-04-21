package com.example.distribute_ui.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

sealed class Bottom(
    val route:String,
    val icon:ImageVector,
    val title:String){

    object Chat:Bottom("Start", Icons.Outlined.Forum, "Chat")
    object Finetune:Bottom("Finetune", Icons.Outlined.Extension, "Finetune")
    object Settings:Bottom("Settings", Icons.Filled.Settings, "Settings")
}

@Composable
fun BottomBar(navController: NavController){
    val bottomlist = listOf(Bottom.Chat, Bottom.Finetune, Bottom.Settings)

    NavigationBar (containerColor = Color.White){
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        bottomlist.forEach {
            NavigationBarItem(
                selected = if (it != Bottom.Chat) (currentRoute == it.route) else {
                    currentRoute == "Start" || currentRoute == "Chat/{index}" || currentRoute == "Select"
                },
                label = { Text(it.title) },
                alwaysShowLabel = true,
                icon = { Icon(it.icon, "") },
                onClick = {
                    navController.navigate(it.route) {   // 导航到指定的目标路由并配置导航行为
                        // .let检查变量是否为空，若非空则执行{}内代码，并将该变量作为参数传入
                        // 获取navController的起始界面
                        navController.graph.startDestinationRoute?.let { startRoute ->   // 即为navController.graph.startDestinationRoute
                            popUpTo(startRoute) {    // 跳转前先弹出导航栈中的界面直到指定界面为止
                                saveState = true    // 弹出目标页面时，会保存该页面的状态以供恢复
                            }
                        }
                        launchSingleTop = true  // 保证界面在栈顶处中仅有一个实例，避免重复创建界面
                        restoreState = true     // 导航到界面时会恢复由popUpTo保存的状态
                    }
                },
                colors = NavigationBarItemDefaults.colors(indicatorColor = Color(0x0FD4C159))
            )
        }
    }
}