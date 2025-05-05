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

    private String messageContent = "";             // 存储用户输入的消息内容

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
        messageStatus = event.messageSent;
        messageContent = event.messageContent;
        System.out.println("messageSent Status is: " + messageStatus);
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
     * 从配置文件中获取服务器IP地址
     * 
     * @return 服务器IP地址字符串
     */
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

        // 获取模型名称
        String modelName = "";
        if (intent != null && intent.hasExtra("model")) {   // 提取Intent中附加的额外信息"model"的值
            modelName = intent.getStringExtra("model");     // 获取模型名称
            System.out.println("model name is: "+ modelName);
        }

        // 获取服务器IP
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
                serverStatus = com.sendIPToServer(role, finalModelName); // 头节点需要提供模型名称
            } else {
                serverStatus = com.sendIPToServer(role, ""); // 工作节点不需要提供模型名称
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
                while (!messageStatus) {
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
                        while (j < com.param.numSample) {           // 共执行numSample(BatchSize)次
                            if (messageContent.equals(userinput)){  // 检查是否有新的用户输入
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                            } else {
                                // 收到新消息，处理并添加到输入列表
                                System.out.println("current numSample:" + j + ", New prompt:" + messageContent);
//                                messageContent = String.format("User: %s. Response:", messageContent); // 格式化prompt，用指定内容替换占位符
                                userinput = messageContent;
                                test_input.add(userinput);      // 将prompt加入列表中
                                j++;                            // 将当前批次的计数+1
                            }
                        }
                    }).start();
                }
                
                // 设置线程池参数并启动推理
                int corePoolSize = 2;      // 核心线程数
                int maximumPoolSize = 2;   // 最大线程数
                int keepAliveTime = 500;   // 线程空闲超时
                try {
                    Log.d(TAG, "communication starts to running");
                    // 启动实际推理任务，传入线程池参数和输入数据，这个应该是核心的推理线程是不是？
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
                    Log.d(TAG, "communication starts to running");
                    // 启动推理任务
                    com.running(corePoolSize, maximumPoolSize, keepAliveTime, test_input);
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
                results = com.timeUsage;
                return null;
            }
            return null;
        });

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
    }

    /**
     * 服务销毁回调
     * 取消EventBus事件监听
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        isServiceRunning = false;
        EventBus.getDefault().unregister(this);  // 取消事件总线监听器
    }
}
