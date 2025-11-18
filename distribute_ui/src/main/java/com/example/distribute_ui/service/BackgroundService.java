/**
 * BackgroundService 是分布式推理系统的核心后台服务
 * 主要功能：
 * 1. 设备初始化和与服务器通信
 * 2. 模型加载和准备
 * 3. 接收用户输入并进行推理
 * 4. 处理故障恢复
 * 
 * 该服务处理两种模式：
 * - 工作模式(working)：正常参与推理计算
 * - 活跃模式(active)：待命状态，随时准备替代故障设备
 * 
 * 设备角色：
 * - 头节点(header)：接收用户输入，处理模型开始部分
 * - 工作节点(worker)：处理模型中间层
 */
package com.example.distribute_ui.service;
import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;

import com.example.SecureConnection.Communication;
import com.example.SecureConnection.Config;
import com.example.SecureConnection.Dataset;
import com.example.SecureConnection.LoadBalance;
import com.example.distribute_ui.DataRepository;
import com.example.distribute_ui.Events;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Properties;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class BackgroundService extends Service {    // 继承自Service，表明为一个服务
    public static double[] results;                 // 存储推理结果
    public static final String TAG = "StarDust_backend";
    private String role = "worker";                 // 设备的角色，默认为"worker"
    private  String serverStatus = "active";           // 是否需要monitor服务
    private  String  monitor_port = "1000";           // 是否需要monitor服务
    private final boolean running_classification = false;   // 是否为分类任务
    private boolean shouldStartInference = false;   // 是否开始推理
    public static boolean runningStatus = false;          // 是否为运行状态
    public static boolean isScreenOff = false;          // 是否收到消息
    public static boolean isServiceRunning = false; // 服务是否正在运行
    private boolean isAppInBackground = false; // APP是否在后台
    private Thread backgroundCheckThread = null; // 后台检测线程
    private volatile boolean isBackgroundCheckRunning = true; // 后台检测线程运行标志
    private Thread energyMonitorThread = null;
    private volatile boolean isEnergyMonitorRunning = true;

    private String messageContent = "";             // 存储用户输入的消息内容

    // 1. 新增事件类（建议正式放到 Events.java）
    public static class ScreenOffEvent {
        private final boolean isScreenOff;
        public ScreenOffEvent(boolean isScreenOff) {
            this.isScreenOff = isScreenOff;
        }
        public boolean isScreenOff() { return isScreenOff; }
    }

    // 2. 在 BackgroundService 中添加广播接收器相关成员
    private android.content.BroadcastReceiver screenReceiver = null;

    // === 全局成员变量 ===
    private String serverIP = "";
    private String deviceIP = "";
    private ZMQ.Socket logSocket = null;
    private ZContext zmqContext = null;

    // === 静态成员变量，保存启动参数供Communication.java访问 ===
    public static Intent lastStartIntent = null;
    public static int lastStartFlags = 0;
    public static int lastStartId = 0;
    public static String lastRole = "";
    public static String lastModelName = "";
    public static String lastServerIP = "";

    /**
     * 监听RunningStatusEvent事件
     * 当Communication类初始化完成后，会发送此事件
     * 这个方法在后台线程中执行，用于更新服务的运行状态
     * 
     * @param event 包含运行状态的事件对象
     */
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onRunningStatus(Events.RunningStatusEvent event){
        runningStatus = event.isRunning;
        System.out.println("Running Status is: " + runningStatus);
    }

    /**
     * 监听messageSentEvent事件
     * 当用户在聊天界面发送消息时触发
     * 记录消息内容，用于后续推理处理
     * 
     * @param event 包含消息状态和内容的事件对象
     */
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onMessageSentEvent(Events.messageSentEvent event) {
        isScreenOff = event.messageSent;
        messageContent = event.messageContent;
        System.out.println("messageSent Status is: " + isScreenOff);
        System.out.println("message Content is: " + messageContent);
    }

    /**
     * 监听enterChatEvent事件
     * 当用户进入聊天界面时触发
     * 用于标记推理过程是否应该开始
     * 
     * @param event 包含进入聊天状态的事件对象
     */
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onEnterChatEvent(Events.enterChatEvent event) {
        shouldStartInference = event.enterChat;
        System.out.println("ShouldStartInference is: " + shouldStartInference);
    }

    /**
     * 获取服务器IP地址，优先级：Intent extra > Config类 > config.properties
     * @param intent 启动服务的Intent，可为null
     * @return 服务器IP地址字符串
     */
    private String getServerIPAddress(Intent intent) {
        String serverIP = null;
        // 1. 优先从Intent extra获取
        if (intent != null && intent.hasExtra("ip")) {
            serverIP = intent.getStringExtra("ip");
            if (serverIP != null && !serverIP.isEmpty()) {
                return serverIP;
            }
        }
        // 2. 其次用Config.root
        try {
            Class<?> configClass = Class.forName("com.example.SecureConnection.Config");
            serverIP = (String) configClass.getField("root").get(null);
            if (serverIP != null && !serverIP.isEmpty()) {
                return serverIP;
            }
        } catch (Exception e) {
            // 忽略，进入下一个兜底
        }
        // 3. 最后兜底config.properties
        Properties properties = new Properties();
        try {
            InputStream inputStream = getAssets().open("config.properties");
            properties.load(inputStream);
            serverIP = properties.getProperty("server_ip");
            inputStream.close();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
        return serverIP != null ? serverIP : "";
    }

    /**
     * 检查模型目录是否为空
     * 用于判断模型文件是否已下载完成
     * 
     * @param modelPath 模型文件路径
     * @return 如果目录为空返回true，否则返回false
     */
    private boolean isModelDirectoryEmpty(String modelPath) {
        File modelDir = new File(modelPath + "/device");
        if (modelDir.isDirectory()) {
            String[] files = modelDir.list();
            return files == null || files.length == 0;
        }
        // Return true if it's not a directory, indicating "empty" in this context.
        return true;
    }
    // 当服务启动（通过startService(Intent)或bind）时调用的方法
    /**
     * 更新模型目录状态到数据仓库
     * 当模型准备就绪后，通知UI更新
     * 
     * @param isDirEmpty 目录是否为空
     */
    private void updateIsDirEmpty(boolean isDirEmpty) {
        // Update the repository with the new value
        DataRepository.INSTANCE.setIsDirEmpty(isDirEmpty);
    }

    /**
     * 服务启动时执行的回调方法
     * 负责初始化推理环境并启动推理过程
     * 
     * 整体流程：
     * 1. 获取设备角色、模型和服务器信息
     * 2. 创建配置对象和通信对象
     * 3. 向服务器注册并获取工作状态(working/active)
     * 4. 根据状态执行不同的初始化流程
     * 5. 等待模型准备完成
     * 6. 对于头节点，等待用户输入开始推理
     * 7. 执行实际推理任务
     * 
     * @param intent 包含启动参数的Intent
     * @param flags 启动标志
     * @param startId 启动ID
     * @return 服务启动模式
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {  // flags和startId（用于标识服务，在终止服务时需要）由系统自动传递
        Log.d(TAG, "background service started");
        Log.d(TAG, "启动参数 - intent: " + intent + ", flags: " + flags + ", startId: " + startId);
        //        看一下参数都有什么
//        然后在另外一个函数中重启

        // 保存启动参数到静态变量的值 
        lastStartIntent = intent;
        lastStartFlags = flags;
        lastStartId = startId;
        isServiceRunning = true;
        
        int id;
        if (intent != null && intent.hasExtra("role")) {
            id = intent.getIntExtra("role", 0);
        } else {
            id = 0;
        }
        if (id == 1) {  // 若id为1，将角色改为头结点
            role = "header";
        }
        Log.d(TAG, "role is " + role);

        // 获取模型名称
        String modelName = "";
        if (intent != null && intent.hasExtra("model")) {   // 提取Intent中附加的额外信息"model"的值
            modelName = intent.getStringExtra("model");     // 获取模型名称
            System.out.println("model name is: "+ modelName);
        }

        // 获取服务器IP，优先级：Intent > Config > config.properties
        serverIP = null;
        if (intent != null && intent.hasExtra("ip")) {
            serverIP = intent.getStringExtra("ip");
        }
        if (serverIP == null || serverIP.isEmpty()) {
            try {
                Class<?> configClass = Class.forName("com.example.SecureConnection.Config");
                serverIP = (String) configClass.getField("root").get(null);
            } catch (Exception e) {
                // 忽略，进入下一个兜底
            }
        }
        if (serverIP == null || serverIP.isEmpty()) {
            Properties properties = new Properties();
            try {
                InputStream inputStream = getAssets().open("config.properties");
                properties.load(inputStream);
                serverIP = properties.getProperty("server_ip");
                inputStream.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
        if (serverIP == null) serverIP = "";
        System.out.println("root ip: "+ serverIP);

        deviceIP = Config.local;
        System.out.println("deviceIP ip: "+ deviceIP);
        // 保存所有参数到静态变量
        lastRole = role;
        lastModelName = modelName;
        lastServerIP = serverIP;
        
        Log.d(TAG, "启动参数已保存 - role: " + lastRole + ", model: " + lastModelName + ", serverIP: " + lastServerIP);

        // === 新增：保存参数到 SharedPreferences ===
        android.content.SharedPreferences prefs = getApplicationContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("role", id);
        editor.putString("model", modelName);
        editor.putString("ip", serverIP);
        editor.putString("device_ip", deviceIP);
        editor.apply();
        // 创建一个单线程的线程池，池中的所有任务按顺序执行，每次最多只有一个正在执行的任务
        // 通过将任务提交给线程池执行，当前线程可以继续执行其他操作而不被阻塞，线程池会自动管理工作线程的生命周期
        ExecutorService executor = Executors.newSingleThreadExecutor();
        String finalModelName = modelName;  // 模型名称
        executor.submit(() -> {             // 提交一个任务到executor（lambda形式）

            // k为top-k采样的参数
            // initial_temp为temperature参数
            // 实例化一个配置类，服务器地址为server_ip:23456，top-k采样，此外还包括自身ip:端口号
            Config cfg = new Config(serverIP, 23456, 7, 0.7f);

            Communication com = new Communication(cfg, this, finalModelName, id); // 根据配置cfg实例化一个Communication
            Communication.loadBalance = new LoadBalance(com, cfg);  // 根据com和cfg实例化一个LoadBalance
            com.param.modelPath = getFilesDir() + "";   // 以字符串形式返回应用程序的私有文件存储目录
//            com.param.modelPath =  "/sdcard";
            Log.d(TAG, "Storage path is:" + com.param.modelPath);

            // 1. send IP to server to request model
            // 与服务器建立连接，发送自身ip（对头结点还需加上模型名称），根据从服务器接受信息决定need_monitor为true/false
            if (role.equals("header")) {
                monitor_port = com.sendIPToServer(role, finalModelName); // 头节点需要提供模型名称
            } else {
                monitor_port = com.sendIPToServer(role, ""); // 工作节点不需要提供模型名称
            }
            Log.d(TAG, "monitor_port = " + monitor_port);
//          启动控初始化通信
            com.runPrepareThread(monitor_port);


            // 2. Initiate device monitor for server-side optimization
            // 若need_monitor为true，则发送action为"START_MONITOR"的广播，
            // MainActivity中的receiver在接收到该广播后将启动MonitorService并附加role信息
//            if (need_monitor) {
//                Intent broadcastIntent = new Intent();
//                broadcastIntent.setAction("START_MONITOR"); // 设置广播的"action"
//                LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
//                sendBroadcast(broadcastIntent);
//                Log.d(TAG, "broadcast sent by backgroundService");
//            }
//            if (serverStatus.equals("working")){
//                Log.d(TAG, "serverStatus :working ");
////              初始化阶段
////                1. 传输控制信号 34567
////                    1.1 Ready->Open->Prepare->Initialized->Start->Running
////
//                // 3.1 start downloading required model and tokenizer files from server
//                // 执行Client.communicationOpenClose中param.status.equals("Ready")对应代码，包括准备模型文件和分词器等从初始化工作
//                com.runPrepareThread(serverStatus);
//
//            }
//
//            if (serverStatus.equals("active")){
//                Log.d(TAG, "serverStatus :active ");
//                // 3.1 start downloading required model and tokenizer files from server
//                // 执行Client.communicationOpenClose中param.status.equals("Ready")对应代码，包括准备模型文件和分词器等从初始化工作
//                com.runPrepareThread(serverStatus);
////              运行阶段
////                1. 传输控制信号 34567
////                    1.1 Ready->Open->Prepare->Initialized 到这里但是不启动推理
////                    ->Start->Running
//
//
//
//
//            }





            // 3.2 Check whether the model file exists
            // 当param.status == "Running"时会收到事件RunningStatusEvent->runningStatus=true
            // 然后检查模型文件是否准备完毕

            while (!runningStatus) {
                try {
                    Thread.sleep(1000); // Sleep for a short duration to avoid busy waiting
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Restore the interrupted status
                    break; // Exit the loop if the thread is interrupted
                }
            }
            
            // 检查模型文件是否已准备就绪
            boolean isDirEmpty = isModelDirectoryEmpty(com.param.modelPath);
            Log.d(TAG, "check the direction is empty: " + isDirEmpty);
            if (runningStatus && !isDirEmpty){
                System.out.println("Prepare is Finished.");
                // 若为头结点，更新DataRepository中isDirEmptyLiveData的值->ModelScreen的ConfirmButton可点击->发送事件enterChatEvent
                // -> shouldStartInference=true
                if (cfg.isHeader()){
                    updateIsDirEmpty(isDirEmpty);
                }
            }

            // 对于头节点，等待用户确认开始推理
            // 当用户点击开始推理按钮时，会发送enterChatEvent事件，将shouldStartInference设为true
            if (cfg.isHeader()) {
                while (!shouldStartInference) {
                    try {
                        Thread.sleep(1000); // Sleep for a short duration to avoid busy waiting
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // Restore the interrupted status
                        break; // Exit the loop if the thread is interrupted
                    }
                }
            }

            // 对头结点的推理过程
            if (shouldStartInference && cfg.isHeader()){
                // 设置分类标签
                com.param.classes = new String[]{"Negative", "Positive"};
                // 4.2 Dataset would be used if we need conduct evaluation experiment
                Dataset dataset = null;

                // 等待直到numSample > 0
                while (com.param.numSample <= 0)
                    Thread.sleep(1000);

                System.out.println("batch size is: " + com.param.numSample);
                // 4.3 Create input string array to store user input query. By default, the array size
                // is set to 1 for testing single-turn chat conversation.

                // 4.4 Based on whether user give input to run the inference
                ArrayList<String> test_input = new ArrayList<>();

                // 4.4.1 Receive userinput from chatscreen and save it to test_input array
                // 等待直到用户按下send按钮->发送事件messageSentEvent->messageStatus=true
                while (!isScreenOff) {
                    try {
                        Thread.sleep(1000); // Sleep for a short duration to avoid busy waiting
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // Restore the interrupted status
                        break; // Exit the loop if the thread is interrupted
                    }
                }

                // 创建线程处理用户输入
                if (cfg.isHeader()) {
                    new Thread(() -> {
                        int j = 0;  // 记录当前批次序号
                        String userinput = "";
                        // 自动输入相关变量
                        // int autoInputCount = 1;
                        // boolean autoInputEnabled = true; // 可加开关
                        while (j < com.param.numSample) {           // 共执行numSample(BatchSize)次
                            // 检查是否有新的用户输入
                            if (messageContent.equals(userinput)){
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                            } else {
                                // 收到新消息，处理并添加到输入列表
                                System.out.println("current numSample:" + j + ", New prompt:" + messageContent);
                                Log.d(TAG, "[AutoInput] 用户输入检测到新消息: " + messageContent);
                                userinput = messageContent;
                                test_input.add(userinput);      // 将prompt加入列表中
                                Log.d(TAG, "[AutoInput] test_input已更新, 当前size: " + test_input.size());
                                j++;                            // 将当前批次的计数+1
                            }

                            // // 自动输入逻辑
                            // if (autoInputEnabled && j < com.param.numSample) {
                            //     // 检查推理线程是否处于等待输入
                            //     Log.d(TAG, "在autoInputEnabled里");
                            //     if (com.sampleId >= test_input.size()) {
                            //         Log.d(TAG, "[AutoInput] 检测到推理线程等待输入, sampleId=" + com.sampleId + ", test_input.size=" + test_input.size());
                            //         // 生成自动输入内容
                            //         String autoMsg = "模拟用户输入" + autoInputCount;
                            //         autoInputCount++;
                            //         // 发送事件，模拟用户输入
                            //         messageContent = autoMsg;
                            //         EventBus.getDefault().post(new Events.messageSentEvent(true, autoMsg));
                            //         Log.d(TAG, "[AutoInput] 自动输入已发送: " + autoMsg);
                            //         // 等待推理线程处理本轮输入
                            //         while (com.sampleId < test_input.size()) {
                            //             Log.d(TAG, "[AutoInput] 等待推理线程处理自动输入, sampleId=" + com.sampleId + ", test_input.size=" + test_input.size());
                            //             try {
                            //                 Thread.sleep(500);
                            //             } catch (InterruptedException e) {
                            //                 throw new RuntimeException(e);
                            //             }
                            //         }
                            //         // 推理完成后等待10秒再自动输入下一条
                            //         Log.d(TAG, "[AutoInput] 推理完成, 等待10秒后准备下一个自动输入");
                            //         try {
                            //             Thread.sleep(10000);
                            //         } catch (InterruptedException e) {
                            //             throw new RuntimeException(e);
                            //         }
                            //     }
                            // }
                        }
                    }).start();
                }
                
                // 设置线程池参数并启动推理
                int corePoolSize = 2;      // 核心线程数
                int maximumPoolSize = 2;   // 最大线程数
                int keepAliveTime = 500;   // 线程空闲超时
                try {
                    Log.w(TAG, "onStartCommand里进去的 communication starts to running");
                    // 启动实际推理任务，传入线程池参数和输入数据，这个不是线程，是直接启动了，但是包裹在 ExecutorService executor里
                    com.running(corePoolSize, maximumPoolSize, keepAliveTime, test_input);
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
                double startTime = System.nanoTime();
                results = com.timeUsage;   // 保存时间统计结果

                Log.d(TAG, "Results Computation Time: " + (System.nanoTime() - startTime) / 1000000000.0);
                return null;
            }

            // 非头节点推理流程
            // 工作节点不需要用户输入，直接执行推理任务
            else if (!shouldStartInference && !cfg.isHeader()){
                com.param.classes = new String[]{"Negative", "Positive"};
                Dataset dataset = null;
                // 等待批处理大小设置完成
                while (com.param.numSample <= 0)
                    Thread.sleep(1000);
                
                // 工作节点不需要实际的输入数据，但需要提供一个空列表
                ArrayList<String> test_input = new ArrayList<>();
                int corePoolSize = 2;
                int maximumPoolSize = 2;
                int keepAliveTime = 500;

                try {

                    // 启动推理任务
                    Log.w(TAG, "onStartCommand里进去的 communication starts to running");
                    com.running(corePoolSize, maximumPoolSize, keepAliveTime, test_input);
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
                results = com.timeUsage;
                return null;
            }
            return null;
        });

        // 初始化ZeroMQ日志socket
        if (zmqContext == null) {
            zmqContext = new ZContext();
        }
        if (logSocket == null) {
            logSocket = zmqContext.createSocket(SocketType.DEALER);
            String connectStr = "tcp://" + serverIP + ":9889";
            logSocket.connect(connectStr);
            Log.d(TAG, "Log socket connected to " + connectStr);
        }

        return START_STICKY; // 如果系统杀死服务，会尝试重新启动并恢复Intent
    }

    /**
     * 服务绑定回调
     * 本服务不支持绑定，返回null
     */
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    /**
     * 服务创建回调
     * 注册EventBus事件监听
     */
    @Override
    public void onCreate() {
        super.onCreate();
        isServiceRunning = true;
        EventBus.getDefault().register(this);  // 注册事件总线监听器
        startBackgroundCheck(); // 启动后台检测
        startEnergyMonitor(); // 启动能耗采集
        // 注册息屏/亮屏广播
        registerScreenReceiver();
        Log.d(TAG, "onCreate");
    }

    /**
     * 服务销毁回调
     * 取消EventBus事件监听
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        isServiceRunning = false;
//        stopBackgroundCheck(); // 停止后台检测
//        stopEnergyMonitor(); // 停止能耗采集
//        EventBus.getDefault().unregister(this);  // 取消事件总线监听器
//        // 注销息屏/亮屏广播
//        unregisterScreenReceiver();
    }

    /**
     * 启动后台检测线程
     * 定期检查APP是否在后台运行
     */
    private void startBackgroundCheck() {
        backgroundCheckThread = new Thread(() -> {
            while (isBackgroundCheckRunning) {
                try {
                    // 检查APP是否在后台
                    boolean isBackground = !isAppInForeground();
                    
                    // 如果状态发生变化，发送事件
                    if (isBackground != isAppInBackground) {
                        isAppInBackground = isBackground;
                        EventBus.getDefault().post(new Events.AppBackgroundEvent(isBackground));
                        Log.d(TAG, "App background status changed: " + (isBackground ? "in background" : "in foreground"));
                    }
                    
                    // 每秒检查一次
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Background check thread interrupted: " + e.getMessage());
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Error in background check: " + e.getMessage());
                }
            }
        });
        backgroundCheckThread.setDaemon(true);
        backgroundCheckThread.start();
        Log.d(TAG, "Background check thread started");
    }

    /**
     * 停止后台检测线程
     */
    private void stopBackgroundCheck() {
        isBackgroundCheckRunning = false;
        if (backgroundCheckThread != null) {
            backgroundCheckThread.interrupt();
            backgroundCheckThread = null;
        }
        Log.d(TAG, "Background check thread stopped");
    }

    /**
     * 检查APP是否在前台运行
     * @return true if app is in foreground, false otherwise
     */
    private boolean isAppInForeground() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) return false;

        List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
        if (appProcesses == null) return false;

        String packageName = getPackageName();
        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                    && appProcess.processName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 监听APP后台状态变化事件
     */
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onAppBackgroundEvent(Events.AppBackgroundEvent event) {
        if (event.isInBackground()) {
            Log.d(TAG, "App entered background, taking appropriate actions");
            // 在这里添加APP进入后台时需要执行的操作
            // 例如：暂停某些操作、保存状态等
        } else {
            Log.d(TAG, "App entered foreground, resuming normal operations");
            // 在这里添加APP回到前台时需要执行的操作
            // 例如：恢复暂停的操作、更新UI等
        }
    }

    /**
     * 处理获取后台状态的事件
     */
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onGetBackgroundStatus(Events.GetBackgroundStatusEvent event) {
        event.setInBackground(isAppInBackground);
    }

    /**
     * 启动能耗采集线程
     */
    private void startEnergyMonitor() {
        energyMonitorThread = new Thread(() -> {
            while (isEnergyMonitorRunning) {
                try {
                    // 采集能耗数据
                    int battery = getBatteryLevel();
                    double cpuUsage = getCpuUsage();
                    double temperature = getDeviceTemperature();
                    long timestamp = System.currentTimeMillis();
                    String deviceId = android.os.Build.SERIAL;
                    String roleStr = role;
                    // 发送能耗事件
                    EventBus.getDefault().post(new com.example.distribute_ui.Events.EnergyEvent(deviceId, roleStr, timestamp, battery, cpuUsage, temperature));
                    Thread.sleep(10000); // 每10秒采集一次
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        energyMonitorThread.setDaemon(true);
        energyMonitorThread.start();
    }

    private void stopEnergyMonitor() {
        isEnergyMonitorRunning = false;
        if (energyMonitorThread != null) {
            energyMonitorThread.interrupt();
            energyMonitorThread = null;
        }
    }

    // 获取电量百分比
    private int getBatteryLevel() {
        android.os.BatteryManager bm = (android.os.BatteryManager) getSystemService(BATTERY_SERVICE);
        if (bm != null) {
            int level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
            return level; // 0-100
        }
        return -1; // 获取失败
    }
    // 获取CPU占用率（系统整体）
    private double getCpuUsage() {
        // Android 10+ 无法访问 /proc/stat，直接返回 0.0
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            Log.w(TAG, "getCpuUsage: 当前系统不支持采集CPU占用率，返回0.0");
            return 0.0;
        }
        try {
            java.io.RandomAccessFile reader = new java.io.RandomAccessFile("/proc/stat", "r");
            String load = reader.readLine();
            String[] toks = load.split(" +"); // 多个空格分割
            long idle1 = Long.parseLong(toks[4]);
            long cpu1 = 0;
            for (int i = 1; i < 8; i++) {
                cpu1 += Long.parseLong(toks[i]);
            }
            Thread.sleep(360);
            reader.seek(0);
            load = reader.readLine();
            reader.close();
            toks = load.split(" +");
            long idle2 = Long.parseLong(toks[4]);
            long cpu2 = 0;
            for (int i = 1; i < 8; i++) {
                cpu2 += Long.parseLong(toks[i]);
            }
            return (double) (cpu2 - cpu1 - (idle2 - idle1)) / (cpu2 - cpu1);
        } catch (Exception e) {
            Log.w(TAG, "getCpuUsage: 采集失败，返回0.0");
            return 0.0;
        }
    }
    // 获取设备温度（电池温度）
    private double getDeviceTemperature() {
        android.content.Intent intent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent != null) {
            int temp = intent.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1);
            if (temp != -1) {
                return temp / 10.0; // 单位是 0.1°C
            }
        }
        return -1;
    }

    // 监听SessionLogEvent
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onSessionLogEvent(com.example.distribute_ui.Events.SessionLogEvent event) {
        sendLogToServer(event);
    }

    // 日志发送到服务端（此处仅打印日志，后续可扩展为发送功能）
    private void sendLogToServer(Object logEvent) {
        if (logEvent instanceof com.example.distribute_ui.Events.SessionLogEvent) {
            com.example.distribute_ui.Events.SessionLogEvent sessionLog = (com.example.distribute_ui.Events.SessionLogEvent) logEvent;
            StringBuilder sb = new StringBuilder();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
            sb.append("\n==== SessionLogEvent ====");
            // 打印 QueryLogEvent
            com.example.distribute_ui.Events.QueryLogEvent q = sessionLog.queryLog;
            sb.append("\n[QueryLog] id:").append(q.queryId)
              .append(", userQuery:").append(q.userQuery)
              .append(", response:").append(q.response)
              .append("\n tokens:").append(q.tokens)
              .append(", throughput:").append(q.throughput);
            // 打印每个token的详细阶段时间戳
            int tokenCount = Math.max(
                Math.max(
                    q.clientReceiveTimes != null ? q.clientReceiveTimes.size() : 0,
                    q.inferenceTimes != null ? q.inferenceTimes.size() : 0
                ),
                Math.max(
                    q.serverSendTimes != null ? q.serverSendTimes.size() : 0,
                    q.tailerResultTimes != null ? q.tailerResultTimes.size() : 0
                )
            );
            sb.append("\n[TokenStageTimes] count:").append(tokenCount);
            for (int i = 0; i < tokenCount; i++) {
                StringBuilder sbToken = new StringBuilder();
                sbToken.append("token[").append(i).append("] ");
                // clientReceive
                if (q.clientReceiveTimes != null && i < q.clientReceiveTimes.size()) {
                    long[] t = q.clientReceiveTimes.get(i);
                    sbToken.append("clientReceive:")
                        .append(sdf.format(new Date(t[0]))).append("-")
                        .append(sdf.format(new Date(t[1]))).append(", ");
                }
                // inference
                if (q.inferenceTimes != null && i < q.inferenceTimes.size()) {
                    long[] t = q.inferenceTimes.get(i);
                    sbToken.append("inference:")
                        .append(sdf.format(new Date(t[0]))).append("-")
                        .append(sdf.format(new Date(t[1]))).append(", ");
                }
                // serverSend
                if (q.serverSendTimes != null && i < q.serverSendTimes.size()) {
                    long[] t = q.serverSendTimes.get(i);
                    sbToken.append("serverSend:")
                        .append(sdf.format(new Date(t[0]))).append("-")
                        .append(sdf.format(new Date(t[1]))).append(", ");
                }
                // tailerResult
                if (q.tailerResultTimes != null && i < q.tailerResultTimes.size()) {
                    long[] t = q.tailerResultTimes.get(i);
                    sbToken.append("tailerResult:")
                        .append(sdf.format(new Date(t[0]))).append("-")
                        .append(sdf.format(new Date(t[1])));
                }
                sb.append(sbToken.toString());
            }
            // 打印 FaultEvent
            sb.append("\n[FaultEvents] count:").append(sessionLog.faultEvents.size());
            for (com.example.distribute_ui.Events.FaultEvent f : sessionLog.faultEvents) {
                sb.append("\n  type:").append(f.faultType)
                  .append(", time:").append(f.faultTime > 0 ? sdf.format(new Date(f.faultTime)) : "-")
                  .append(", recovery:").append(f.recoveryTime > 0 ? sdf.format(new Date(f.recoveryTime)) : "-")
                  .append(", affectedQueryId:").append(f.affectedQueryId);
            }
            // 打印 EnergyEvent
            sb.append("\n[EnergyEvents] count:").append(sessionLog.energyEvents.size());
            for (com.example.distribute_ui.Events.EnergyEvent e : sessionLog.energyEvents) {
                sb.append("\n  time:").append(e.timestamp > 0 ? sdf.format(new Date(e.timestamp)) : "-")
                  .append(", battery:").append(e.battery)
                  .append(", cpu:").append(e.cpuUsage)
                  .append(", temp:").append(e.temperature);
            }
            sb.append("\n========================");
            Log.d(TAG, sb.toString());

            // 新增：将日志以JSON格式发送到Python服务端（ZeroMQ方式）
            try {
                JSONObject json = new JSONObject();
                // 设备IP
                json.put("deviceIP", deviceIP != null ? deviceIP :Config.local);

                // QueryLogEvent
                JSONObject queryLogJson = new JSONObject();
                queryLogJson.put("queryId", q.queryId);
                queryLogJson.put("userQuery", q.userQuery);
                queryLogJson.put("response", q.response);
                queryLogJson.put("tokens", q.tokens);
                queryLogJson.put("throughput", q.throughput);
                // token阶段时间戳（全部转为字符串并合并为一行）
                JSONArray tokenStageTimes = new JSONArray();
                tokenCount = Math.max(
                    Math.max(
                        q.clientReceiveTimes != null ? q.clientReceiveTimes.size() : 0,
                        q.inferenceTimes != null ? q.inferenceTimes.size() : 0
                    ),
                    Math.max(
                        q.serverSendTimes != null ? q.serverSendTimes.size() : 0,
                        q.tailerResultTimes != null ? q.tailerResultTimes.size() : 0
                    )
                );
                for (int i = 0; i < tokenCount; i++) {
                    StringBuilder sbToken = new StringBuilder();
                    sbToken.append("token[").append(i).append("] ");
                    // clientReceive
                    if (q.clientReceiveTimes != null && i < q.clientReceiveTimes.size()) {
                        long[] t = q.clientReceiveTimes.get(i);
                        sbToken.append("clientReceive:")
                            .append(sdf.format(new Date(t[0]))).append("-")
                            .append(sdf.format(new Date(t[1]))).append(", ");
                    }
                    // inference
                    if (q.inferenceTimes != null && i < q.inferenceTimes.size()) {
                        long[] t = q.inferenceTimes.get(i);
                        sbToken.append("inference:")
                            .append(sdf.format(new Date(t[0]))).append("-")
                            .append(sdf.format(new Date(t[1]))).append(", ");
                    }
                    // serverSend
                    if (q.serverSendTimes != null && i < q.serverSendTimes.size()) {
                        long[] t = q.serverSendTimes.get(i);
                        sbToken.append("serverSend:")
                            .append(sdf.format(new Date(t[0]))).append("-")
                            .append(sdf.format(new Date(t[1]))).append(", ");
                    }
                    // tailerResult
                    if (q.tailerResultTimes != null && i < q.tailerResultTimes.size()) {
                        long[] t = q.tailerResultTimes.get(i);
                        sbToken.append("tailerResult:")
                            .append(sdf.format(new Date(t[0]))).append("-")
                            .append(sdf.format(new Date(t[1])));
                    }
                    tokenStageTimes.put(sbToken.toString());
                }
                queryLogJson.put("tokenStageTimes", tokenStageTimes);
                json.put("queryLog", queryLogJson);
                // FaultEvents
                JSONArray faultEventsJson = new JSONArray();
                for (com.example.distribute_ui.Events.FaultEvent f : sessionLog.faultEvents) {
                    JSONObject fJson = new JSONObject();
                    fJson.put("faultType", f.faultType);
                    fJson.put("faultTime", f.faultTime);
                    fJson.put("recoveryTime", f.recoveryTime);
                    fJson.put("affectedQueryId", f.affectedQueryId);
                    faultEventsJson.put(fJson);
                }
                json.put("faultEvents", faultEventsJson);
                // EnergyEvents
                JSONArray energyEventsJson = new JSONArray();
                for (com.example.distribute_ui.Events.EnergyEvent e : sessionLog.energyEvents) {
                    JSONObject eJson = new JSONObject();
                    eJson.put("timestamp", e.timestamp);
                    eJson.put("battery", e.battery);
                    eJson.put("cpuUsage", e.cpuUsage);
                    eJson.put("temperature", e.temperature);
                    energyEventsJson.put(eJson);
                }
                json.put("energyEvents", energyEventsJson);

                // 新增：发送时间段（第一个token和最后一个token的clientReceive[0]时间）
                String timeSpan = "";
                if (q.clientReceiveTimes != null && q.clientReceiveTimes.size() > 0) {
                    long first = q.clientReceiveTimes.get(0)[0];
                    long last = q.clientReceiveTimes.get(q.clientReceiveTimes.size() - 1)[0];
                    timeSpan = sdf.format(new Date(first)) + "~" + sdf.format(new Date(last));
                }
                json.put("timeSpan", timeSpan);

                // 发送到Python服务端（ZeroMQ方式）
                if (logSocket == null) {
                    Log.e(TAG, "Log socket not initialized");
                    return;
                }
                String jsonStr = json.toString();
                logSocket.send(jsonStr);
                Log.d(TAG, "Log sent to server via ZeroMQ");
            } catch (Exception e) {
                Log.e(TAG, "Failed to send log to server: " + e.getMessage());
            }
        } else {
            Log.d(TAG, "Send log to server: " + logEvent.toString());
        }
    }



    // 3. 注册/注销广播方法
    private void registerScreenReceiver() {
        if (screenReceiver == null) {
            screenReceiver = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                        isScreenOff = true;
                        EventBus.getDefault().post(new ScreenOffEvent(true));
                        Log.d(TAG, "Screen turned off");
                    } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                        isScreenOff = false;
                        EventBus.getDefault().post(new ScreenOffEvent(false));
                        Log.d(TAG, "Screen turned on");
                    }
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            registerReceiver(screenReceiver, filter);
            Log.d(TAG, "Screen receiver registered");
        }
    }
    private void unregisterScreenReceiver() {
        if (screenReceiver != null) {
            unregisterReceiver(screenReceiver);
            screenReceiver = null;
            Log.d(TAG, "Screen receiver unregistered");
        }
    }

    // 4. 订阅息屏事件
    @org.greenrobot.eventbus.Subscribe(threadMode = org.greenrobot.eventbus.ThreadMode.BACKGROUND)
    public void onScreenOffEvent(ScreenOffEvent event) {
        if (event.isScreenOff()) {
            Log.d(TAG, "Screen is off, take appropriate actions");
            // 这里可以添加息屏时的业务逻辑
        } else {
            Log.d(TAG, "Screen is on, resume normal operations");
            // 这里可以添加亮屏时的业务逻辑
        }
    }
}
