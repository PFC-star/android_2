package com.example.SecureConnection;
import static com.example.distribute_ui.service.BackgroundService.TAG;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.*;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZContext;
import org.zeromq.SocketType;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.json.JSONObject;
import com.example.SecureConnection.Utils.LBPause;
import com.example.distribute_ui.DataRepository;


public class Communication {

    public class Params {
        public String modelPath;

        public String sourcePath;
        public int max_length = 256;
        public String task_type;
        public boolean skip_special_token;
        public int corePoolSize;

        public String[] classes;

        public volatile String status = "Ready";
        public int numSample;

    }
    public static volatile Long tokenizer;

    public static Long session;
    public static ArrayList<Long> sessions;
    public static String[] sessionIndex;
    public static LoadBalance loadBalance;
    public static LBPause LB_Pause;
    public ExecutorService executor;
    // 线程控制标志，用于故障恢复时中断推理
    public volatile boolean isRunning = true;
    // 保存推理任务的线程池引用，用于故障恢复时中断推理
    ThreadPoolExecutor pool;

    public Params param;
    public Config cfg;
    public String[] InputString;
    public volatile Map<Integer, ArrayList<Integer>> InputIds;
    public volatile Map<Integer, byte[]> InputData;
    public volatile Map<Integer, byte[]> OutputData;
    public volatile Map<Integer, Map<String, ArrayList<byte[]>>> ResidualDataFromDevice;
    public volatile Map<Integer, Map<String , ArrayList<byte[]>>> ResidualDataToDevice;
    public volatile Map<Integer, byte[]> logits;
    public int sampleId;    // 当前批次

    public boolean valid;

    public Map<String, Socket> sockets;

    public ZContext context;
    public Socket rootSocket;
    public Socket commuSocket;
    public Client beClient;
    public Server beServer;
    public LinkedBlockingQueue<ArrayList<Map<Integer, Socket>>> allSockets;
    //    public LinkedBlockingQueue<Map<Integer, Socket>> serverSockets;
//    public LinkedBlockingQueue<Map<Integer, Socket>> clientSockets;
    public double[] timeUsage;  // 准备时间
    public Set<Integer> receiveDeviceIndex;
    public Set<Integer> sendDeviceIndex;
    public TreeMap<String, ArrayList<JSONObject>> sendIndex;
    public TreeMap<String, ArrayList<JSONObject>> receiveIndex;
    public TreeMap<Integer, ArrayList<String>> sendD2D;
    public TreeMap<Integer, ArrayList<String>> receiveD2D;
    public TreeMap<String, Integer> module_on_devices;

    private Context conText;
    private String modelName;
    private int role;


    public Communication(Config cfg, Context conText, String modelName, int role) {
        Communication.sessions = new ArrayList<>();
        Communication.LB_Pause = new LBPause();
        this.conText = conText;
        this.cfg = cfg;
        this.modelName = modelName;
        this.role = role;

        param = new Params();
        param.skip_special_token = false;

        InputIds = new HashMap<>();
        InputData = new ConcurrentHashMap<>();
        OutputData = new ConcurrentHashMap<>();
        ResidualDataFromDevice = new ConcurrentHashMap<>();
        ResidualDataToDevice = new ConcurrentHashMap<>();
        logits = new ConcurrentHashMap<>();

        sampleId = 0;
        sockets = new HashMap<>();
        context = new ZContext();
        beClient = new Client();
        beServer = new Server();

        allSockets = new LinkedBlockingQueue<>();
//        serverSockets = new LinkedBlockingQueue<>();
//        clientSockets = new LinkedBlockingQueue<>();
        sendIndex = new TreeMap<>();
        receiveIndex = new TreeMap<>();

        sendDeviceIndex = new TreeSet<>();
        receiveDeviceIndex = new TreeSet<>();
        timeUsage = new double[2];

        module_on_devices = new TreeMap<>();
    }

    public String sendIPToServer(String role, String modelRequest) throws JSONException {
        rootSocket = beClient.establish_connection(context, SocketType.DEALER, cfg.rootPort, cfg.root); // 与服务器建立连接
        Log.d(TAG, "socket establish connection");
        // 将自身Ip与role写入Json文件，若为头结点，则附加上模型名称
        String currentIP = Config.local;
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("ip", currentIP);
        jsonObject.put("role", role);
        if ("header".equals(role)) {
            jsonObject.put("model", modelRequest);
        }
        // 向服务器发送自身Ip等信息
        rootSocket.sendMore("RegisterIP");
        rootSocket.send(jsonObject.toString());
        Log.d(TAG, "IP message sent");
        String msg_ = new String(rootSocket.recv(0));
        Log.d(TAG, "msg: " + msg_);

        // 启动心跳检测线程
        startHeartbeatDetection();

        return msg_;
    }

    private volatile boolean isHeartbeatRunning = true;
    private Thread heartbeatThread = null;
    
    /**
     * 启动心跳检测线程，定期向服务器发送心跳信息
     * 
     * 心跳检测是故障监控的关键机制，通过定期发送心跳包实现：
     * 1. 向服务器证明设备仍然在线
     * 2. 接收服务器发送的系统状态信息
     * 3. 检测并响应SYSTEM_FAILURE信号，触发故障恢复流程
     * 
     * 心跳频率：每10秒发送一次，低于服务器超时阈值
     */
    public void startHeartbeatDetection() {
        if (heartbeatThread != null && heartbeatThread.isAlive()) {
            return; // 避免重复启动
        }
        
        heartbeatThread = new Thread(() -> {
            try {
                // 使用相同的Socket连接，通过不同的Action进行区分
                // 注册时使用的是"RegisterIP"，心跳检测使用"HEARTBEAT"
                Log.d(TAG, "Heartbeat thread started, using existing socket connection");
                
                while (isHeartbeatRunning) {
                    try {
                        // 发送心跳消息
                        rootSocket.sendMore("HEARTBEAT");
                        rootSocket.send("");
//                        Log.d(TAG, "Heartbeat sent with action HEARTBEAT");
                        
                        // 接收心跳响应
                        String response = new String(rootSocket.recv(0));
                        
                        // 检查是否收到额外的系统状态信息
                        if (rootSocket.hasReceiveMore()) {
                            String systemStatus = new String(rootSocket.recv(0));
                            Log.d(TAG, "System status: " + systemStatus);
                            Log.d(TAG, "Current param status: " +  param.status);
                            
                            // 如果系统出现故障，触发故障恢复流程
                            if ("SYSTEM_FAILURE".equals(systemStatus) && 
                                !"Recovery".equals(param.status) && 
                                !"Recovering".equals(param.status)) {
                                
                                Log.w(TAG, "系统故障检测到! 准备进入故障恢复流程");
                                
                                // 设置状态为Recovery，Client.communicationOpenClose会检测到此状态变化并处理
                                synchronized (param) {
                                    param.status = "Recovery";
                                }
                                
                                Log.w(TAG, "状态已设置为 Recovery, 通信线程将处理恢复");
                            }
                        }
                        
                        // 心跳间隔，建议低于服务器超时阈值的一半
                        Thread.sleep(10000); // 10秒发送一次心跳
                    } catch (Exception e) {
                        Log.e(TAG, "心跳循环出错: " + e.getMessage());
                        // 心跳发送失败，短暂等待后重试
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ie) {
                            // 忽略中断异常
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "心跳线程致命错误: " + e.getMessage());
            }
        });
        
        heartbeatThread.setDaemon(true); // 设为守护线程，不阻止JVM退出
        heartbeatThread.start();
        Log.d(TAG, "心跳检测线程已启动");
    }
    
    /**
     * 停止心跳检测线程
     * 
     * 在设备关闭或任务完成时调用，确保心跳线程正确终止
     */
    public void stopHeartbeatDetection() {
        isHeartbeatRunning = false;
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
            heartbeatThread = null;
        }
        Log.d(TAG, "心跳检测已停止");
    }
    
    /**
     * 处理系统故障
     * 当检测到系统故障时，此方法将被调用进行恢复
     * 
     * 主要恢复步骤：
     * 1. 标记设备状态为"Recovering"
     * 2. 触发负载重平衡，更新任务分配
     * 3. 清理现有连接并重新建立通信
     * 4. 恢复中断的推理过程
     * 5. 将状态恢复为"Running"
     * 
     * 注意：此方法由Client.communicationOpenClose调用，不再直接处理通信
     */
    public void handleSystemFailure() {
        // 如果已经在故障处理或恢复中，避免重复处理
        Log.d(TAG, "Entering system failure handling procedure");
        if ("Failure".equals(param.status) || "Recovering".equals(param.status)) {
            Log.d(TAG, "System already in failure/recovery state, ignoring duplicate recovery trigger");
            return;
        }
        
        Log.w(TAG, "System failure handling initiated");
        param.status = "Recovering"; // 更改为恢复中状态，区别于完全故障
        Log.d(TAG, "param status: " +  param.status);
        
        try {
            // 注意：此方法不再直接处理通信逻辑，所有的通信现在应该在Client.communicationOpenClose中处理
            // 它只包含恢复逻辑，以便在接收到所需数据后执行
            
            // 设置LoadBalance的reSampleId触发重载平衡
            Communication.loadBalance.setReSampleId(sampleId);
            
            // 清理现有Socket连接
            cleanExistingConnections();
            
            // 使用LoadBalance的方法更新设备映射和会话
            loadBalance.ModifySession();
            loadBalance.reLoadBalance();
            
            // 重新创建Socket连接
            Log.w(TAG, "重新创建Socket连接");
            updateSockets(param.corePoolSize);
            Log.d(TAG, "Device connections re-established");
            
            // 重置重载标志
            Communication.loadBalance.setReSampleId(-1);
            Communication.LB_Pause.setConditionFalse();
            
            // 恢复状态
            param.status = "Running";
            Log.d(TAG, "System recovery completed, status restored to: " + param.status);
            
            // 重新启动推理过程
            resumeInference();
        } catch (Exception e) {
            Log.e(TAG, "Error during system failure recovery: " + e.getMessage());
            e.printStackTrace();
            // 恢复过程出错，设置为故障状态
            param.status = "Failure";
        }
    }
    
    /**
     * 清理现有的Socket连接
     * 
     * 在故障恢复过程中，需要关闭并重新创建所有通信套接字
     * 本方法安全地关闭所有现有连接，确保资源正确释放
     */
    void cleanExistingConnections() {
        try {
            // 清理现有连接
            while (!allSockets.isEmpty()) {
                ArrayList<Map<Integer, Socket>> socketPair = allSockets.take();
                for (Map<Integer, Socket> socketMap : socketPair) {
                    for (Socket socket : socketMap.values()) {
                        socket.setLinger(0);//确保快速释放端口
                        socket.close();
                    }

                }
            }

            Log.d(TAG, "Existing connections cleaned up");
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning existing connections: " + e.getMessage());
        }
    }

    /**
     * 恢复推理过程
     *
     * 故障恢复的最后阶段，重启被中断的推理过程
     * 主要步骤：
     * 1. 确认当前推理进度
     * 2. 恢复保存的输入/中间状态数据
     * 3. 对于头节点，恢复已经生成的部分结果
     * 4. 通知服务器已准备好继续推理
     */
    void resumeInference() {
        try {
            Log.d(TAG, "恢复推理过程");

            // 检查当前的状态和进度
            if (sampleId < param.numSample) {
                // 还有未完成的样本，重新开始从当前样本推理
                Log.d(TAG, "Resuming inference from sample " + sampleId);

                // 清理可能的中间状态，但保留输入数据
                Map<Integer, ArrayList<Integer>> savedInputIds = null;
                if (cfg.isHeader()) {
                    // 头节点需要保存输入数据以便恢复
                    savedInputIds = new HashMap<>(InputIds);
                }

                OutputData.clear();
                ResidualDataFromDevice.clear();
                ResidualDataToDevice.clear();

                if (cfg.isHeader()) {
                    // 恢复头节点的输入数据
                    if (savedInputIds != null) {
                        // 仅保留已完成处理的样本的数据
                        for (int i = 0; i < sampleId; i++) {
                            if (savedInputIds.containsKey(i)) {
                                InputIds.put(i, savedInputIds.get(i));
                                Log.d(TAG, "Restored input data for sample " + i);
                            }
                        }
                    }

                    // 如果当前正在处理的样本有部分输入数据，也需要恢复
                    if (savedInputIds != null && savedInputIds.containsKey(sampleId-1)) {
                        InputIds.put(sampleId-1, savedInputIds.get(sampleId-1));
                        Log.d(TAG, "Restored in-progress sample data for sample " + (sampleId-1));

                        // 记录恢复的数据大小，用于调试
                        Log.d(TAG, "Restored data size: " + InputIds.get(sampleId-1).size() + " tokens");

                        // 检查是否有足够的输入标记以继续处理
                        if (InputIds.get(sampleId-1).size() > 0) {
                            // 获取最后生成的token，可用于显示
                            int lastToken = InputIds.get(sampleId-1).get(InputIds.get(sampleId-1).size() - 1);
                            String lastTokenText = decodeID(new int[]{lastToken}, tokenizer);
                            Log.d(TAG, "Last generated token before failure: " + lastTokenText);

                            // 更新UI显示，通知用户恢复了之前的生成
                            ArrayList<Integer> decodeList = InputIds.get(sampleId-1);
                            String decodedString = decodeID(Utils.convertArrayListToIntArray(
                                    Objects.requireNonNull(decodeList)), tokenizer);
                            System.out.println("Recovered generated text: " + decodedString);
                            DataRepository.INSTANCE.updateDecodingString(decodedString);
                        }
                    }

                    Log.d(TAG, "Header node input data resynchronized");
                }

                // 通知服务器准备好继续推理
                rootSocket.sendMore("RESUME_INFERENCE");
                rootSocket.send(String.valueOf(sampleId));

                Log.d(TAG, "Inference process resumed");
            } else {
                Log.d(TAG, "No more samples to process, inference already completed");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error resuming inference: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 运行负责函数Prepare的线程
    public void runPrepareThread(String param){
        executor = Executors.newFixedThreadPool(2); // 创建一个最多2个线程同时执行的线程池
        executor.submit(()-> {  // 提交任务到线程池
            try {
                this.prepare(param); // 运行Communication类的prepare方法
            } catch (Exception e) { // 如果 prepare() 方法抛出异常，抛出一个运行时异常
                throw new RuntimeException(e);
            }
        });
    }

    public void runRunningThread(int corePoolSize, int maximumPoolSize, int keepAliveTime, ArrayList<String> input_data){
        executor.submit(()-> {
            try {
                Log.w(TAG, "runPrepareThread进去的 communication starts to running");
                this.running(corePoolSize, maximumPoolSize, keepAliveTime, input_data);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void shutDownPrepare(){
        executor.shutdown();
    }

    public void prepare(String param) throws Exception {
        long startTime = System.nanoTime(); // 记录开始时间
        // Communicate with Root Root Server
        Log.d(TAG, "root IP: " + cfg.root +  " ,root port: " + cfg.rootPort);

        if (param.equals("active")){
            commuSocket  = beClient.establish_connection(context, SocketType.DEALER, 23457,cfg.root); // 与服务器建立连接

            beClient.communicationOpenCloseActive(cfg, this, commuSocket, this.modelName, this.cfg.root, this.role);
        }
        else if (param.equals("working")){
            commuSocket  = beClient.establish_connection(context, SocketType.DEALER, 34567,cfg.root); // 与服务器建立连接
            beClient.communicationOpenClose(cfg, this, commuSocket, this.modelName, this.cfg.root, this.role);
        }
        // 执行Client类的communicationOpenClose方法

        long prepareTime = System.nanoTime();   // 记录完成时间
        System.out.println("Prepare Time in seconds: " + (prepareTime - startTime) / 1000000000.0);
        timeUsage[0] = (prepareTime - startTime) / 1000000000.0;    // 记录准备时间（单位/s）
    }

    public void cleanUpBuffer(int id) {
//        ResidualDataToDevice.remove(id);
//        ResidualDataFromDevice.remove(id);
        InputData.remove(id);
        OutputData.remove(id);
    }

    // 从sendIndex中获取与指定模块索引（module_idx）相关的残差索引数据，并将其返回。
    // 返回的数据是一个二维数组int[][]，每个子数组表示一个残差索引列表
    public int[][] getResIndices(int module_idx) throws JSONException {
        if (!sendIndex.containsKey(sessionIndex[module_idx]) || sendIndex.get(sessionIndex[module_idx]).size() <= 1 )
            return new int[0][];

        JSONObject resIndex = sendIndex.get(sessionIndex[module_idx]).get(1);
        resIndex.keys();
        int[][] ResIndex = new int[resIndex.length()][];
        Iterator<String> keys = resIndex.keys();
        // Need to sorted before computing
        List<String> tmp = new ArrayList<>();
        while (keys.hasNext())
            tmp.add(keys.next());
        Collections.sort(tmp);

        for (int i=0; i< tmp.size(); i++){
            ResIndex[i] = Utils.JsonArray2IntArray(resIndex.getJSONArray(tmp.get(i)));
        }
        return ResIndex;
    }


    public ArrayList<byte[]> mergeResFromAndToDevice(int id, String module_idx){
        ArrayList<byte[]> data1 = new ArrayList<>();
        ArrayList<byte[]> data2 = new ArrayList<>();
        if (ResidualDataFromDevice.containsKey(id) && ResidualDataFromDevice.get(id).containsKey(module_idx))
            data1 = ResidualDataFromDevice.get(id).get(module_idx); // Comes from previous module on the different device
        if (ResidualDataToDevice.containsKey(id) && ResidualDataToDevice.get(id).containsKey(module_idx))
            data2 = ResidualDataToDevice.get(id).get(module_idx);   // Comes from previous module on the same device
//        System.out.println("Merge ResidualDataToDevice Size: " +  data1.size());
        data1.addAll(data2);  // if sorted, then from previous device first, then from local device next.
//        System.out.println("Merge ResidualDataToDevice Size: " +  data2.size());
        System.out.println("Merge Receive Res Size: " +  data1.size());
        return data1;
    }

    public void convertOutput(int id, int module_idx, Object[] result){
        OutputData.put(id, (byte[]) result[0]); // 将模型某部分的推理结果存入输出映射表中

        JSONObject resIndex = null;
        if (sendIndex.get(sessionIndex[module_idx]).size() > 1) // 获取残差数据的索引
            resIndex = sendIndex.get(sessionIndex[module_idx]).get(1);

        // 检查是否有残差数据
        if (result.length > 1 && resIndex != null) {
            Iterator<String> keys = resIndex.keys();    // 获取所有key值
            int i = 0;
            while (keys.hasNext()) {    // 遍历每个key，将残差数据存储到ResidualDataToDevice
                String k = keys.next();
                if (!ResidualDataToDevice.get(id).containsKey(k))   // 若ResidualDataToDevice中不存在该key则创建
                    ResidualDataToDevice.get(id).put(k, new ArrayList<>());
                byte[][] val = (byte[][])result[1];
                ResidualDataToDevice.get(id).get(k).add(val[i]);    // Careful about the order added in
                i++;
            }
            if (ResidualDataToDevice.get(id).size() > 0)    // 当ResidualDataToDevice中有数据时打印信息
                for (Map.Entry<String, ArrayList<byte[]>> e: ResidualDataToDevice.get(id).entrySet())
                    if (e.getValue().size() > 1)
                        System.out.println("To Module "+ e.getKey() +" receive byte: "+  e.getValue().size());
        }
    }

    public void inferenceProcedure(int id) throws JSONException {
        // id即为SampleId。首先检查id对应输入数据是否存在
        if (((InputData.containsKey(id) && InputData.get(id) != null)) || (this.InputIds.get(id)) != null) {
            System.out.println("Start inference," + "session size: " + sessions.size());
            if (sessions.size() != 0) {     // 确保分块数 > 0
                byte[] res;
                Object[] result = null;
                // 为映射表添加一项(批次->映射表[字符串->比特数组指针列表])，用于存储设备间的数据
                ResidualDataToDevice.put(id, new TreeMap<>());
                if (cfg.isHeader()) {   // 对头结点
                    System.out.println("Inference on Master");
                    for (int i = 0;  i < sessions.size(); i++){  //
                        // 从 sendIndex 获取对应会话的发送索引，并将其转换为整数数组，准备发送给其他设备。
                        int[] to_send_seq_indices = Utils.JsonArray2IntArray(sendIndex.get(sessionIndex[i]).get(0).getJSONArray(String.valueOf(Integer.parseInt(sessionIndex[i]) + 1)));
                        System.out.println("to_send_seq_indices： " + Arrays.toString(to_send_seq_indices));
                        if (i == 0) {
//                            只使用最后一个 token kv-cache
                            ArrayList<Integer> inputIds = InputIds.get(id);
                            int[] currentToken = new int[]{inputIds.get(inputIds.size() - 1)};
                            result = ((Object[]) runInferenceMasterResidual(sessions.get(i), currentToken, to_send_seq_indices, getResIndices(i)));

                            System.out.println("current session: " + i + ", execute " + "runInferenceMasterResidual");
//                            result = ((Object[]) runInferenceMasterResidual(sessions.get(i), Utils.convertArrayListToIntArray(InputIds.get(id)), to_send_seq_indices, getResIndices(i)));
//                            RecordResult(result);
                        } else {
                            System.out.println("current session: " + i + ", execute " + "runInferenceWorkerResidual");
                            result = ((Object[]) runInferenceWorkerResidual(sessions.get(i), OutputData.get(id), mergeResFromAndToDevice(id, sessionIndex[i]), to_send_seq_indices, getResIndices(i)));
                        }
                        convertOutput(id, i, result);   // 将result存入OutputData和残差Data中
                    }
                }else if (cfg.isTailer()) { // 对尾结点
                    System.out.println("Inference on Tail");
                    for (int i = 0;  i< sessions.size(); i++){
                        if (i == sessions.size() - 1) { //
                            if (i == 0){ //
                                System.out.println("current session: " + i + ", execute " + "runInferenceWorkerResidualLastGeneration");
                                res = runInferenceWorkerResidualLastGeneration(sessions.get(i),
                                        InputData.get(id),
                                        mergeResFromAndToDevice(id, sessionIndex[i]),
                                        cfg.k,
                                        cfg.initial_temp);
                                }
                            else {
                                System.out.println("current session: " + i + ", execute " + "runInferenceWorkerResidualLastGeneration");
                                res = runInferenceWorkerResidualLastGeneration(sessions.get(i),
                                        OutputData.get(id),
                                        mergeResFromAndToDevice(id, sessionIndex[i]),
                                        cfg.k,
                                        cfg.initial_temp);
                            }
                            OutputData.put(id, res);
                            break;
                        }else if (i == 0) {
                            System.out.println("current session: " + i + ", execute " + "runInferenceWorkerResidual");
                            result = ((Object[]) runInferenceWorkerResidual(sessions.get(i),InputData.get(id), mergeResFromAndToDevice(id, sessionIndex[i]), Utils.JsonArray2IntArray(sendIndex.get(sessionIndex[i]).get(0).getJSONArray(sessionIndex[i + 1])), getResIndices(i)));
                        } else {
                            System.out.println("current session: " + i + ", execute " + "runInferenceWorkerResidual");
                            result = ((Object[]) runInferenceWorkerResidual(sessions.get(i), OutputData.get(id), mergeResFromAndToDevice(id, sessionIndex[i]), Utils.JsonArray2IntArray(sendIndex.get(sessionIndex[i]).get(0).getJSONArray(sessionIndex[i + 1])), getResIndices(i)));
                        }
                        convertOutput(id, i, result);
                    }
                }else {
                    System.out.println("Inference on Worker");
                    for (int i = 0;  i< sessions.size(); i++){
                        int[] to_send_seq_indices = Utils.JsonArray2IntArray(sendIndex.get(sessionIndex[i]).get(0).getJSONArray(String.valueOf(Integer.parseInt(sessionIndex[i]) + 1)));
                        System.out.println("to_send_seq_indices： " + Arrays.toString(to_send_seq_indices));
                        if (i == 0) {
                            System.out.println("current session: " + i + ", execute " + "runInferenceWorkerResidual");
                            result = ((Object[]) runInferenceWorkerResidual(sessions.get(i), InputData.get(id), mergeResFromAndToDevice(id, sessionIndex[i]), to_send_seq_indices, getResIndices(i)));
                        } else {
                            System.out.println("current session: " + i + ", execute " + "runInferenceWorkerResidual");
                            result = ((Object[]) runInferenceWorkerResidual(sessions.get(i), OutputData.get(id), mergeResFromAndToDevice(id, sessionIndex[i]), to_send_seq_indices, getResIndices(i)));
                        }
                        convertOutput(id, i, result);
                    }
                }
                System.out.println("Inference completed");
            }
        } else {    // 数据缺失
            System.out.println("Data missing");
        }
    }

    // corePoolSize=2, maximumPoolSize=2, keepAliveTime=500
    public void running(int corePoolSize, int maximumPoolSize, int keepAliveTime, ArrayList<String> input_data) throws Exception {
        // 重置运行标志
        isRunning = true;
//        启动running
        Log.w(TAG, "启动running");
        while(!param.status.equals("Running")) {
            Thread.sleep(1000);
        }

        if (!cfg.isHeader())    // 对非头结点，清空input_data
            input_data = null;

        if (param.corePoolSize > 0) {   // para.corePoolSize == 1
            corePoolSize = param.corePoolSize;
            maximumPoolSize = param.corePoolSize;
        }
        System.out.println("corePoolSize:" + corePoolSize + ", maximumPoolSize:" + maximumPoolSize);

        Semaphore latch = new Semaphore(param.corePoolSize);    // 设置值为corePoolSize的信号量

        Lock socketLock = new ReentrantLock();

        // 线程安全的队列，用于保存线程池中等待执行的任务
        LinkedBlockingQueue<Runnable> waitingQueue = new LinkedBlockingQueue<Runnable>();

        // 一个线程池，用于管理线程和任务的执行
        pool = new ThreadPoolExecutor(
                corePoolSize,           // 线程池保持的最小线程数
                maximumPoolSize,        // 线程池能够创建的最大线程数
                keepAliveTime,          // 最大空闲时间
                TimeUnit.MILLISECONDS,
                waitingQueue,           // 等待队列
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());

        // 为设备创建发送和接收的Socket连接，对头结点和尾结点有特殊处理
        Log.w(TAG, "running线程内创建Socket连接");
        updateSockets(corePoolSize);

        System.out.println("Load Balance On Running");

        long startTime = System.nanoTime();

        while (isRunning) {  // 检查isRunning标志位，支持故障恢复中断
            if (sampleId >= param.numSample) {  // 处理完所有批次后退出
                break;
            }else{
                if (cfg.isHeader) {     // 对头结点
                    while(sampleId >= input_data.size()) {  // 当前批次还未收到输入时则等待一段时间
                        // 检查是否收到了中断请求
                        if (!isRunning) {
                            Log.d(TAG, "等待输入时收到终止请求，中断推理");
                            break;
                        }
                        Thread.sleep(1000);
                    }
                    
                    // 再次检查终止标志
                    if (!isRunning) break;
                    
                    // 传递当前字符串和tokenizer指针，将字符串编码为整数数组
                    int[] data = encodeString(input_data.get(sampleId), tokenizer);
                    System.out.println("encode array: : " + Arrays.toString(data));
                    this.InputIds.put(sampleId, Utils.convertIntegerArrayToArrayList(data));    // 将编码结果记录到InputIds(映射表)中
                }
            }

            // 再次检查终止标志
            if (!isRunning) break;

            if (pool.getActiveCount() + waitingQueue.size() < corePoolSize) {   // 检查线程池中是否可以添加新任务
                if ((!LB_Pause.condition && loadBalance.reSampleId == -1 ) || sampleId < loadBalance.reSampleId) {  // 允许提交新任务的条件
                    latch.acquire();    // 获取等待信号量
                    System.out.println("提交新任务，实际执行的是multiSteps.run()");
                    pool.execute(new multiSteps(sampleId, latch));  // 提交新任务，实际执行的是multiSteps.run()
                    sampleId += 1;      // 批次计数+1
                }else if (LB_Pause.condition){
                    // 暂停提交新任务的逻辑
                    System.out.println("resampleId " + loadBalance.reSampleId);
                    System.out.println("wait the Process to Finish");
                    System.out.println("Active Thread Count: " + (pool.getActiveCount() + waitingQueue.size()));
                    if ((pool.getActiveCount() + waitingQueue.size()) == 0 && (loadBalance.reSampleId != -1 && sampleId >= loadBalance.reSampleId)) {
                        // Launch re-load when no active process in the pool
//                        一旦进入会暂停新任务提交，并且等待当前没有新任务了
                        System.out.println("===================== Load Balance =====================");
//                        loadBalance.ModifySession();
//                        loadBalance.reLoadBalance();
//                        Communication.loadBalance.setReSampleId(-1);
//                        LB_Pause.setConditionFalse();
                    }
                }
            }
            
            // 检查是否收到了中断请求
            if (!isRunning) {
                Log.d(TAG, "收到线程终止请求，中断推理循环");
                break;
            }
            
            // 避免CPU过度使用
            Thread.sleep(10);
        }

        // 如果是因为终止信号退出循环，需要清理资源
        if (!isRunning) {
            Log.d(TAG, "推理被中断，清理资源");
            pool.shutdownNow(); // 强制终止所有任务
        } else {
            // 正常完成
            Utils.await(latch, param.corePoolSize); // 在完成全部批次后，检查所有信号量是否都被释放
            pool.shutdown();
        }

        long runningTime = System.nanoTime();
        System.out.println("Running Time in seconds: " + (runningTime - startTime) / 1000000000.0);
        timeUsage[1] = (runningTime - startTime) / 1000000000.0;

        // Do after processing
        param.status = "Finish";

        shutDownPrepare();

        // Print out results
        System.out.println("Prepare time is: " + timeUsage[0] + "seconds");
        System.out.println("Running time is: " + timeUsage[1] + "seconds");

        if (cfg.isHeader()) {
            assert Objects.requireNonNull(input_data).size() >= logits.size();
            assert Objects.requireNonNull(input_data).size() >= param.numSample;
            for (int i = 0; i < param.numSample; i++) {
                if ((param.max_length == 0) && (param.task_type.equals("classification"))) {
                    System.out.println("The result of sample " + i + ":" + this.param.classes[binaryClassify(logits.get(i))]);
                    Log.d(TAG, "The result of sample " + i + ":" + this.param.classes[binaryClassify(logits.get(i))]);
                } else {
                    System.out.println(InputIds.get(i));
                    String decoding_String = decodeID(Utils.convertArrayListToIntArray(Objects.requireNonNull(InputIds.get(i))), tokenizer);
                    System.out.println("Generated sequence:" + decoding_String);
                    Log.d(TAG, "Generated sequence:" + decoding_String);
                }
            }
        }
    }

    // 实现了Runnable接口的类，必须重写run()方法，当提交该对象到线程池中会自动调用run()方法
    class multiSteps implements Runnable {
        private Map<Integer, Socket> serverSocket;
        private Map<Integer, Socket> clientSocket;
        private final int sample_id;
        private final Semaphore latch;

        // 构造函数，初始化样本 ID 和信号量，并从 allSockets 队列中获取服务器端和客户端的套接字映射
        public multiSteps(int sample_id, Semaphore latch) {
            this.sample_id = sample_id;
            ArrayList<Map<Integer, Socket>> sockets = null;
            try {
//                控制上下通信
                sockets = allSockets.take();    // 从队列中取出一个ArrayList<Map<Integer, Socket>>，包含客户端和服务器的socket配置
            } catch (InterruptedException e) {
                System.out.println("Waiting for an element from the sockets queue...");
                e.printStackTrace();
            }
            this.clientSocket = sockets.get(0); // 获取后继接收方的socket映射表
            this.serverSocket = sockets.get(1); // 获取前驱发送方的socket映射表

            this.latch = latch;
        }

        @Override
        public void run() {
            DataRepository.INSTANCE.updateSampleId(this.sample_id); // 更新数据仓库中的当前批次

            if (param.max_length < 0) {
                System.out.println("ERROR: Set up max_length");
            } else if (param.max_length == 0) { // 当max_length为0时代表分类任务
                System.out.println("SampleID: " + sample_id);
                int receivedId = 0;
                try {
                    receivedId = new OneStep(this.sample_id, serverSocket, clientSocket).run();
                } catch (InterruptedException | JSONException e) {
                    throw new RuntimeException(e);
                }
                cleanUpBuffer(receivedId);
            } else {    // 当max_length>0时代表生成任务
                int receivedId = sampleId-1;          // 获取当前批次
                int input_size = param.max_length;  // 当前处理的字符串长度
                System.out.println("Start inference current batch: " + this.sample_id + ", input_size is: " + input_size);

                Set<String> seenWindows = new HashSet<>();
                final int WINDOW_SIZE = 5;
                final int REPEAT_SIZE = 4;

                // 对每个token进行处理
                for (int m = 0; m < param.max_length; m++) {
                    long startTime = System.nanoTime();
                    System.out.println("current token:" + m);
                    try {   // 调用OneStep处理每个Token
                        int flag = 1;
//                        receivedId = new OneStep(this.sample_id, serverSocket, clientSocket).run();
                        // 顺序生成每一个token
                        flag = new OneStep(this.sample_id, serverSocket, clientSocket).run();

                        if (cfg.isHeader()) {
                            // 如果是头设备，进行解码并同步结果
                            input_size = Math.min(input_size, InputIds.get(receivedId).size());
                            // 截取字符串中的生成部分
                            ArrayList<Integer> decodeList = new ArrayList(InputIds.get(receivedId).subList(input_size-1, InputIds.get(receivedId).size()));
                            System.out.println("decode_ids:" + decodeList);
                            String decodedString = decodeID(Utils.convertArrayListToIntArray(
                                    Objects.requireNonNull(decodeList)), tokenizer);
                            System.out.println("decodedString:" + decodedString);
                            DataRepository.INSTANCE.updateDecodingString(decodedString);
                            System.out.println("token" + m + " Results Obtained");

                            List<Integer> currentTokens = new ArrayList<>(InputIds.get(receivedId).subList(input_size - 1, InputIds.get(receivedId).size()));
                            boolean stopGeneration = false;

                            // 检测 1：滑动窗口重复
                            if (currentTokens.size() >= WINDOW_SIZE) {
                                List<Integer> currentWindow = currentTokens.subList(currentTokens.size() - WINDOW_SIZE, currentTokens.size());
                                // 将窗口转换为唯一字符串（例如 "1,2,3"）
                                StringBuilder windowKeyBuilder = new StringBuilder();
                                for (int token : currentWindow) {
                                    windowKeyBuilder.append(token).append(",");
                                }
                                String windowKey = windowKeyBuilder.toString();
                                if (seenWindows.contains(windowKey)) {
                                    stopGeneration = true;
                                } else {
                                    seenWindows.add(windowKey);
                                }
                            }

                            // 检测 2：连续相同 token
                            if (currentTokens.size() >= REPEAT_SIZE) {
                                List<Integer> lastN = currentTokens.subList(currentTokens.size() - REPEAT_SIZE, currentTokens.size());
                                int lastToken = lastN.get(lastN.size() - 1);
                                boolean allSame = true;
                                for (int token : lastN) {
                                    if (token != lastToken) {
                                        allSame = false;
                                        break;
                                    }
                                }
                                if (allSame) {
                                    stopGeneration = true;
                                }
                            }

                            // 如果触发停止条件，终止生成
                            if (stopGeneration) {
                                System.out.println("重复生成，终止解码");
                                break;
                            }
                        }

                        if(flag == 0)
                            break;
                    } catch (InterruptedException | JSONException e) {
                        throw new RuntimeException(e);
                    }
                    System.out.println("Token " + m + " Process Time: " + (System.nanoTime() - startTime) / 1000000000.0);
                }
                cleanUpBuffer(this.sample_id);  // 清理缓冲区
            }

            try {   // 将客户端和服务器端的套接字重新放回 allSockets 队列中
                allSockets.put(new ArrayList<Map<Integer, Socket>>(){{
                    add(clientSocket);
                    add(serverSocket);
                }});
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            latch.release();    // 释放信号量
        }
    }

    /**
     * OneStep类用于管理分布式推理中的单步处理流程
     * 实现设备间的数据接收、计算处理和结果发送
     */
    public class OneStep {
        // 存储与其他设备通信的Socket映射
        private final Map<Integer, Socket> serverSocketMap;   // 服务端Socket映射，用于接收数据
        private final Map<Integer, Socket> clientSocketMap;   // 客户端Socket映射，用于发送数据
        private final Socket serverSocket;                    // 从前驱节点接收数据的Socket
        private final Socket clientSocket;                    // 发送数据给后继节点的Socket
        private final int sample_id;                          // 当前处理的样本/批次ID

        private int current_token_index;                      // 当前处理的token索引

        /**
         * 构造函数，初始化通信Socket和样本ID
         * 
         * @param sample_id 当前处理的样本ID
         * @param serverSide 服务端Socket映射，用于接收数据
         * @param clientSide 客户端Socket映射，用于发送数据
         */
        public OneStep(int sample_id, Map<Integer, Socket> serverSide, Map<Integer, Socket> clientSide) {
            this.sample_id = sample_id;
            this.serverSocketMap = serverSide;
            this.clientSocketMap = clientSide;
            this.serverSocket = serverSide.get(cfg.prevDeviceId()); // 从前驱节点接收的Socket
            this.clientSocket = clientSide.get(cfg.nextDeviceId()); // 发送给后继节点的Socket
        }

        /**
         * 作为客户端处理数据的方法
         * 负责向前驱节点请求数据，并接收前驱节点发送的数据
         * 
         * @param receivedId 接收的样本ID
         * @return 处理后的样本ID
         * @throws InterruptedException 线程中断时抛出
         */
        public int procssingAsClient(int receivedId) throws InterruptedException {
            if (!cfg.isHeader()) {  // 如果当前设备不是头节点
                System.out.println("Start to be a Client");

                // 检查系统状态，如果正在恢复则不等待结果
                if ("Recovery".equals(param.status) || "Recovering".equals(param.status)) {
                    Log.d(TAG, "系统正在故障恢复中，跳过客户端数据接收");
                    return receivedId;
                }
                
                // 检查是否有终止请求
                if (!isRunning) {
                    Log.d(TAG, "推理已被中断，跳过客户端数据接收");
                    return receivedId;
                }

                // 设置临时接收超时，防止故障时永久阻塞
                int originalTimeout = serverSocket.getReceiveTimeOut();
//                serverSocket.setReceiveTimeOut(5000); // 5秒超时
                
                try {
                    serverSocket.send("Request Data");  // 向前驱节点发送数据请求
                    
                    // 接收并解析样本ID
                    byte[] idData = serverSocket.recv(0);
                    if (idData == null) {
                        Log.w(TAG, "等待前驱节点响应超时，可能发生故障");
                        // 恢复原始超时设置
                        serverSocket.setReceiveTimeOut(originalTimeout);
                        return receivedId;
                    }
                    receivedId = Utils.convertByteArrayToInt(idData);

                    // 启动一个线程异步接收残差数据（用于模型优化和加速）
                    Thread workerThread = new Thread(new ReceiveResidualConnection(receivedId, serverSocketMap));
                    workerThread.start();

                    // 验证接收的样本ID是否匹配当前处理的样本ID，如不匹配则输出警告
                    if (receivedId != this.sample_id) {
                        System.out.println("Client: Data out of the order, sampleId: " + this.sample_id + ", receivedId: " + receivedId);
                    }

                    // 从前驱节点接收实际数据
                    byte[] msgFrom = serverSocket.recv(0);
                    if (msgFrom == null) {
                        Log.w(TAG, "等待前驱节点数据超时，可能发生故障");
                        // 恢复原始超时设置
                        serverSocket.setReceiveTimeOut(originalTimeout);
                        return receivedId;
                    }
                    
                    // 存储接收到的数据
                    InputData.put(receivedId, msgFrom);
                    System.out.println("Received Data");
                    
                    // 使用带超时的join等待残差数据接收线程完成
                    // 设置较短的超时时间以防止长时间阻塞
                    workerThread.join(3000);
                    
                    // 如果线程仍在运行，不强制中断它，但记录日志
                    if (workerThread.isAlive()) {
                        Log.w(TAG, "残差数据接收线程未能在规定时间内完成");
                    } else {
                        System.out.println("Received ResData");
                    }
                } finally {
                    // 确保在所有情况下恢复原始超时设置
                    serverSocket.setReceiveTimeOut(originalTimeout);
                }

            } else {
                // 作为头节点时，从本地加载数据而不是从其他节点接收
                if (logits.get(receivedId) == null) {
                    System.out.println("Load Data");
                }
            }
            return receivedId; // 返回处理的样本ID
        }

        /**
         * 作为服务器处理数据的方法
         * 负责向后继节点发送数据响应
         * 
         * @param receivedId 当前处理的样本ID
         * @throws InterruptedException 线程中断时抛出
         */
        public void processAsServer(int receivedId) throws InterruptedException {
            // 向头节点或后继节点返回数据
            if (clientSocket == null) {
                Log.e(TAG, "ProcessAsServer Error: clientSocket为空");
                return;  // 无法继续处理
            }

            // 检查系统状态，如果正在恢复则不等待请求
            if ("Recovery".equals(param.status) || "Recovering".equals(param.status)) {
                Log.d(TAG, "系统正在故障恢复中，跳过服务器数据发送");
                return;
            }
            
            // 检查是否有终止请求
            if (!isRunning) {
                Log.d(TAG, "推理已被中断，跳过服务器数据发送");
                return;
            }

            System.out.println("Start to be a Server");
            
            // 设置临时接收超时，防止故障时永久阻塞
            int originalTimeout = clientSocket.getReceiveTimeOut();
            clientSocket.setReceiveTimeOut(5000); // 5秒超时
            
            try {
                // 接收来自后继节点的请求ID和内容
                byte[] comefrom_id = clientSocket.recv(0);
                if (comefrom_id == null) {
                    Log.w(TAG, "等待后继节点请求ID超时，可能发生故障");
                    return;
                }
                
                byte[] msgTo = clientSocket.recv(0);
                if (msgTo == null) {
                    Log.w(TAG, "等待后继节点请求内容超时，可能发生故障");
                    return;
                }
                
                // 如果收到数据请求
                if (new String(msgTo).contains("Request Data")) {
                    // 检查是否有可发送的数据
                    if (OutputData.containsKey(receivedId)) {
                        byte[] id = "from".getBytes();
                        
                        // 启动线程发送残差数据
                        Thread workerThread = new Thread(new SendResidualConnection(receivedId, clientSocketMap));
                        workerThread.start();
                        
                        // 发送样本ID
                        id = Utils.convertIntToByteArray(receivedId);
                        boolean sendSuccess = true;
                        
                        try {
                            clientSocket.sendMore(comefrom_id);  // 首先发送请求方ID
                            clientSocket.sendMore(id);           // 然后发送样本ID
                            
                            // 发送输出数据，对于尾节点且为生成任务时，发送特定解码ID
                            if (cfg.isTailer() && (param.task_type.equals("generation"))) {
                                byte[] decode_id = OutputData.get(receivedId);
                                clientSocket.send(decode_id, 0);
                            } else {
                                clientSocket.send(OutputData.get(receivedId), 0);
                            }
                        } catch (org.zeromq.ZMQException e) {
                            Log.w(TAG, "发送数据时发生异常: " + e.getMessage());
                            sendSuccess = false;
                        }
                        
                        if (sendSuccess) {
                            System.out.println("Sent Data");
                        } else {
                            Log.w(TAG, "数据发送失败，可能是接收方已断开连接");
                        }
                        
                        // 使用带超时的join等待残差数据发送线程完成
                        workerThread.join(3000);
                        
                        // 如果线程仍在运行，不强制中断它，但记录日志
                        if (workerThread.isAlive()) {
                            Log.w(TAG, "残差数据发送线程未能在规定时间内完成");
                        }
                    } else {
                        // 数据不存在时输出警告
                        System.out.println(receivedId + " is not in the OutputData");
                    }
                }
            } finally {
                // 确保在所有情况下恢复原始超时设置
                clientSocket.setReceiveTimeOut(originalTimeout);
            }
        }

        /**
         * 从尾节点获取结果的方法
         * 仅头节点使用，用于获取整个分布式推理的最终结果
         * 
         * @param receivedId 当前处理的样本ID
         * @return 处理状态标志：0表示处理完成，1表示继续处理
         */
        public int obtainResultsFromTailer(int receivedId) {
            // 头节点特有的从尾节点获取结果的功能
            int flag = 1;  // 默认继续处理标志
            
            if (cfg.isHeader()) {
                try {
                    System.out.println("Start to obtain result from tailer");
                    
                    // 检查系统状态，如果正在恢复则不等待结果
                    if ("Recovery".equals(param.status) || "Recovering".equals(param.status)) {
                        Log.d(TAG, "系统正在故障恢复中，跳过等待尾节点结果");
                        return flag;
                    }
                    
                    // 检查是否有终止请求
                    if (!isRunning) {
                        Log.d(TAG, "推理已被中断，跳过等待尾节点结果");
                        return flag;
                    }
                    
                    // 向尾节点请求结果
                    serverSocket.send("Request Data");
                    
                    // 设置临时接收超时，防止故障时永久阻塞
                    int originalTimeout = serverSocket.getReceiveTimeOut();
//                    serverSocket.setReceiveTimeOut(5000); // 5秒超时
                    
                    // 接收样本ID
                    byte[] idData = serverSocket.recv(0);
                    Log.w(TAG, "收到idData");
                    if (idData == null) {
                        Log.w(TAG, "等待尾节点响应超时，可能发生故障");
                        return flag;
                    }
                    receivedId = Utils.convertByteArrayToInt(idData);

                    // 验证样本ID是否匹配
                    if (receivedId != this.sample_id) {
                        System.out.println("Server: Data out of the order, sampleId: " + this.sample_id + ", receivedId: " + receivedId);
                    }
                    
                    // 接收结果数据
                    byte[] res = serverSocket.recv(0);
                    if (res == null) {
                        Log.w(TAG, "等待尾节点数据超时，可能发生故障");
                        return flag;
                    }
                    
                    // 恢复原始超时设置
                    serverSocket.setReceiveTimeOut(originalTimeout);
                    
                    // 根据任务类型处理数据
                    if (param.task_type.equals("generation")) {
                        // 生成任务：解析解码ID并添加到输入序列
                        int decode_id = deserializeInt(res);
                        System.out.println("Obtain decode_id: " + decode_id);
                        
                        // 如果解码ID为2(通常是结束标记)，设置完成标志
                        if(decode_id == 2){
                            flag = 0;
                        }
                        
                        // 将解码ID添加到输入序列
                        InputIds.get(receivedId).add(decode_id);
                    } else {
                        // 非生成任务：直接存储logits数据
                        logits.put(receivedId, res);
                    }
                } catch (org.zeromq.ZMQException e) {
                    // 处理ZMQ异常，通常是由于故障恢复期间中断Socket操作导致
                    Log.w(TAG, "Socket操作被中断，可能是故障恢复过程引起: " + e.getMessage());
                    
                    // 检查是否系统正在恢复
                    if ("Recovery".equals(param.status) || "Recovering".equals(param.status)) {
                        Log.d(TAG, "系统正在执行故障恢复，Socket中断是预期行为");
                    } else {
                        Log.e(TAG, "Socket通信意外中断", e);
                    }
                } catch (Exception e) {
                    // 处理其他异常
                    Log.e(TAG, "获取尾节点结果时发生异常: " + e.getMessage(), e);
                }
            }
            return flag;  // 返回处理状态标志
        }

        /**
         * 执行完整的单步推理流程
         * 包括客户端处理、推理计算、服务器处理和结果获取
         * 
         * @return 处理状态标志：0表示处理完成，1表示继续处理
         * @throws RuntimeException 运行时异常
         * @throws InterruptedException 线程中断时抛出
         * @throws JSONException JSON处理异常
         */
        public int run() throws RuntimeException, InterruptedException, JSONException {
            int receivedId = this.sample_id;    // 获取当前批次ID
            int flag = 1;  // 默认继续处理标志
            
            try {
                // 检查系统状态和推理是否被中断
                if ("Recovery".equals(param.status) || "Recovering".equals(param.status)) {
                    Log.d(TAG, "系统正在故障恢复中，跳过推理步骤");
                    return flag;
                }
                
                if (!isRunning) {
                    Log.d(TAG, "推理已被中断，跳过推理步骤");
                    return flag;
                }
                
                // 第一步：作为客户端接收数据
                long startTime = System.nanoTime();  // 记录开始时间
                try {
                    receivedId = procssingAsClient(receivedId);
                    System.out.println("AsClient Process Time: " + (System.nanoTime() - startTime) / 1000000000.0);
                } catch (org.zeromq.ZMQException e) {
                    // 处理Socket通信异常
                    Log.w(TAG, "客户端接收数据时Socket操作被中断: " + e.getMessage());
                    checkSystemStatus();
                    return flag;
                }
                
                // 再次检查系统状态，防止在第一步完成后系统状态已变化
                if (checkSystemStatus()) {
                    return flag;
                }

                // 第二步：执行推理计算
                startTime = System.nanoTime();
                try {
                    inferenceProcedure(receivedId);  // 调用推理处理方法
                    System.out.println("Inference Process Time: " + (System.nanoTime() - startTime) / 1000000000.0);
                } catch (Exception e) {
                    // 处理推理计算异常
                    Log.e(TAG, "推理计算过程发生异常: " + e.getMessage(), e);
                    checkSystemStatus();
                    return flag;
                }
                
                // 再次检查系统状态
                if (checkSystemStatus()) {
                    return flag;
                }

                // 第三步：作为服务器发送数据
                startTime = System.nanoTime();
                try {
                    processAsServer(receivedId);
                    System.out.println("AsServer Process Time: " + (System.nanoTime() - startTime) / 1000000000.0);
                } catch (org.zeromq.ZMQException e) {
                    // 处理Socket通信异常
                    Log.w(TAG, "服务器发送数据时Socket操作被中断: " + e.getMessage());
                    checkSystemStatus();
                    return flag;
                }
                
                // 再次检查系统状态
                if (checkSystemStatus()) {
                    return flag;
                }

                // 第四步：获取尾节点结果
                startTime = System.nanoTime();
                try {
                    flag = obtainResultsFromTailer(receivedId);  // 获取处理状态标志
                    System.out.println("ObtainResult Process Time: " + (System.nanoTime() - startTime) / 1000000000.0);
                } catch (Exception e) {
                    // 处理结果获取异常
                    Log.w(TAG, "获取尾节点结果时发生异常: " + e.getMessage());
                    checkSystemStatus();
                    return flag;
                }
            } catch (Exception e) {
                // 处理其他未预期异常
                Log.e(TAG, "推理流程中发生未预期异常: " + e.getMessage(), e);
                // 不抛出异常，防止应用崩溃
            }
            
            return flag;  // 返回处理状态标志
        }
        
        /**
         * 检查系统状态
         * 
         * @return true如果系统处于故障恢复状态或推理已停止，false表示正常运行
         */
        private boolean checkSystemStatus() {
            if ("Recovery".equals(param.status) || "Recovering".equals(param.status)) {
                Log.d(TAG, "系统当前处于故障恢复状态，推理步骤中断");
                return true;
            }
            
            if (!isRunning) {
                Log.d(TAG, "推理已被中断，当前步骤终止");
                return true;
            }
            
            return false;
        }
    }

    /**
     * 更新通信Socket的方法
     * 为每个处理核心创建与前驱和后继节点通信的Socket
     * 
     * @param corePoolSize 线程池核心大小，决定创建的Socket组数量
     * @throws InterruptedException 线程中断时抛出
     */
    public void updateSockets(int corePoolSize) throws InterruptedException {
        Log.d(TAG, "开始执行updateSockets，核心池大小: " + corePoolSize);
        Log.d(TAG, "发送设备集合: " + sendDeviceIndex + ", 接收设备集合: " + receiveDeviceIndex);
        
        int j = cfg.ipGraph.length;  // 获取IP图的长度（设备总数）
        System.out.println("Graph length: " + j);
        Log.d(TAG, "IP图内容: " + Arrays.toString(cfg.ipGraph) + ", 当前设备ID: " + cfg.deviceId);
        
        // 为每个核心创建一组Socket
        for (int i = 0; i < corePoolSize; i++) {
            Log.d(TAG, "创建核心 " + i + " 的Socket组");
            ArrayList<Map<Integer, Socket>> socketContainer = new ArrayList<>();  // 创建Socket容器
            
            // 创建发送Socket映射
            Log.d(TAG, "开始创建发送Socket映射, 设备ID: " + cfg.deviceId);
            Map<Integer, Socket> SendSocket = new HashMap<>();
            for (Integer idx : sendDeviceIndex) {  // 遍历需要发送数据的设备索引
                try {
                    int portNum = Config.port + j*i + (idx-cfg.deviceId);
                    Log.d(TAG, "尝试创建发送Socket至设备: " + idx + ", 端口: " + portNum);
                    
                    // 创建路由型Socket（多对多通信）
                    Socket temp = beServer.establish_connection(context, SocketType.ROUTER, 
                            portNum);  // 计算唯一端口号
                    Log.d(TAG, "创建路由型Socket（多对多通信）成功，端口: " + portNum);
                    // 设置Socket标识
                    temp.setIdentity(("ROUTER Send From " + cfg.deviceId + " to " + idx + "." + 
                            portNum).getBytes());
                    Log.d(TAG, "设置Socket标识成功");
                    // 将Socket添加到映射中
                    SendSocket.put(idx, temp);
                    Log.d(TAG, "成功创建发送Socket至设备: " + idx);
                } catch (Exception e) {
                    Log.e(TAG, "创建发送Socket至设备: " + idx + " 失败: " + e.getMessage(), e);
                }
            }

            // 如果当前节点是尾节点，需要额外创建与头节点通信的Socket
            if (cfg.isTailer()){
                try {
                    int portNum = Config.port + j*i + 1;
                    Log.d(TAG, "尾节点创建额外Socket至头节点, 端口: " + portNum);
                    Socket temp = beServer.establish_connection(context, SocketType.ROUTER, 
                            portNum);  // 使用特定端口
                    
                    temp.setIdentity(("ROUTER Send From " + cfg.deviceId + " to " + 
                            cfg.nextDeviceId() + "." + portNum).getBytes());
                    
                    SendSocket.put(cfg.nextDeviceId(), temp);
                    Log.d(TAG, "尾节点成功创建额外Socket至头节点: " + cfg.nextDeviceId());
                } catch (Exception e) {
                    Log.e(TAG, "尾节点创建额外Socket至头节点失败: " + e.getMessage(), e);
                }
            }
            
            // 将发送Socket映射添加到容器
            socketContainer.add(SendSocket);
            Log.d(TAG, "发送Socket映射已添加到容器，大小: " + SendSocket.size());

            // 创建接收Socket映射
            Log.d(TAG, "开始创建接收Socket映射");
            Map<Integer, Socket> receiveSocket = new HashMap<>();
            for (Integer idx : receiveDeviceIndex) {  // 遍历需要接收数据的设备索引
                try {
                    int portNum = Config.port + j*i + (cfg.deviceId-idx);
                    String targetIP = cfg.ipGraph[idx];
                    Log.d(TAG, "尝试创建接收Socket从设备: " + idx + ", IP: " + targetIP + 
                          ", 端口: " + portNum);
                    
                    // 创建经销商型Socket（多对一通信）
                    Socket temp = beClient.establish_connection(context, SocketType.DEALER, 
                            portNum,  // 计算唯一端口号
                            targetIP);  // 连接目标设备IP
                    
                    // 设置Socket标识
                    temp.setIdentity(("DEALER Receive From: " + cfg.deviceId + " to " + 
                            idx + "." + portNum).getBytes());
                    
                    // 将Socket添加到映射中
                    receiveSocket.put(idx, temp);
                    Log.d(TAG, "成功创建接收Socket从设备: " + idx);
                } catch (Exception e) {
                    Log.e(TAG, "创建接收Socket从设备: " + idx + " 失败: " + e.getMessage() + 
                           ", IP: " + cfg.ipGraph[idx] + ", 端口: " + (Config.port + j*i + (cfg.deviceId-idx)), e);
                }
            }

            // 如果当前节点是头节点，需要额外创建与尾节点通信的Socket
            if (cfg.isHeader()){
                try {
                    int portNum = Config.port + j*i + 1;
                    String targetIP = cfg.prevNodes.get(0);
                    Log.d(TAG, "头节点创建额外Socket从尾节点, IP: " + targetIP + 
                          ", 端口: " + portNum);
                    Socket temp = beClient.establish_connection(context, SocketType.DEALER, 
                            portNum,  // 使用特定端口
                            targetIP);  // 连接前驱节点IP
                    
                    temp.setIdentity(("DEALER Receive From: " + cfg.deviceId + " to " + 
                            cfg.nextDeviceId() + "." + portNum).getBytes());
                    
                    receiveSocket.put(cfg.prevDeviceId(), temp);
                    Log.d(TAG, "头节点成功创建额外Socket从尾节点: " + cfg.prevDeviceId());
                } catch (Exception e) {
                    Log.e(TAG, "头节点创建额外Socket从尾节点失败: " + e.getMessage() + 
                           ", IP: " + cfg.prevNodes.get(0) + ", 端口: " + (Config.port + j*i + 1), e);
                }
            }

            // 将接收Socket映射添加到容器
            socketContainer.add(receiveSocket);
            Log.d(TAG, "接收Socket映射已添加到容器，大小: " + receiveSocket.size());

            try {
                // 将整个Socket容器添加到全局Socket列表
                allSockets.put(socketContainer);
                Log.d(TAG, "Socket容器已添加到全局队列");
            } catch (Exception e) {
                Log.e(TAG, "添加Socket容器到全局队列失败: " + e.getMessage(), e);
            }
        }

        Log.d(TAG, "updateSockets函数执行完成");
        System.out.println("Sockets are built successfully");  // 输出Socket创建成功信息
    }

    /**
     * 获取需要发送残差数据的设备与模块映射
     * 用于优化模型间残差连接的通信
     */
    public void getSendResDevice2Device(){
        sendD2D = new TreeMap<>();  // 创建有序映射存储发送残差数据的设备-模块关系
        
        // 遍历所有发送索引
        for (ArrayList<JSONObject> sendIndexList : sendIndex.values()) {
            // 如果存在残差索引（通常是第二个索引）
            if (sendIndexList.size() > 1) {
                JSONObject sendResIndex = sendIndexList.get(1);  // 获取残差索引
                Iterator<String> keys = sendResIndex.keys();  // 获取所有模块键
                
                // 遍历所有模块
                while (keys.hasNext()) {
                    String k = keys.next();  // 获取模块名称
                    int device = module_on_devices.get(k);  // 获取模块所在设备ID
                    
                    // 如果模块不在当前设备上，需要通过网络发送
                    if (device != cfg.deviceId) {
                        // 如果映射中不存在该设备，则创建新列表
                        if (!sendD2D.containsKey(device))
                            sendD2D.put(device, new ArrayList<>());
                        
                        // 将模块名称添加到对应设备的列表中
                        sendD2D.get(device).add(k);
                    }
                }
            }
        }
        
        // 对每个设备的模块列表进行排序，确保操作顺序一致
        for (List<String> i : sendD2D.values())
            Collections.sort(i);
    }

    /**
     * 获取需要接收残差数据的设备与模块映射
     * 用于优化模型间残差连接的通信
     */
    public void getReceiveResDevice2Device(){
        receiveD2D = new TreeMap<>();  // 创建有序映射存储接收残差数据的设备-模块关系
        
        // 遍历所有接收索引
        for (Map.Entry<String, ArrayList<JSONObject>> receiveIndexList : receiveIndex.entrySet()) {
            // 如果存在残差索引（通常是第二个索引）
            if (receiveIndexList.getValue().size() > 1) {
                JSONObject receiveResIndex = receiveIndexList.getValue().get(1);  // 获取残差索引
                Iterator<String> keys = receiveResIndex.keys();  // 获取所有模块键
                
                // 遍历所有模块
                while (keys.hasNext()) {
                    String k = keys.next();  // 获取模块名称
                    int device = module_on_devices.get(k);  // 获取模块所在设备ID
                    
                    // 如果模块不在当前设备上，需要通过网络接收
                    if (device != cfg.deviceId) {
                        // 如果映射中不存在该设备，则创建新列表
                        if (!receiveD2D.containsKey(device))
                            receiveD2D.put(device, new ArrayList<>());
                        
                        // 将当前模块名称添加到对应设备的列表中
                        receiveD2D.get(device).add(receiveIndexList.getKey());
                    }
                }
            }
        }
        
        // 对每个设备的模块列表进行排序，确保操作顺序一致
        for (List<String> i : receiveD2D.values())
            Collections.sort(i);
    }

    public void RecordResult(Object[] result) {
        // 假设每个 result[i] 的类型都是 byte[]
        // 计算所有数组的总长度
        int totalLength = 0;
        for (Object obj : result) {
            byte[] bytes = (byte[]) obj;
            totalLength += bytes.length;
        }

        // 创建一个足够大的数组以容纳所有字节
        byte[] res = new byte[totalLength];
        int currentIndex = 0;
        for (Object obj : result) {
            byte[] bytes = (byte[]) obj;
            System.arraycopy(bytes, 0, res, currentIndex, bytes.length);
            currentIndex += bytes.length;
        }

        // 根据文件是否存在来构造唯一的文件名
        int fileIndex = 0;
        String fileName = "result_" + fileIndex + ".bin";
        File file = new File(conText.getFilesDir(), fileName);
        while (file.exists()) {
            fileIndex++;
            fileName = "result_" + fileIndex + ".bin";
            file = new File(conText.getFilesDir(), fileName);
        }

        // 将合并后的字节数组写入文件
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(res);
            fos.flush();
            Log.d(TAG, "File " + fileName + " saved at: " + file.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    class SendResidualConnection implements Runnable {
        int receiveId;
        Map<Integer, Socket> clientSide;
        public SendResidualConnection(int receiveId, Map<Integer, Socket> clientSide) {
            this.receiveId = receiveId;
            this.clientSide = clientSide;
        }

        @Override
        public void run() {

            for (Map.Entry<Integer, ArrayList<String>> entry : sendD2D.entrySet()) {
                int target_device_id = entry.getKey();
                System.out.println("Send to device "+ target_device_id);
                Socket sendSocket = this.clientSide.get(target_device_id);
                System.out.println(new String(sendSocket.getIdentity()));
                byte[] comefrom_id = sendSocket.recv(0);
                int target_id = Utils.convertByteArrayToInt(sendSocket.recv(0));
                assert target_id == target_device_id;
                byte[] msgTo = sendSocket.recv(0);
                System.out.println(new String(msgTo));
                if (new String(msgTo).contains("Request Res Data")) {
                    sendSocket.sendMore(comefrom_id);
                    sendSocket.sendMore(Utils.convertIntToByteArray(cfg.deviceId));
                    System.out.println("Target Device ID: " + target_id);
                    List<String> sendByte = entry.getValue();
                    for (String k : sendByte) {
                        ArrayList<byte[]> data = ResidualDataToDevice.get(receiveId).get(k);
                        for (byte[] i : data)
                            sendSocket.sendMore(i);
                        sendSocket.sendMore(";");
                    }
                    sendSocket.send("Over");
                }
                System.out.println("Send the Residual Data to Device " + entry.getKey());
            }
        }
    }


    class ReceiveResidualConnection implements Runnable {
        int receiveId;
        Map<Integer, Socket> serverSide;

        public ReceiveResidualConnection(int receiveId, Map<Integer, Socket> serverSide) {
            this.receiveId = receiveId;
            this.serverSide = serverSide;
        }

        @Override
        public void run() {
            receiveIndex.keySet();
            for (Map.Entry<Integer, ArrayList<String>> entry : receiveD2D.entrySet()) {
                Socket receiveSocket = serverSide.get(entry.getKey());
                System.out.println(new String(receiveSocket.getIdentity()));
                receiveSocket.sendMore(Utils.convertIntToByteArray(cfg.deviceId));
                receiveSocket.send("Request Res Data");
                int send_device_id = Utils.convertByteArrayToInt(receiveSocket.recv(0));
                System.out.println("Actual Receive the Residual Data from Device " + send_device_id);

                int i = 0;
                List<String> keyOnDevices = entry.getValue();
                Map<String, ArrayList<byte[]>> tmpReceiver = ResidualDataFromDevice.get(receiveId); // get()方法返回的是对象的引用而不是副本
//                if (!tmpReceiver.containsKey(keyOnDevices.get(i)))
//                    tmpReceiver.put(keyOnDevices.get(i), new ArrayList<>());
                if (tmpReceiver == null){
                    tmpReceiver = new TreeMap<>();
                    ResidualDataFromDevice.put(receiveId, tmpReceiver);
                }
                tmpReceiver.put(keyOnDevices.get(i), new ArrayList<>());

                while (true) {
                    byte[] data = receiveSocket.recv(0);
                    if (new String(data).equals("Over")) {
                        break;
                    }else if (new String(data).equals(";")) {
                        i += 1;
                        if (keyOnDevices.size() > i && !tmpReceiver.containsKey(keyOnDevices.get(i)))
                            tmpReceiver.put(keyOnDevices.get(i), new ArrayList<>());
                    }else {
                        tmpReceiver.get(keyOnDevices.get(i)).add(data);
                    }
                }

                System.out.println("Receive the Residual Data from Device " + entry.getKey());
                System.out.println(this.receiveId + " With the idx and size ");
            }
        }
    }


    public Socket getSocketsInQueue(LinkedBlockingQueue<Socket> queue, String identity) {
//        Byte[] identity = ("server: " + Config.local + "."+ 1).getBytes();
        Iterator<Socket> iterator = queue.iterator();
        while (iterator.hasNext()) {
            Socket item = iterator.next();
            if (Arrays.toString(item.getIdentity()).equals(identity)) {
                iterator.remove();  // Remove the current item
                return item;
            }
        }
        return null;
    }

    //    public native byte[] performInferenceMaster(long session, String input_string, long tokenizer);

    public native int tensorSizeDebug(byte[] logits);
    public native byte[] performInferenceMaster(long session, int[] input_ids);
    public native byte[] performInferenceWorker(long session, byte[] data);
    public native int binaryClassify(byte[] data);
    public native int[] encodeString(String input_string, long tokenizer);
    public native int greedyDecoding(byte[] data);
    public native String decodeID(int[] data, long tokenizer);

    public native Double modelFlopsPerSecond(int modelFlops, long session, int[] input_ids_j);
    public native Object runInferenceMasterResidual(long session, int[] input_ids_j, int[] to_send_seq_indices, int[][] to_send_res_indices);
    public native Object runInferenceWorkerResidual(long session,  byte[] sequential_input, ArrayList<byte[]> residual_input, int[] to_send_seq_indices, int[][] to_send_res_indices);
    public native byte[] runInferenceWorkerResidualLast(long session, byte[] sequential_input, ArrayList<byte[]>  residual_input);

    public native byte[] runInferenceWorkerResidualLastGeneration(long session,
                                                                  byte[] sequential_input,
                                                                  ArrayList<byte[]>  residual_input,
                                                                  int k,
                                                                  float init_temp);

    public native byte[] runInferenceWorkerResidualLastClassification(long session, byte[] sequential_input, ArrayList<byte[]>  residual_input);

    public native int deserializeInt(byte[] decode_id);

    public native int TokenToID(String token, long tokenizer);

    public native boolean EosCheck(byte[] output, long tokenizer); // TODO: adding EOS string check for generation early stopping - Junchen 02/28/2024

//    public native OnnxValue[] DeserializeTensor(byte[] data);

    /**
     * 安全终止推理流程
     * 故障恢复时调用此方法终止现有的推理线程
     * 
     * @return 是否成功停止推理
     */
    public boolean stopInference() {
        try {
            Log.d(TAG, "请求停止推理流程");
            
            // 设置停止标志
            isRunning = false;
            
            // 如果有推理线程池在运行，尝试终止它
            if (pool != null && !pool.isTerminated()) {
                Log.d(TAG, "开始关闭推理线程池...");
                
                // 首先尝试正常关闭线程池，允许任务完成
                pool.shutdown();
                
                // 设置Socket超时，避免永久阻塞
                if (allSockets != null && !allSockets.isEmpty()) {
                    ArrayList<ArrayList<Map<Integer, Socket>>> socketsCopy = new ArrayList<>();
                    allSockets.drainTo(socketsCopy);
                    
                    Log.d(TAG, "设置所有Socket超时为100毫秒");
                    for (ArrayList<Map<Integer, Socket>> socketPair : socketsCopy) {
                        for (Map<Integer, Socket> socketMap : socketPair) {
                            for (Socket socket : socketMap.values()) {
                                try {
                                    // 设置较短的接收超时
                                    socket.setReceiveTimeOut(100);
                                    
                                    // 尝试发送一个"INTERRUPT"消息，唤醒等待接收的线程
                                    socket.send("INTERRUPT", ZMQ.DONTWAIT);
                                } catch (Exception e) {
                                    // 忽略发送错误，继续处理
                                    Log.w(TAG, "设置Socket参数失败: " + e.getMessage());
                                }
                            }
                        }
                    }
                    
                    // 将Socket放回队列
                    for (ArrayList<Map<Integer, Socket>> socketPair : socketsCopy) {
                        allSockets.put(socketPair);
                    }
                }
                
                // 等待一段时间让推理线程自行响应终止标志
                try {
                    Log.d(TAG, "等待推理线程正常终止...");
                    boolean terminated = pool.awaitTermination(5000, TimeUnit.MILLISECONDS);
                    
                    // 如果超时仍未终止，强制关闭
                    if (!terminated) {
                        Log.w(TAG, "推理线程池未能自行终止，强制关闭");
                        List<Runnable> pendingTasks = pool.shutdownNow();
                        Log.d(TAG, "未执行的任务数量: " + pendingTasks.size());
                        
                        // 再等待一段时间，确保线程被中断
                        try {
                            terminated = pool.awaitTermination(2000, TimeUnit.MILLISECONDS);
                            if (!terminated) {
                                Log.e(TAG, "强制中断后线程池仍未完全关闭");
                            }
                        } catch (InterruptedException ie) {
                            Log.w(TAG, "等待线程池关闭时被中断");
                            Thread.currentThread().interrupt(); // 重置中断状态
                        }
                    }
                } catch (InterruptedException e) {
                    Log.w(TAG, "等待线程池关闭时被中断");
                    // 如果当前线程被中断，恢复中断状态并继续处理
                    Thread.currentThread().interrupt();
                    // 强制关闭线程池
                    pool.shutdownNow();
                }
                
                Log.d(TAG, "推理线程已终止");
                return true;
            } else {
                Log.d(TAG, "没有活动的推理线程需要停止");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "停止推理失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

}


