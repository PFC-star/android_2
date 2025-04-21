package com.example.distribute_ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.distribute_ui.data.PreferenceHelper
import com.example.distribute_ui.data.serverIp
import com.example.distribute_ui.service.BackgroundService
import com.example.distribute_ui.service.MonitorService
import com.example.distribute_ui.ui.InferenceViewModel
import com.example.distribute_ui.ui.components.SplashScreen
import com.example.distribute_ui.ui.theme.Distributed_inference_demoTheme
import com.google.accompanist.systemuicontroller.rememberSystemUiController


const val TAG = "StarDust"

// Define the permission request code
private const val MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1 // or any other unique integer

// 应用程序的主入口，继承自ComponentActivity类并实现LatencyMeasurementCallbacks接口
class SelectionActivity : ComponentActivity(), LatencyMeasurementCallbacks {
    // Intent类：组件间通信的基础类。用于在组件之间传递消息、启动活动、启动服务和发送广播
    private var monitorIntent: Intent? = null       // 负责monitor服务的Intent对象
    private var backgroundIntent: Intent? = null    // 负责background服务的Intent对象
    // 通过委托获取的InferenceViewModel实例，用于管理和保存UI数据；通过委托使其生命周期与Activity绑定
    private val viewModel : InferenceViewModel by viewModels()

    private var service: MonitorService? = null // MonitorService的服务实例
    private var serviceBound = false            // 标记服务是否已经成功绑定（service是否存在）

    private var id = 0 // 标记设备为头结点还是参与节点: 1 -> header, 0 -> worker
    private var modelName = ""  // 记录模型名称

    // 一个ServiceConnection的实例对象
    private val serviceConnection = object : ServiceConnection {
        // 当服务连接成功时，会通过iBinder获取到服务实例，并设置serviceBound为true
        override fun onServiceConnected(className: ComponentName, iBinder: IBinder) {
            Log.d(TAG, "monitor service connection is successful")

//            val binder = service as MonitorActions.MyBinder
//            service = binder.getService()

            val binder = iBinder as MonitorService.LocalBinder
            service = binder.getService()
            serviceBound = true

            // Fetch data from service and update the ViewModel, upload memory and CPU frequency
//            val memory = service?.getAvailableMemory()
//            val freq = service?.getFrequency()
//            viewModel.prepareUploadData(memory ?: 0, freq ?: 0.0)
        }

        // 当服务断开时，将 serviceBound 设置为 false
        override fun onServiceDisconnected(arg0: ComponentName) {
            serviceBound = false
        }
    }

    // 一个BroadcastReceiver的实例对象，用于接收广播
    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        // 收到广播后，会启动monitorIntent并传递当前的设备id
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "selectionActivity receives the broadcast")
            monitorIntent!!.putExtra("role", id)   // 在Intent中添加名为"role"的额外数据，值为id
            startService(monitorIntent)                  // 通过monitorIntent启动后台服务MonitorService
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 申请读写外部存储的权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(BackgroundService.TAG, "write external storage denied")
            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            ) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else {
                // No explanation needed; request the permission
                ActivityCompat.requestPermissions(
                    this, arrayOf<String>(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE
                )

                // MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        } else {
            // Permission has already been granted
            Log.d(BackgroundService.TAG, "write external storage permit is ok")
        }

        backgroundIntent = Intent(this, BackgroundService::class.java)  // 指向BackgroundService的Intent实例
        monitorIntent = Intent(this ,MonitorService::class.java)        // 指向MonitorService的Intent实例
//        startService(monitorIntent)

        // 过滤器用于选择确定接收的广播。它指定接收动作为'START_MONITOR'的广播，即任何发送了该动作广播的组件都会触发该接收器
        val filter = IntentFilter("START_MONITOR")
        // 先获取应用级发送接收广播的类，再将广播接收器receiver和过滤器进行绑定。每当一个符合过滤器条件的广播被发送时,receiver将会接收到该广播并执行相应的处理逻辑
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter)


        WindowCompat.setDecorFitsSystemWindows(window, false)
        serverIp = PreferenceHelper.loadServerIp(this)
        setContent {
            var showSplash by remember { mutableStateOf(true) }
            val systemUiController = rememberSystemUiController()
            systemUiController.setSystemBarsColor(
                color = Color.Transparent,
                darkIcons = false
            )

            if(showSplash){
                SplashScreen(onAnimationEnd = { showSplash = false})
            }
            else {
                HomeScreen(
                    onMonitorStarted = {    // 启动MonitorService，传递id
                        monitorIntent!!.putExtra("role", id)
                        startService(monitorIntent)
                    },
                    onBackendStarted = {    // 若未有正在运行的BackgroundService，则启动，传递id与模型名
                        if (!BackgroundService.isServiceRunning) {
                            backgroundIntent!!.putExtra("role", id)
                            backgroundIntent!!.putExtra("model", modelName)
                            backgroundIntent!!.putExtra("ip", serverIp)
                            startService(backgroundIntent)
                        }
                    },
                    onModelSelected = { // 设置模型名
                        setModel(it)
                    },
                    onRolePassed = {    // 设置角色
                        setRole(it)
                    },
                    viewModel = viewModel
                )
            }
        }
    }

    private fun setRole(id: Int) {      // 设置属性id的值
        this.id = id
        Log.d(TAG, "id is $id")
    }

    private fun setModel(modelName: String) {   // 设置属性modelName的值
        this.modelName = modelName
        Log.d(TAG, "model name is $modelName")
    }

    // 在Activity被销毁时，注销广播接收器，并解绑Monitor服务实例，停止background服务
    override fun onDestroy() {
        super.onDestroy()
//        unregisterReceiver(receiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
//        stopService(monitorIntent)
        stopService(backgroundIntent)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onLatencyMeasured(latency: Double) {   // 用latency更新viewModel的延迟数据
        viewModel.updateLatency(latency)
    }

    companion object {
        init {
            System.loadLibrary("distributed_inference_demo")    // 加载本地库
        }
    }
    // 声明外部函数（JNI方法）
    external fun createSession(inference_model_path:String): Long   // 创建会话，返回
    external fun modelFlopsPerSecond(modelFlops: Int, session: Long, data: ByteArray?): Double  // 计算模型每秒浮点计算次数
}

interface LatencyMeasurementCallbacks {
    fun onLatencyMeasured(latency: Double)
}

