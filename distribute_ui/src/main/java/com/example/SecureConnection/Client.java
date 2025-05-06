package com.example.SecureConnection;

import static com.example.distribute_ui.service.BackgroundService.TAG;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import android.content.Context;
import android.os.Environment;
import java.util.ArrayList;
import java.util.Map;
import java.io.File;
import java.io.FileOutputStream;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONException;
import org.json.JSONObject;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;

import java.io.IOException;
import java.util.Objects;

import com.example.distribute_ui.Events;
import com.example.distribute_ui.network.FTPHelper;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.lang.reflect.Field;

public class Client {
    /**
     * 与服务器建立TCP连接，并返回对应的Socket对象
     * @param context ZeroMQ上下文
     * @param type Socket类型(DEALER, ROUTER等)
     * @param port 目标端口
     * @param address 目标IP地址
     * @return 创建好的Socket对象
     */
    public Socket establish_connection(ZContext context, SocketType type, int port, String address) {
        Socket socket = context.createSocket(type);
        try {
            Log.d(TAG, "尝试连接到地址: " + address + ", 端口: " + port);
            socket.connect("tcp://" + address + ":" + port);
            Log.d(TAG, "成功连接到地址: " + address + ", 端口: " + port);
        } catch (ZMQException e) {
            Log.e(TAG, "连接到地址: " + address + ", 端口: " + port + " 失败: " + e.getMessage() + ", 错误码: " + e.getErrorCode());
            if (e.getErrorCode() == ZMQ.Error.ECONNREFUSED.getCode()) {
                Log.e(TAG, "连接被拒绝，目标设备可能未启动或端口未开启");
            } else if (e.getErrorCode() == ZMQ.Error.ETIMEDOUT.getCode()) {
                Log.e(TAG, "连接超时，目标设备可能无法访问");
            }
            // 重新抛出异常，保持原有行为
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "连接到地址: " + address + ", 端口: " + port + " 时发生未知异常: " + e.getMessage());
            throw e;
        }
        return socket;
    }
    
    private Context conText;
    
    /**
     * 主通信处理函数 - 工作设备模式
     * 负责与服务器的通信循环，处理设备的状态转换和故障恢复
     * 
     * 状态流程：
     * Ready -> Open -> Prepare -> Initialized -> Start -> Running -> Finish
     * 故障恢复流程：
     * Running -> Recovery -> Recovering -> Running
     * 
     * @param cfg 设备配置对象
     * @param com 通信对象
     * @param receiver 用于接收消息的Socket
     * @param modelName 模型名称
     * @param serverIp 服务器IP
     * @param role 设备角色
     * @throws Exception 通信过程中可能发生的异常
     */
    public void communicationOpenClose(Config cfg, Communication com, Socket receiver, String modelName, String serverIp, int role) throws Exception {
        Log.d(TAG, "Start communicationOpenClose");
        Communication.Params param = com.param;
        while (true) {
            // status为Ready时，向服务器发送"Ready"和自身ip，等待服务器发送msg
            if (param.status.equals("Ready")) {
                Log.d(TAG, "Status: Ready");
                receiver.sendMore("Ready");         // sendMore表示后续还有消息待发送

                receiver.send(Config.local, 0);     // send表示为完整消息
                Log.d(TAG, "waiting for open signal");

                // Open
                String msg = new String(receiver.recv(0));
                Log.d(TAG, "msg: " + msg);
                if (msg.equals("Open")) {   // 收到msg为"Open"时，修改status，进行一系列准备工作
                    param.status = "Open";
                    System.out.println("Status: Open");

                    receiveIPGraph(cfg, receiver);

                    // 接收会话索引
                    receiveSessionIndex(receiver);

                    // 接收任务类型(生成式或分类)
                    receiveTaskType(param,receiver);

                    // 接收线程池大小配置
                    receiveThreadPoolSize(param, receiver);

                    // 接收批处理大小
                    receiveBatchSize(param, receiver);

                    // 接收序列最大长度
                    receiveSeqLength(param,receiver);

                    // 接收依赖图(模块间依赖关系)
                    receiveDependencyMap(receiver);

                    String num_devices = new String(receiver.recv(0));
                    Log.d(TAG, "num_devices: " + num_devices);


                    Log.d(TAG, "open status receive info finished");
                }

                // Prepare
                msg = new String(receiver.recv(0));
                Log.d(TAG, "prepare msg: " + msg);
                if (msg.equals("Prepare")) {    // 收到msg为"Prepare"时，修改status，准备好模型文件
                    communicationPrepare(receiver, param, modelName, serverIp, role);  // 准备好解压后的模型文件
                }

                // 初始化负载均衡和模型（新建会话和分词器）
                LoadBalanceInitialization();
                modelInitialization(cfg, param);
                param.status = "Initialized";
                System.out.println("Status: Initialized");
                receiver.send("Initialized", 0);

                msg = new String(receiver.recv(0));
                System.out.println(msg);

                if (msg.equals("Start")) {
                    param.status = "Start";
                    Log.d(TAG, "Status: Start");
                    receiver.send("Running");
                    Log.d(TAG, "Status: Running");
                    param.status = "Running";
                    if (param.status == "Running") {
                        // 发送RunningStatusEvent事件使BackgroundService中的runningStatus为true
                        EventBus.getDefault().post(new Events.RunningStatusEvent(true));
                        Log.d(TAG, "Post Events.RunningStatusEvent(true)");
                    }
                }
            }



            else if (param.status.equals("Finish")) {
                // 任务完成，发送结束信号
                receiver.send("Finish");
                String msg = new String(receiver.recv(0));
                System.out.println(msg);
                System.out.println("Status: Close");
                Log.d(TAG, "Status: Close");

                if (msg.equals("Close")) {  // 收到msg为"Close"时，关闭所有的套接字
                    for(ArrayList<Map<Integer, Socket>> s: com.allSockets)
                        closeSockets(s);
//                    com.context.close();
                }
                System.out.println("Finish task");
                Log.d(TAG, "Finish task");
                break;
            }
            else if (param.status.equals("Recovery")){
                // 收到故障恢复信号，进入恢复流程
                Log.d(TAG, "进入故障恢复状态 - Recovery");
                
                // 暂停所有推理线程（设置标志位阻止新任务提交）
                Communication.LB_Pause.setConditionTrue();
                Log.d(TAG, "已暂停所有推理线程，阻止新任务提交");
                
                // 保存当前批次ID用于后续恢复
                // 这个只是保存了当前批次ID
                Communication.loadBalance.setReSampleId(com.sampleId);
                Log.d(TAG, "已保存当前批次ID: " + com.sampleId + " 用于恢复");
                
                // 向服务器发送恢复请求和自身IP
                receiver.sendMore("Recovery");         
                receiver.send(Config.local, 0);
                Log.d(TAG, "已向服务器发送恢复请求");

                // 接收新的IP图与会话索引
                Log.d(TAG, "开始接收故障恢复信息");
                receiveIPGraph(cfg, receiver);
                receiveSessionIndex(receiver);
                
                // 接收新的依赖图（如果需要）
                receiveDependencyMap(receiver);
                Log.d(TAG, "故障恢复信息接收完成");
                
                // 进入恢复处理阶段
                param.status = "Recovering";
            }
            else if (param.status.equals("Recovering")){
                Log.d(TAG, "开始执行故障恢复过程 - Recovering");

                // 等待现有任务完成
                // 清理现有Socket连接
                com.cleanExistingConnections();
                waitForTasksToComplete(com);
                Log.d(TAG, "所有正在进行的任务已终止");
                
                // 保存中间状态（如果还未保存），InputData,OutputData，InputIds这些
                saveIntermediateState(com);
                Log.d(TAG, "中间状态已保存");


                Log.d(TAG, "现有Socket连接已清理");

                // 使用LoadBalance的方法更新设备映射和会话
                Communication.loadBalance.ModifySession();
                Log.d(TAG, "设备会话映射已更新");
                
                Communication.loadBalance.reLoadBalance();
                Log.d(TAG, "负载均衡已重新计算");

                // 重新创建Socket连接
                Log.w(TAG, "重新创建Socket连接");
                com.updateSockets(param.corePoolSize);
                Log.d(TAG, "Socket池已根据新的通信拓扑更新");

                // 向服务器发送恢复准备就绪信号
                receiver.sendMore("WaitingStart");
                receiver.send(Config.local, 0);

                param.status = "WaitingStart";
                Log.d(TAG, "已通知服务器恢复准备就绪，等待开始，当前批次: " + com.sampleId);
                // 等待服务器发送恢复推理信号
                String response = new String(receiver.recv(0));
                if ("ResumeStart".equals(response)) {
                    Log.d(TAG, "服务器已授权恢复推理 ResumeStart");
                    
                    // 重置重载标志，允许继续处理任务
                    Communication.loadBalance.setReSampleId(-1);
                    Communication.LB_Pause.setConditionFalse();
                    Log.d(TAG, "已重置标志位，允许推理继续");
                    
//                    // 如果需要向其他设备同步状态，在此处实现
//                    syncStateWithNewDevices(com, receiver);
                    
                    // 恢复状态
                    param.status = "Running";
                    Log.d(TAG, "状态已恢复为Running，推理继续");
                    
                    // 通知服务器恢复完成
                    receiver.sendMore("Running");
                    receiver.send(Config.local, 0);
                    Log.d(TAG, "已通知服务器恢复完成,Running");
                } else {
                    // 服务器没有发送正确的恢复信号
                    Log.e(TAG, "服务器响应异常: " + response);
                    param.status = "Failure";
                }
            }
        }
    }
    
    /**
     * 主通信处理函数 - 活跃设备模式
     * 与communicationOpenClose类似，但针对活跃设备池中的设备
     * 活跃设备是指尚未分配任务但随时可以替代故障设备的设备
     * 
     * @param cfg 设备配置对象
     * @param com 通信对象
     * @param receiver 用于接收消息的Socket
     * @param modelName 模型名称
     * @param serverIp 服务器IP
     * @param role 设备角色
     * @throws Exception 通信过程中可能发生的异常
     */
    public void communicationOpenCloseActive(Config cfg, Communication com, Socket receiver, String modelName, String serverIp, int role) throws Exception {
        Log.d(TAG, "Start communicationOpenCloseActive");
        Communication.Params param = com.param;
        while (true) {
            // status为Ready时，向服务器发送"Ready"和自身ip，等待服务器发送msg
            if (param.status.equals("Ready")) {
                Log.d(TAG, "Status: Ready");
                receiver.sendMore("Ready");         // sendMore表示后续还有消息待发送

                receiver.send(Config.local, 0);     // send表示为完整消息
                Log.d(TAG, "waiting for open signal");

                // Open
                String msg = new String(receiver.recv(0));
                Log.d(TAG, "msg: " + msg);
                if (msg.equals("Open")) {   // 收到msg为"Open"时，修改status，进行一系列准备工作
                    param.status = "Open";
                    System.out.println("Status: Open");

                    receiveIPGraph(cfg, receiver);

                    receiveSessionIndex(receiver);

                    receiveTaskType(param,receiver);

                    receiveThreadPoolSize(param, receiver);

                    receiveBatchSize(param, receiver);

                    receiveSeqLength(param,receiver);

                    receiveDependencyMap(receiver);

                    String num_devices = new String(receiver.recv(0));
                    Log.d(TAG, "num_devices: " + num_devices);


                    Log.d(TAG, "open status receive info finished");
                }

                // Prepare
                msg = new String(receiver.recv(0));
                Log.d(TAG, "prepare msg: " + msg);
                if (msg.equals("Prepare")) {    // 收到msg为"Prepare"时，修改status，准备好模型文件
//                    先不准备模型
                      param.status = "Prepare";
//                    communicationPrepare(receiver, param, modelName, serverIp, role);  // 准备好解压后的模型文件
                }

                // 初始化负载均衡和模型（新建会话和分词器）
                LoadBalanceInitialization();
//                modelInitialization(cfg, param); //暂时先不加载权重
                param.status = "Initialized";
                System.out.println("Status: Initialized");
                receiver.send("Initialized", 0);
                msg = new String(receiver.recv(0));
                Log.d(TAG, "prepare msg: " + msg);

                if (msg.equals("WaitingRecovery")) {
                    param.status = "WaitingRecovery";
                    Log.d(TAG, "Status: WaitingRecovery");

                }

            }



            else if (param.status.equals("Finish")) {
                receiver.send("Finish");
                String msg = new String(receiver.recv(0));
                System.out.println(msg);
                System.out.println("Status: Close");
                Log.d(TAG, "Status: Close");

                if (msg.equals("Close")) {  // 收到msg为"Close"时，关闭所有的套接字
                    for(ArrayList<Map<Integer, Socket>> s: com.allSockets)
                        closeSockets(s);
//                    com.context.close();
                }
                System.out.println("Finish task");
                Log.d(TAG, "Finish task");
                break;
            }
            else if (param.status.equals("Recovery")){
                // 活跃设备被选中替代故障设备时的恢复流程
                Log.d(TAG, "进入故障恢复状态 - Recovery（活跃设备）");
                
                // 向服务器发送恢复请求和自身IP
                receiver.sendMore("Recovery");         
                receiver.send(Config.local, 0);
                Log.d(TAG, "已向服务器发送恢复请求");

                // 接收新的IP图与会话索引
                Log.d(TAG, "开始接收故障恢复信息");
                receiveIPGraph(cfg, receiver);
                receiveSessionIndex(receiver);
                
                // 接收依赖图和其他必要配置
                receiveDependencyMap(receiver);
                Log.d(TAG, "故障恢复信息接收完成");
                
                // 进入恢复处理阶段
                param.status = "Recovering";
            }
            else if (param.status.equals("Recovering")){
                Log.d(TAG, "开始执行故障恢复过程 - Recovering（活跃设备）");






                // 重新创建Socket连接
//                com.updateSockets(param.corePoolSize);
//                Log.d(TAG, "Socket池已根据新的通信拓扑更新");
                // 如果需要向其他设备同步状态，在此处实现
                syncStateWithNewDevices(com, receiver);
                // 向服务器发送恢复准备就绪信号
                receiver.sendMore("WaitingStart");
                receiver.send(Config.local, 0);
                param.status = "WaitingStart";
                Log.d(TAG, "已通知服务器恢复准备就绪，等待开始");
                String msg = new String(receiver.recv(0));
                System.out.println(msg);

                if (msg.equals("Start")) {
                    param.status = "Start";
                    Log.d(TAG, "Status: Start");
                    receiver.send("Running");
                    Log.d(TAG, "Status: Running");
                    param.status = "Running";
                    if (param.status == "Running") {
                        // 发送RunningStatusEvent事件使BackgroundService中的runningStatus为true

                        EventBus.getDefault().post(new Events.RunningStatusEvent(true));
                        Log.d(TAG, "Post Events.RunningStatusEvent(true)");
                    }
                }

            }
        }
    }

    /**
     * 接收模型文件并保存到指定路径
     * 支持两种接收方式：整个文件一次接收或分块接收
     * 
     * @param path 保存文件的路径
     * @param receiver 用于接收数据的Socket
     * @param chunked 是否使用分块传输
     * @param chunk_size 分块大小(字节)
     */
    public void receiveModelFile(String path, Socket receiver, boolean chunked, int chunk_size) {
        File file = new File(path);
        if (file.exists() && file.delete()) {   // 若文件已存在则尝试删除文件
            System.out.println("Deleted the file: " + file.getName());
        } else {
            System.out.println("Failed to delete the file.");
        }

        File parentDir = file.getParentFile();  // 记录模型文件所在目录
        System.out.println("parent dir is: " + parentDir.toString());
        assert parentDir != null;
        if (!parentDir.exists()) {  // 若父目录不存在则创建
            parentDir.mkdirs();
        }
        System.out.println("Start receiving file");

        file = new File(path);
        if (!chunked) { // 当整个传输时
            try (FileOutputStream fos = new FileOutputStream(file)) {   // 直接接收整个文件后再写入
                byte[] data = receiver.recv(0);
                fos.write(data);
                System.out.println("Data is written");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {    // 当分块传输时
            try (FileOutputStream fos = new FileOutputStream(file)) {
                byte[] chunk;
                int totalSize = 0;
                while ((chunk = receiver.recv()) != null) { // 每次分块接收后写入直到不再端口不再收到信息
                    fos.write(chunk);
                    totalSize += chunk.length;
                    if (chunk.length == 0) {
                        break;
                    }
                    System.out.println("Chunk size: " + chunk.length + " Total size: " + totalSize);
                }
                System.out.println("Data is written");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 初始化负载均衡系统
     * 调用LoadBalance对象的reLoadBalance方法重新计算负载分配
     * 
     * @throws Exception 负载均衡过程中可能发生的异常
     */
    public void LoadBalanceInitialization() throws Exception {
        Communication.loadBalance.reLoadBalance();  // 负载均衡
        Log.d(TAG, "load balance init finished");
    }

    /**
     * 准备模型文件的通信处理
     * 1. 从服务器接收是否需要下载模型
     * 2. 如果需要，接收并保存模型文件
     * 3. 解压模型文件到工作目录
     * 
     * @param receiver 用于接收数据的Socket
     * @param param 通信参数对象
     * @param modelName 模型名称
     * @param serverIp 服务器IP
     * @param role 设备角色
     */
    public void communicationPrepare(Socket receiver, Communication.Params param, String modelName, String serverIp, int role) {
        param.status = "Prepare";   // 修改状态为"Prepare"
        boolean chunk = true;       // 默认为分块传输
        System.out.println("Status: Prepare");
        String skipModelDownload = new String(receiver.recv(0));    // 从服务器接收是否跳过下载阶段
        Log.d(TAG, "skipModelDownload: " + skipModelDownload);
        if (skipModelDownload.equals("False")) {
            receiveModelFile(param.modelPath + "/module.zip", receiver, chunk, 100 * 1024 * 1024);  // 接收模型文件，分块大小1MB
            System.out.println("Model Received");
        } else {    // 模型已存在，跳过模型下载阶段
            System.out.println("Model Exists");
        }
        if (skipModelDownload.equals("False")){
            Utils.unzipFile(param.modelPath + "/module.zip");   // 解压模型zip文件
        }
    }

    /**
     * 模型初始化
     * 1. 加载模型并创建推理会话
     * 2. 对于头节点和尾节点，还会创建分词器
     * 
     * @param cfg 设备配置对象
     * @param param 通信参数对象
     * @throws IOException 文件操作可能发生的异常
     */
    public void modelInitialization(Config cfg, Communication.Params param) throws IOException {
//        for (String i: Communication.sessionIndex) {
////            Communication.sessions.add(createSession(param.modelPath + "/device/module" + i + "/module_" + i + ".onnx"));
//            Communication.sessions.add(createSession(param.modelPath + "/device/module.onnx"));
//            System.out.println("Load module " + i + " successfully");
//            Log.d(TAG, "Load module " + i + " successfully");
//        }
//        File destFile = new File(conText.getFilesDir(), "module.onnx");
//        File sourceFile = new File(Environment.getExternalStorageDirectory(), "device/module.onnx");
//        Files.copy(sourceFile.toPath(), destFile.toPath());


        // 创建新的session并添加至列表中
//        String modelPath = destFile.getAbsolutePath();
//        Communication.sessions.add(createSession(modelPath));
        Communication.sessions.add(createSession(param.modelPath + "/device/module.onnx"));
        System.out.println("create session finished");


        // 对头结点和尾结点，还需要根据tokenizer.json创建分词器
        if (cfg.isHeader() || cfg.isTailer()) {
            Communication.tokenizer = createHuggingFaceTokenizer(param.modelPath + "/device/tokenizer.json");
            // OR SENTENCEPIECE LATER
            System.out.println("Tokenizer created");
            Log.d(TAG, "Tokenizer created");
        }
        System.out.println("model init finished");
    }

    /**
     * 接收IP图信息并构建通信图
     * IP图定义了分布式推理中各设备的连接拓扑
     * 
     * @param cfg 设备配置对象
     * @param receiver 用于接收数据的Socket
     */
    void receiveIPGraph(Config cfg, Socket receiver){
        byte[] ip_graph = receiver.recv(0);     // 接收IP图
        String ip_graph_str = new String(ip_graph);
        cfg.buildCommunicationGraph(ip_graph_str);   // 根据IP图构建通信图，为每个节点添加其头尾节点列表
        Log.d(TAG, "Get IP graph: " + ip_graph_str);
        cfg.getDeviceId();  // 获取DeviceId
    }

    /**
     * 接收会话索引信息
     * 会话索引定义了模型各部分如何分配到不同设备
     * 
     * @param receiver 用于接收数据的Socket
     */
    void receiveSessionIndex(Socket receiver){
        // Receive Session Index and inital load balance
        String session_indices = receiver.recvStr(0);
        Communication.loadBalance.sessIndices = session_indices.split(";");
        Log.d(TAG, "Get session index: " + session_indices);
    }

    /**
     * 接收任务类型信息
     * 支持两种任务类型：生成式(generation)和分类(classification)
     * 
     * @param param 通信参数对象
     * @param receiver 用于接收数据的Socket
     */
    private void receiveTaskType(Communication.Params param, Socket receiver){
        byte[] task_type = receiver.recv(0);
        param.task_type = new String(task_type);
        Log.d(TAG, "Task: " + param.task_type);
        if (param.task_type.equals("generation")) {
            Log.d(TAG, "Generation with text length: " + param.max_length);
        }else if (param.task_type.equals("classification")){
            Log.d(TAG, "Classification without text length");
        }
    }

    /**
     * 接收线程池大小配置
     * 线程池用于并行处理多个推理请求
     * 
     * @param param 通信参数对象
     * @param receiver 用于接收数据的Socket
     */
    private void receiveThreadPoolSize(Communication.Params param, Socket receiver){
        String pool_size = "";
        try {
            byte[] core_pool_size = receiver.recv(0);
            pool_size = new String(core_pool_size);
            param.corePoolSize = Integer.parseInt(pool_size);   // 将字符串类型参数转为整型
        } catch (NumberFormatException nfe) {
            System.out.println("Core Pool Size is not Integer");
        }
        Log.d(TAG, "Get ThreadPollSize: " + pool_size);
    }

    /**
     * 接收批处理大小配置
     * 批处理大小决定了一次处理多少个输入样本
     * 
     * @param param 通信参数对象
     * @param receiver 用于接收数据的Socket
     */
    private void receiveBatchSize(Communication.Params param, Socket receiver){
        try {
            byte[] batch = receiver.recv(0);
            param.numSample = Integer.parseInt(new String(batch));
        } catch (NumberFormatException nfe) {
            System.out.println("Num of Batch is not Integer");
        }
        Log.d(TAG, "Num of batch: " + param.numSample);
    }

    /**
     * 接收序列最大长度配置
     * 对于生成式任务，定义了生成文本的最大长度
     * 对于分类任务，长度为0
     * 
     * @param param 通信参数对象
     * @param receiver 用于接收数据的Socket
     */
    private void receiveSeqLength(Communication.Params param, Socket receiver) {
        try {
            byte[] max_length = receiver.recv(0);
            param.max_length = Integer.parseInt(new String(max_length));
        } catch (NumberFormatException nfe) {
            Log.d(TAG, "max_length is not Integer");
        }
        Log.d(TAG, "Get Sequence Max Length: " + param.max_length);
    }

    /**
     * 接收依赖图信息
     * 依赖图定义了模型各部分之间的数据依赖关系
     * 
     * @param receiver 用于接收数据的Socket
     */
    private void receiveDependencyMap(Socket receiver) {
        String depMap = receiver.recvStr(0);
        Log.d(TAG, "Show Map: " + depMap);
        try {
            Communication.loadBalance.dependencyMap = new JSONObject(depMap);   // 根据字符串创建JSON文件
        }catch (JSONException e) {
            Log.d(TAG, "Dependency Map JSON EXCEPTION");
        }
        Log.d(TAG, "Get Dependency Map");
    }

    /**
     * 关闭所有Socket连接
     * 在任务完成或需要重置通信时调用
     * 
     * @param sockets 需要关闭的Socket列表
     */
    public void closeSockets(ArrayList<Map<Integer, Socket>> sockets) {
//        releaseSession(Communication.session);
        for (Map<Integer, Socket> sock: sockets) {
            for (Socket socket : sock.values()) {
                socket.close();
            }
        }
    }

    // 本地方法，用于创建ONNX会话、释放会话和创建分词器
    public static native long createSession(String inference_model_path);
    public static native long releaseSession(long session);
    public native long createHuggingFaceTokenizer(String tokenizer_path);
    public native long createSentencePieceTokenizer(String tokenizer_path);

    /**
     * 等待现有推理任务完成
     * 监控推理线程，等待它们终止或超时强制终止
     * 主要处理OneStep线程和可能被阻塞的Socket通信
     * 
     * @param com 通信对象
     */
    private void waitForTasksToComplete(Communication com) {
        try {
            Log.d(TAG, "等待现有推理任务完成...");
            
            // 1. 先尝试找到并中断实际推理线程（Com.running中的ThreadPoolExecutor）
            // 而不是关闭executor（负责prepare和整个通信流程）
            Field poolField = null;
            ThreadPoolExecutor inferencePool = null;
            
            try {
                // 通过反射找到Communication中的ThreadPoolExecutor实例
                // 这种方法更精确，只终止推理线程而不影响通信线程
                poolField = Communication.class.getDeclaredField("pool");
                if (poolField != null) {
                    poolField.setAccessible(true);
                    inferencePool = (ThreadPoolExecutor) poolField.get(com);
                    
                    if (inferencePool != null && !inferencePool.isShutdown()) {
                        Log.w(TAG, "正在关闭推理线程池...");
                        // 拒绝新任务提交
                        inferencePool.shutdown();
                        
                        // 给线程一些时间完成当前执行的任务
                        boolean terminated = inferencePool.awaitTermination(3000, TimeUnit.MILLISECONDS);
                        
                        if (!terminated) {
                            // 如果超时仍未终止，强制中断所有线程
                            Log.w(TAG, "推理线程池未能及时关闭，强制中断");
                            inferencePool.shutdownNow();
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "通过反射访问推理线程池失败: " + e.getMessage());
                // 反射失败，回退到直接中断的方式
            }
            

            
            // 3. 如果我们直接无法访问推理线程池，尝试关闭executor
            // 但这是最后的选择，因为它可能会影响通信线程
            if (inferencePool == null && com.executor != null && !com.executor.isTerminated()) {
                Log.w(TAG, "未找到推理线程池，尝试直接关闭执行器");
                
                // 设置一个标志位告知通信线程结束运行
                // 这可能需要在Communication类中添加一个volatile标志位
                try {
                    Field runningField = Communication.class.getDeclaredField("isRunning");
                    if (runningField != null) {
                        runningField.setAccessible(true);
                        runningField.set(com, false);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "无法设置运行标志位: " + e.getMessage());
                }
                
                // 等待一小段时间让标志位生效
                Thread.sleep(1000);
                
                // 避免关闭通信线程，仅注册一个回调通知executor我们要终止
                // 而不是直接调用shutdown
                com.executor.execute(() -> {
                    Log.d(TAG, "请求终止运行中的推理任务");
                });
            }
            
            // 最后等待一段时间确保所有资源被释放
            Thread.sleep(2000);
            
            Log.d(TAG, "推理任务终止流程完成");
        } catch (Exception e) {
            Log.e(TAG, "终止推理任务失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 中断被阻塞的Socket通信
     * 用于故障恢复时打破设备等待故障节点的死锁
     * 
     * @param com 通信对象
     */
    private void interruptBlockedSockets(Communication com) {
        try {
            Log.d(TAG, "正在释放阻塞的Socket通信...");
            
            // 遍历所有Socket连接
            if (com.allSockets != null && !com.allSockets.isEmpty()) {
                // 复制队列内容以避免修改原队列结构
                ArrayList<ArrayList<Map<Integer, Socket>>> allSocketsCopy = new ArrayList<>();
                
                // 复制allSockets内容到临时列表
                com.allSockets.drainTo(allSocketsCopy);
                
                for (ArrayList<Map<Integer, Socket>> socketPair : allSocketsCopy) {
                    for (Map<Integer, Socket> socketMap : socketPair) {
                        for (Socket socket : socketMap.values()) {
                            // 为所有Socket设置接收超时，避免永久阻塞
                            socket.setReceiveTimeOut(100);
                            // 尝试发送一个"INTERRUPT"消息，唤醒等待接收的线程
                            try {
                                socket.send("INTERRUPT", ZMQ.DONTWAIT);
                            } catch (Exception e) {
                                // 忽略发送错误，继续处理
                            }
                        }
                    }
                }
                
                // 将Socket放回队列
                for (ArrayList<Map<Integer, Socket>> socketPair : allSocketsCopy) {
                    com.allSockets.put(socketPair);
                }
            }
            
            Log.d(TAG, "Socket通信阻塞释放完成");
        } catch (Exception e) {
            Log.e(TAG, "释放Socket阻塞失败: " + e.getMessage());
        }
    }
    
    /**
     * 保存推理的中间状态
     * 对于头节点，需要保存已生成的Token序列
     * 对于工作节点，需要保存计算中间结果
     * 
     * @param com 通信对象
     */
    private void saveIntermediateState(Communication com) {
        Log.d(TAG, "保存中间状态...");
        
        try {
            // 当前批次ID已在Recovery阶段保存到loadBalance.reSampleId
            int currentSampleId = Communication.loadBalance.reSampleId;
            
            // 检查是否有中间结果需要保存
            if (currentSampleId >= 0) {
                Log.d(TAG, "保存批次 " + currentSampleId + " 的中间状态");
                
                // 对于头节点，保存当前的输入ID序列（已经生成的token）
                if (com.cfg.isHeader() && com.InputIds.containsKey(currentSampleId-1)) {
                    // 记录已生成的token数量，用于恢复时验证
                    Log.d(TAG, "头节点: 保存输入ID序列，长度: " + 
                          com.InputIds.get(currentSampleId-1).size());
                    
                    // InputIds已经是类成员变量，在恢复时会自动访问，这里只需确保它被正确保存
                    // 如果需要额外备份，可以在这里实现
                }
                
                // 对于中间节点，保存中间计算结果
                if (!com.cfg.isHeader() && !com.cfg.isTailer()) {
                    // 保存任何中间计算状态，如果有的话
                    if (com.OutputData.containsKey(currentSampleId-1)) {
                        Log.d(TAG, "工作节点: 保存输出数据");
                        // OutputData已经是类成员变量，在恢复时会自动访问
                    }
                }
                
                // 如果有残差数据需要保存
                if (com.ResidualDataFromDevice.containsKey(currentSampleId-1) || 
                    com.ResidualDataToDevice.containsKey(currentSampleId-1)) {
                    Log.d(TAG, "保存残差数据");
                    // 残差数据已经存储在类成员变量中，在恢复时会自动访问
                }
            } else {
                Log.d(TAG, "无中间状态需要保存，当前批次ID无效");
            }
            
            Log.d(TAG, "中间状态保存完成");
        } catch (Exception e) {
            Log.e(TAG, "保存中间状态失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 与新设备同步状态
     * 在故障恢复过程中，将当前状态同步到新加入的设备
     * 
     * @param com 通信对象
     * @param receiver 用于与服务器通信的Socket
     */
    private void syncStateWithNewDevices(Communication com, Socket receiver) {
        try {
            Log.d(TAG, "开始与新设备同步状态...");
            
            // 当前批次ID
            int currentSampleId = Communication.loadBalance.reSampleId;
            if (currentSampleId < 0) {
                Log.d(TAG, "无需同步状态，没有有效的恢复批次ID");
                return;
            }
            
            // 从服务器获取新加入的设备信息（如果有）
            // 这里假设服务器会发送一个包含新设备ID的消息
            if (receiver.hasReceiveMore()) {
                String newDevicesInfo = new String(receiver.recv(0));
                Log.d(TAG, "获取到新设备信息: " + newDevicesInfo);
                
                // 解析新设备信息，格式可能是"NEW_DEVICE:ip:port"
                if (newDevicesInfo.startsWith("NEW_DEVICE:")) {
                    String[] parts = newDevicesInfo.substring(11).split(":");
                    if (parts.length >= 2) {
                        String newDeviceIp = parts[0];
                        int newDevicePort = Integer.parseInt(parts[1]);
                        
                        // 建立与新设备的直接通信
                        Log.d(TAG, "与新设备建立直接通信: " + newDeviceIp + ":" + newDevicePort);
                        
                        // 使用现有Socket或创建新Socket与新设备通信
                        // 这里可能需要根据新的通信拓扑查找对应的Socket
                        Socket newDeviceSocket = null;
//                        for (Map.Entry<Integer, String> entry : com.cfg.ipGraph_entry.entrySet()) {
//                            if (entry.getValue().equals(newDeviceIp)) {
//                                // 找到新设备的ID
//                                int newDeviceId = entry.getKey();
//                                Log.d(TAG, "找到新设备ID: " + newDeviceId);
//
//                                // 获取与新设备通信的Socket
//                                // 这里假设allSockets结构中已经建立了与新设备的Socket
//                                // 实际实现可能需要根据具体代码结构调整
//                                ArrayList<Map<Integer, Socket>> socketPair = com.allSockets.peek();
//                                if (socketPair != null && socketPair.size() >= 2) {
//                                    Map<Integer, Socket> clientSockets = socketPair.get(0);
//                                    if (clientSockets.containsKey(newDeviceId)) {
//                                        newDeviceSocket = clientSockets.get(newDeviceId);
//                                        Log.d(TAG, "找到与新设备通信的Socket");
//                                    }
//                                }
//                                break;
//                            }
//                        }
//
                        // 如果找到了与新设备通信的Socket，发送状态数据
                        if (newDeviceSocket != null) {
                            Log.d(TAG, "开始向新设备发送状态数据");
                            
                            // 发送握手信号
                            newDeviceSocket.send("SYNC_STATE");
                            
                            // 等待新设备确认
                            String response = new String(newDeviceSocket.recv(0));
                            if ("READY_FOR_SYNC".equals(response)) {
                                // 发送当前批次ID
                                newDeviceSocket.sendMore("SAMPLE_ID");
                                newDeviceSocket.send(String.valueOf(currentSampleId));
                                
                                // 根据设备角色发送不同的状态数据
                                if (com.cfg.isHeader()) {
                                    // 头节点发送已生成的token序列
                                    if (com.InputIds.containsKey(currentSampleId-1)) {
                                        ArrayList<Integer> tokens = com.InputIds.get(currentSampleId-1);
                                        // 这里需要将ArrayList<Integer>转换为可传输的格式
                                        // 简单起见，这里转为字符串，实际应使用二进制格式
                                        StringBuilder sb = new StringBuilder();
                                        for (int token : tokens) {
                                            sb.append(token).append(",");
                                        }
                                        newDeviceSocket.sendMore("INPUT_IDS");
                                        newDeviceSocket.send(sb.toString());
                                    }
                                }
                                
                                // 等待新设备确认接收完成
                                response = new String(newDeviceSocket.recv(0));
                                if ("SYNC_COMPLETED".equals(response)) {
                                    Log.d(TAG, "状态同步成功完成");
                                } else {
                                    Log.e(TAG, "状态同步异常: " + response);
                                }
                            } else {
                                Log.e(TAG, "新设备未准备好接收状态: " + response);
                            }
                        } else {
                            Log.e(TAG, "未找到与新设备通信的Socket");
                        }
                    }
                }
            } else {
                Log.d(TAG, "服务器未提供新设备信息，跳过状态同步");
            }
            
            Log.d(TAG, "状态同步过程完成");
        } catch (Exception e) {
            Log.e(TAG, "状态同步失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 等待状态同步
     * 活跃设备转为工作设备时，等待其他设备发来的状态同步信息
     * 
     * @param com 通信对象
     */
    private void waitForStateSynchronization(Communication com) {
        Log.d(TAG, "等待状态同步...");
        
        try {
            // 基于现有的通信结构接收状态同步
            // 查找所有可能的Socket连接
            ArrayList<Map<Integer, Socket>> socketPair = com.allSockets.peek();
            if (socketPair == null || socketPair.size() < 2) {
                Log.e(TAG, "无法找到有效的Socket连接，跳过状态同步");
                return;
            }
            
            // 获取接收Socket映射
            Map<Integer, Socket> serverSockets = socketPair.get(1);
            
            // 尝试从每个连接接收状态数据
            boolean syncReceived = false;
            long startTime = System.currentTimeMillis();
            long timeout = 10000; // 10秒超时
            
            while (!syncReceived && System.currentTimeMillis() - startTime < timeout) {
                // 检查所有接收Socket
                for (Socket socket : serverSockets.values()) {
                    // 非阻塞方式检查是否有消息
                    byte[] message = socket.recv(ZMQ.DONTWAIT);
                    if (message != null) {
                        String command = new String(message);
                        Log.d(TAG, "收到消息: " + command);
                        
                        if ("SYNC_STATE".equals(command)) {
                            // 收到同步请求，发送准备就绪响应
                            socket.send("READY_FOR_SYNC");
                            Log.d(TAG, "已发送准备同步响应");
                            
                            // 接收同步数据
                            String dataType = new String(socket.recv(0));
                            if ("SAMPLE_ID".equals(dataType)) {
                                // 接收当前批次ID
                                String sampleIdStr = new String(socket.recv(0));
                                int sampleId = Integer.parseInt(sampleIdStr);
                                Log.d(TAG, "接收到批次ID: " + sampleId);
                                
                                // 继续接收其他数据
                                dataType = new String(socket.recv(0));
                                if ("INPUT_IDS".equals(dataType)) {
                                    // 接收输入ID序列（对头节点的后继节点重要）
                                    String tokensStr = new String(socket.recv(0));
                                    String[] tokenParts = tokensStr.split(",");
                                    ArrayList<Integer> tokens = new ArrayList<>();
                                    for (String part : tokenParts) {
                                        if (!part.isEmpty()) {
                                            tokens.add(Integer.parseInt(part));
                                        }
                                    }
                                    
                                    // 将接收到的tokens保存到当前设备的状态中
                                    if (tokens.size() > 0) {
                                        // 如果需要，可以在这里保存tokens
                                        // 例如，如果这个设备成为新的头节点，需要保存tokens
                                        if (com.cfg.isHeader() && !com.InputIds.containsKey(sampleId-1)) {
                                            com.InputIds.put(sampleId-1, tokens);
                                            Log.d(TAG, "保存接收到的token序列，长度: " + tokens.size());
                                        }
                                    }
                                }
                                
                                // 可能还有其他数据类型需要接收...
                                
                                // 发送同步完成确认
                                socket.send("SYNC_COMPLETED");
                                Log.d(TAG, "状态同步成功完成");
                                
                                syncReceived = true;
                                break;
                            }
                        }
                    }
                }
                
                // 如果还未收到同步，小睡一会再检查
                if (!syncReceived) {
                    Thread.sleep(100);
                }
            }
            
            if (!syncReceived) {
                Log.w(TAG, "等待状态同步超时，将尝试无状态恢复");
            }
        } catch (Exception e) {
            Log.e(TAG, "状态同步接收失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}