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

import java.io.IOException;
import java.util.Objects;

import com.example.distribute_ui.Events;
import com.example.distribute_ui.network.FTPHelper;

public class Client {
    // 与服务器建立tcp连接，返回其Socket对象
    public Socket establish_connection(ZContext context, SocketType type, int port, String address) {
        Socket socket = context.createSocket(type);
        socket.connect("tcp://" + address + ":" + port);
        return socket;
    }
    private Context conText;
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

                // 添加对运行状态的处理，实时监听是否有故障恢复消息


                receiver.sendMore("Recovery");         // sendMore表示后续还有消息待发送

                receiver.send(Config.local, 0);

//                  握手成功
                Log.d(TAG, "开始接收故障恢复信息");
                receiveIPGraph(cfg, receiver);

                receiveSessionIndex(receiver);
                        // 进行故障恢复处理
                param.status = "Recovering";


            }
            else if (param.status.equals("Recovering")){
                Log.d(TAG, "开始执行故障恢复过程");


////                 清理现有Socket连接
//                com.cleanExistingConnections();
//
//                // 使用LoadBalance的方法更新设备映射和会话
//                Communication.loadBalance.ModifySession();
//                Communication.loadBalance.reLoadBalance();
//
//                // 重新创建Socket连接
//                com.updateSockets(param.corePoolSize);
//
//                // 重置重载标志
//                Communication.loadBalance.setReSampleId(-1);
//                Communication.LB_Pause.setConditionFalse();
//
//                // 恢复推理过程
//                com.resumeInference();

//                 恢复状态
                param.status = "Running";
                receiver.sendMore("RecoveryInference");         // sendMore表示后续还有消息待发送

                receiver.send(Config.local, 0);



            }
        }
    }
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
//                    communicationPrepare(receiver, param, modelName, serverIp, role);  // 准备好解压后的模型文件
                }

                // 初始化负载均衡和模型（新建会话和分词器）
//                LoadBalanceInitialization();
//                modelInitialization(cfg, param); //暂时先不加载权重
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
                if (msg.equals("WaitingStart")) {
                    param.status = "WaitingStart";
                    Log.d(TAG, "Status: WaitingStart");


                    if (param.status == "WaitingStart") {

                    }
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

                // 添加对运行状态的处理，实时监听是否有故障恢复消息


                receiver.sendMore("Recovery");         // sendMore表示后续还有消息待发送

                receiver.send(Config.local, 0);

//                  握手成功
                Log.d(TAG, "开始接收故障恢复信息");
                receiveIPGraph(cfg, receiver);

                receiveSessionIndex(receiver);
                // 进行故障恢复处理
                param.status = "Recovering";


            }
            else if (param.status.equals("Recovering")){
                Log.d(TAG, "开始执行故障恢复过程");


////                 清理现有Socket连接
//                com.cleanExistingConnections();
//
//                // 使用LoadBalance的方法更新设备映射和会话
//                Communication.loadBalance.ModifySession();
//                Communication.loadBalance.reLoadBalance();
//
//                // 重新创建Socket连接
//                com.updateSockets(param.corePoolSize);
//
//                // 重置重载标志
//                Communication.loadBalance.setReSampleId(-1);
//                Communication.LB_Pause.setConditionFalse();
//
//                // 恢复推理过程
//                com.resumeInference();

//                 恢复状态
                param.status = "Running";
                receiver.sendMore("RecoveryInference");         // sendMore表示后续还有消息待发送

                receiver.send(Config.local, 0);



            }
        }
    }

    // 接受模型文件到指定路径，有两种方式：直接接收整个文件或分块接收
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

    // 初始化负载均衡
    public void LoadBalanceInitialization() throws Exception {
        Communication.loadBalance.reLoadBalance();  // 负载均衡
        Log.d(TAG, "load balance init finished");
    }

    // 准备好解压后的模型文件
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

    // 模型初始化
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

    // 构建通信图，获取设备Id
    void receiveIPGraph(Config cfg, Socket receiver){
        byte[] ip_graph = receiver.recv(0);     // 接收IP图
        String ip_graph_str = new String(ip_graph);
        cfg.buildCommunicationGraph(ip_graph_str);   // 根据IP图构建通信图，为每个节点添加其头尾节点列表
        Log.d(TAG, "Get IP graph: " + ip_graph_str);
        cfg.getDeviceId();  // 获取DeviceId
    }

    // 接收片索引
    void receiveSessionIndex(Socket receiver){
        // Receive Session Index and inital load balance
        String session_indices = receiver.recvStr(0);
        Communication.loadBalance.sessIndices = session_indices.split(";");
        Log.d(TAG, "Get session index: " + session_indices);
    }

    // 接收任务类型
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

    // 接收线程池大小
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

    // 接收BatchSize
    private void receiveBatchSize(Communication.Params param, Socket receiver){
        try {
            byte[] batch = receiver.recv(0);
            param.numSample = Integer.parseInt(new String(batch));
        } catch (NumberFormatException nfe) {
            System.out.println("Num of Batch is not Integer");
        }
        Log.d(TAG, "Num of batch: " + param.numSample);
    }

    // 接收最大序列长度。对分类任务长度为0
    private void receiveSeqLength(Communication.Params param, Socket receiver) {
        try {
            byte[] max_length = receiver.recv(0);
            param.max_length = Integer.parseInt(new String(max_length));
        } catch (NumberFormatException nfe) {
            Log.d(TAG, "max_length is not Integer");
        }
        Log.d(TAG, "Get Sequence Max Length: " + param.max_length);
    }

    // 接收依赖图
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

    // 关闭所有作为客户端的套接字
    public void closeSockets(ArrayList<Map<Integer, Socket>> sockets) {
//        releaseSession(Communication.session);
        for (Map<Integer, Socket> sock: sockets) {
            for (Socket socket : sock.values()) {
                socket.close();
            }
        }
    }

    public static native long createSession(String inference_model_path);
    public static native long releaseSession(long session);
    public native long createHuggingFaceTokenizer(String tokenizer_path);
    public native long createSentencePieceTokenizer(String tokenizer_path);
}