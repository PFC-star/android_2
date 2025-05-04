package com.example.distribute_ui.service;
import android.app.Service;
import android.content.Intent;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Properties;

public class BackgroundService extends Service {    // 继承自Service，表明为一个服务
    public static double[] results;                 // 存储推理结果
    public static final String TAG = "StarDust_backend";
    private String role = "worker";                 // 设备的角色，默认为"worker"
    private  String serverStatus = "active";           // 是否需要monitor服务
    private final boolean running_classification = false;   // 是否为分类任务
    private boolean shouldStartInference = false;   // 是否开始推理
    private boolean runningStatus = false;          // 是否为运行状态
    private boolean messageStatus = false;          // 是否收到消息
    public static boolean isServiceRunning = false; // 服务是否正在运行

    private String messageContent = "";

    // 订阅事件RunningStatusEvent，收到事件后启动后台线程处理
    // 获取是否为运行状态
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onRunningStatus(Events.RunningStatusEvent event){
        runningStatus = event.isRunning;
        System.out.println("Running Status is: " + runningStatus);
    }

    // 订阅事件messageSentEvent，收到事件后启动后台线程处理
    // 获取消息是否有消息与消息的内容
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onMessageSentEvent(Events.messageSentEvent event) {
        messageStatus = event.messageSent;
        messageContent = event.messageContent;
        System.out.println("messageSent Status is: " + messageStatus);
        System.out.println("message Content is: " + messageContent);
    }

    // 订阅事件enterChatEvent，收到事件后启动后台线程处理
    // 获取是否应开始推理  推理开始标志
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onEnterChatEvent(Events.enterChatEvent event) {
        shouldStartInference = event.enterChat;
        System.out.println("ShouldStartInference is: " + shouldStartInference);
    }

    // 从config.properties中获取服务器IP地址
    private String getServerIPAddress() {
        String serverIP = "";
        Properties properties = new Properties();
        try {
            InputStream inputStream = getAssets().open("config.properties");
            properties.load(inputStream);
            serverIP = properties.getProperty("server_ip");
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
            // Handle the exception
        }
        return serverIP;
    }

    // 检查模型目录是否为空
    private boolean isModelDirectoryEmpty(String modelPath) {
        File modelDir = new File(modelPath + "/device");
        if (modelDir.isDirectory()) {
            String[] files = modelDir.list();
            return files == null || files.length == 0;
        }
        // Return true if it's not a directory, indicating "empty" in this context.
        return true;
    }

    // 传递参数更新DataRepository中isDirEmptyLiveData的值
    private void updateIsDirEmpty(boolean isDirEmpty) {
        // Update the repository with the new value
        DataRepository.INSTANCE.setIsDirEmpty(isDirEmpty);
    }

    // 当服务启动（通过startService(Intent)或bind）时调用的方法
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {  // flags和startId（用于标识服务，在终止服务时需要）由系统自动传递
        Log.d(TAG, "background service started");
        int id; // 用于存储intent中的role: Int
        if (intent != null && intent.hasExtra("role")) {    // 提取Intent中附加的额外信息"role"的值
            id = intent.getIntExtra("role", 0);
        } else {
            id = 0;
        }
        if (id == 1) {  // 若id为1，将角色改为头结点
            role = "header";
        }
        Log.d(TAG, "role is " + role);

        String modelName = "";
        if (intent != null && intent.hasExtra("model")) {   // 提取Intent中附加的额外信息"model"的值
            modelName = intent.getStringExtra("model");     // 获取模型名称
            System.out.println("model name is: "+ modelName);
        }

        String server_ip;
        if (intent != null && intent.hasExtra("ip")) {
            server_ip = intent.getStringExtra("ip");
            System.out.println("root ip: "+ server_ip);
        } else {
            server_ip = "";
        }

        // 创建一个单线程的线程池，池中的所有任务按顺序执行，每次最多只有一个正在执行的任务
        // 通过将任务提交给线程池执行，当前线程可以继续执行其他操作而不被阻塞，线程池会自动管理工作线程的生命周期
        ExecutorService executor = Executors.newSingleThreadExecutor();
        String finalModelName = modelName;  // 模型名称
        executor.submit(() -> {             // 提交一个任务到executor（lambda形式）

            // k为top-k采样的参数
            // initial_temp为temperature参数
            // 实例化一个配置类，服务器地址为server_ip:23456，top-k采样，此外还包括自身ip:端口号
            Config cfg = new Config(server_ip, 23456, 7, 0.7f);

            Communication com = new Communication(cfg, this, finalModelName, id); // 根据配置cfg实例化一个Communication
            Communication.loadBalance = new LoadBalance(com, cfg);  // 根据com和cfg实例化一个LoadBalance
            com.param.modelPath = getFilesDir() + "";   // 以字符串形式返回应用程序的私有文件存储目录
//            com.param.modelPath =  "/sdcard";
            Log.d(TAG, "Storage path is:" + com.param.modelPath);

            // 1. send IP to server to request model
            // 与服务器建立连接，发送自身ip（对头结点还需加上模型名称），根据从服务器接受信息决定need_monitor为true/false
            if (role.equals("header")) {
                serverStatus = com.sendIPToServer(role, finalModelName);
            } else {
                serverStatus = com.sendIPToServer(role, "");
            }
            Log.d(TAG, "serverStatus = " + serverStatus);

            // 2. Initiate device monitor for server-side optimization
            // 若need_monitor为true，则发送action为"START_MONITOR"的广播，
            // MainActivity中的receiver在接收到该广播后将启动MonitorService并附加role信息
//            if (need_monitor) {
//                Intent broadcastIntent = new Intent();
//                broadcastIntent.setAction("START_MONITOR"); // 设置广播的"action“
//                LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
//                sendBroadcast(broadcastIntent);
//                Log.d(TAG, "broadcast sent by backgroundService");
//            }
            if (serverStatus.equals("working")){
                Log.d(TAG, "serverStatus :working ");
//              初始化阶段
//                1. 传输控制信号 34567
//                    1.1 Ready->Open->Prepare->Initialized->Start->Running
//
                // 3.1 start downloading required model and tokenizer files from server
                // 执行Client.communicationOpenClose中param.status.equals("Ready")对应代码，包括准备模型文件和分词器等从初始化工作
                com.runPrepareThread(serverStatus);

            }

            if (serverStatus.equals("active")){
                Log.d(TAG, "serverStatus :active ");
                // 3.1 start downloading required model and tokenizer files from server
                // 执行Client.communicationOpenClose中param.status.equals("Ready")对应代码，包括准备模型文件和分词器等从初始化工作
                com.runPrepareThread(serverStatus);
//              运行阶段
//                1. 传输控制信号 34567
//                    1.1 Ready->Open->Prepare->Initialized 到这里但是不启动推理
//                    ->Start->Running
//                  什么时候启动推理呢？server检测到设备故障
//                    server 进入故障恢复函数 （把这个搞定吧）
//                    手机1 进入故障恢复函数
//                找到通信IP图（config["graph"],
//                            config["session_index"],）
//                            receiveIPGraph(cfg, receiver); -> Config.buildCommunicationGraph()
//                            receiveSessionIndex(receiver);
//                      以及注册IP图的地方，Communication.updateSockets
//
//                  重新通信IP图，启动手机3
//                  重新注册IP图，启动手机1
//                  恢复推理：手机1 根据IP图重新通信到手机3



            }





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

            // 4. Starting from here we need to based on the ACTION_ENTER_CHAT_SCREEN to start inference
            // 等待直到shouldStartInference为true
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
                // 4.1 分类任务的两个种类
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
                while (!messageStatus) {
                    try {
                        Thread.sleep(1000); // Sleep for a short duration to avoid busy waiting
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // Restore the interrupted status
                        break; // Exit the loop if the thread is interrupted
                    }
                }

                if (cfg.isHeader()) {
                    new Thread(() -> {
                        int j = 0;  // 记录当前批次序号
                        String userinput = "";
                        while (j < com.param.numSample) {           // 共执行numSample(BatchSize)次
                            if (messageContent.equals(userinput)){  // 检查是否有新的用户输入
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                            } else {
                                System.out.println("current numSample:" + j + ", New prompt:" + messageContent);
//                                messageContent = String.format("User: %s. Response:", messageContent); // 格式化prompt，用指定内容替换占位符
                                userinput = messageContent;
                                test_input.add(userinput);      // 将prompt加入列表中
                                j++;                            // 将当前批次的计数+1
                            }
                        }
                    }).start();
                }
                int corePoolSize = 2;
                int maximumPoolSize = 2;
                int keepAliveTime = 500;
                try {
                    Log.d(TAG, "communication starts to running");
                    com.running(corePoolSize, maximumPoolSize, keepAliveTime, test_input);
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
                double startTime = System.nanoTime();
                results = com.timeUsage;

                Log.d(TAG, "Results Computation Time: " + (System.nanoTime() - startTime) / 1000000000.0);
                return null;
            }

            // 对非头结点的推理过程
            else if (!shouldStartInference && !cfg.isHeader()){
                com.param.classes = new String[]{"Negative", "Positive"};
                Dataset dataset = null;
                while (com.param.numSample <= 0)
                    Thread.sleep(1000);
//                String[] test_input = new String[com.param.numSample];
                ArrayList<String> test_input = new ArrayList<>();
                int corePoolSize = 2;
                int maximumPoolSize = 2;
                int keepAliveTime = 500;

                try {
                    Log.d(TAG, "communication starts to running");
                    com.running(corePoolSize, maximumPoolSize, keepAliveTime, test_input);
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
                results = com.timeUsage;
                return null;
            }
            return null;
        });

        return START_STICKY; // 表示如果系统杀死了服务，系统会重新启动服务并尽可能地重新传递Intent
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    @Override
    public void onCreate() {
        super.onCreate();
        isServiceRunning = true;
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isServiceRunning = false;
        EventBus.getDefault().unregister(this);
    }
}
