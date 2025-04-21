package com.example.distribute_ui

import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.distribute_ui.data.Dim
import com.example.distribute_ui.data.exampleModelName
import com.example.distribute_ui.ui.ChatScreen
import com.example.distribute_ui.ui.FinetuneScreen
import com.example.distribute_ui.ui.InferenceViewModel
import com.example.distribute_ui.ui.ModelSelectionScreen
import com.example.distribute_ui.ui.NodeSelectionScreen
import com.example.distribute_ui.ui.SettingsScreen
import com.example.distribute_ui.ui.components.BottomBar
import com.example.distribute_ui.ui.components.TopBar
import com.example.distribute_ui.ui.theme.Distributed_inference_demoTheme
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.*

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavHostController = rememberNavController(),
    onMonitorStarted: () -> Unit,
    onBackendStarted: () -> Unit,
    onModelSelected: (modelName: String) -> Unit,   // 参数为String类型
    viewModel: InferenceViewModel,
    onRolePassed: (id: Int) -> Unit                 // 参数为Int类型
) {
    val configuration = LocalConfiguration.current
    Dim.width = configuration.screenWidthDp
    Dim.height = configuration.screenHeightDp
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val systemUiController = rememberSystemUiController()
    systemUiController.setSystemBarsColor(
        color = Color.Transparent,
        darkIcons = false
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color.White,
                drawerShape = RoundedCornerShape(0.dp, 70.dp, 70.dp, 0.dp),
                modifier = Modifier.requiredWidth(330.dp)) {
                DrawerContent()
            }
        }
    ){
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopBar(scope, drawerState, navController)
            },
            bottomBar = {
                BottomBar(navController)
            }
        ) { contentPadding ->
//        val uistate by viewModel.uiState.collectAsState()

            NavHost(
                navController = navController,
                startDestination = "Start",
                modifier = Modifier.padding(contentPadding)
            ) {
                Log.d(TAG, "recompose in NavHost")
                composable(route = "Start") {
                    NodeSelectionScreen(
                        viewModel = viewModel,
                        onHeaderClicked = {
//                        viewModel.nodeId = 1
                        },
                        onWorkerClicked = {
//                        viewModel.nodeId = 0
                        },
                        onCancelClicked = {
                            viewModel.resetOption()
                        },
                        onNextClicked = {

                            onRolePassed(it)
                            if (it == 1) {
                                navController.navigate("Select")
                            }
                        },
                        onMonitorStarted = onMonitorStarted,
                        onBackendStarted = onBackendStarted,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(dimensionResource(R.dimen.padding_medium))
                    )
                }
                composable(route = "Select") {
                    ModelSelectionScreen(
                        viewModel = viewModel,
                        options = exampleModelName,
                        onCancelClicked = {
                            navController.navigate("Start")
                            viewModel.resetOption()
                        },
                        onNextClicked = {
                            Log.d(TAG, "Navigating to Chat screen")
                            navController.navigate("Chat/${it}")
                        },
                        onBackendStarted = {
                            onBackendStarted()
                        },
                        onModelSelected = {
                            onModelSelected(it)
                        },
                        modifier = Modifier.fillMaxHeight()
                    )
                }
                composable(route = "Chat/{index}", arguments = listOf(navArgument("index"){type = NavType.StringType})) {
                        NavBackStackEntry: NavBackStackEntry ->
                    val index = NavBackStackEntry.arguments?.getString("index")
                    index?.let{
                        ChatScreen(index, viewModel = viewModel)
                    }
                }
                composable(route = "Finetune"){
                    FinetuneScreen()
                }
                composable(route = "Settings"){
                    SettingsScreen(
                        viewModel = viewModel,
                        onHeaderClicked = {
//                        viewModel.nodeId = 1
                        },
                        onWorkerClicked = {
//                        viewModel.nodeId = 0
                        },
                        onCancelClicked = {
                            viewModel.resetOption()
                        },
                        onNextClicked = {

                            onRolePassed(it)
                            if (it == 1) {
                                navController.navigate("Select")
                            }
                        },
                        onMonitorStarted = onMonitorStarted,
                        onBackendStarted = onBackendStarted,
                    )
                }
            }
        }
    }
}

@Composable
fun DrawerContent(){
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, start = 16.dp, end = 2.dp)
    ) {
        Text(text = "Navigator", color = Color(0xFF49454F), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        DrawerItem(icon = Icons.Outlined.Extension, str = "Finetune", color = Color(0xFFF4F4F4))
        DrawerItem(icon = Icons.Filled.Settings, str = "Profile", color = Color.White)
        Divider(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "ChatHistory", color = Color(0xFF49454F), fontWeight = FontWeight.Bold)
        for(i in 1..7){
            DrawerItem(Icons.Outlined.Sms, "Chat_${i}", Color.White)
        }
        Divider(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Preference", color = Color(0xFF49454F), fontWeight = FontWeight.Bold)
        for(i in 1..3){
            DrawerItem(Icons.Filled.Tune, "Setting_${i}", Color.White)
        }
    }
}

@Composable
fun DrawerItem(icon: ImageVector, str: String, color : Color){
    Card (modifier = Modifier.fillMaxWidth().requiredHeight(50.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(25.dp)
    ){
        Row(modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(15.dp))
            Icon(icon, "")
            Spacer(modifier = Modifier.width(5.dp))
            Text(str)
        }
    }
}