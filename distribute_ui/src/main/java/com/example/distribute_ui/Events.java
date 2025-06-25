package com.example.distribute_ui;

import java.util.List;

// 包含多个静态类，表示不同类型的事件（事件对象），用于在应用程序中进行数据传递和通信
// 这种事件驱动的设计用于实现解耦，让不同组件之间可以通过事件传递数据，而不需要直接依赖于对方
public class Events {
    // Event for registering the communication running status for service communication
    //  用于记录与服务器的通信是否处于正在运行状态
    public static class RunningStatusEvent{
        public final boolean isRunning; // 指示服务器是否在运行
        public RunningStatusEvent(boolean isRunning){
            this.isRunning = isRunning;
        }   // 类的构造函数
    }

    // Event for UI-service communication to let the background service know the inference chat can initiate
    // 用于向background service发送消息，请求推理服务
    public static class messageSentEvent{
        public final boolean messageSent;   // 记录消息是否已经发送
        public final String messageContent; // 存储发送的消息内容
        public messageSentEvent(boolean messageSent, String messageContent){
            this.messageSent = messageSent;
            this.messageContent = messageContent;
        }
    }

    // 用于指示是否进入聊天界面
    public static class enterChatEvent{
        public final boolean enterChat;     // 是否进入聊天界面
        public enterChatEvent(boolean enterChat){
            this.enterChat = enterChat;
        }
    }

    // 用于传递sampleId
    public static class sampleIdEvent{
        public final int sampleId;          // sampleId
        public sampleIdEvent(int sampleId){
            this.sampleId = sampleId;
        }
    }

    public static class AppBackgroundEvent {
        private final boolean isInBackground;

        public AppBackgroundEvent(boolean isInBackground) {
            this.isInBackground = isInBackground;
        }

        public boolean isInBackground() {
            return isInBackground;
        }
    }

    public static class GetBackgroundStatusEvent {
        private boolean isInBackground;

        public GetBackgroundStatusEvent() {
            this.isInBackground = false;
        }

        public void setInBackground(boolean inBackground) {
            isInBackground = inBackground;
        }

        public boolean isInBackground() {
            return isInBackground;
        }
    }

    // 用户交互日志事件
    public static class QueryLogEvent {
        public String deviceId;
        public String role;
        public String queryId;
        public String userQuery;
        public String response;
        // 四阶段详细时间戳
        public long clientReceiveStart;
        public long clientReceiveEnd;
        public long inferenceStart;
        public long inferenceEnd;
        public long serverSendStart;
        public long serverSendEnd;
        public long tailerResultStart;
        public long tailerResultEnd;
        public int tokens;
        public double throughput;
        // 故障相关
        public boolean hasFault;
        public long faultStartTime;
        public long faultRecoveryTime;
        // 新增：每个token的详细阶段时间戳
        public java.util.List<long[]> clientReceiveTimes;
        public java.util.List<long[]> inferenceTimes;
        public java.util.List<long[]> serverSendTimes;
        public java.util.List<long[]> tailerResultTimes;
        public QueryLogEvent(String deviceId, String role, String queryId, String userQuery, String response,
                             long clientReceiveStart, long clientReceiveEnd,
                             long inferenceStart, long inferenceEnd,
                             long serverSendStart, long serverSendEnd,
                             long tailerResultStart, long tailerResultEnd,
                             int tokens, double throughput,
                             boolean hasFault, long faultStartTime, long faultRecoveryTime,
                             java.util.List<long[]> clientReceiveTimes,
                             java.util.List<long[]> inferenceTimes,
                             java.util.List<long[]> serverSendTimes,
                             java.util.List<long[]> tailerResultTimes) {
            this.deviceId = deviceId;
            this.role = role;
            this.queryId = queryId;
            this.userQuery = userQuery;
            this.response = response;
            this.clientReceiveStart = clientReceiveStart;
            this.clientReceiveEnd = clientReceiveEnd;
            this.inferenceStart = inferenceStart;
            this.inferenceEnd = inferenceEnd;
            this.serverSendStart = serverSendStart;
            this.serverSendEnd = serverSendEnd;
            this.tailerResultStart = tailerResultStart;
            this.tailerResultEnd = tailerResultEnd;
            this.tokens = tokens;
            this.throughput = throughput;
            this.hasFault = hasFault;
            this.faultStartTime = faultStartTime;
            this.faultRecoveryTime = faultRecoveryTime;
            this.clientReceiveTimes = clientReceiveTimes;
            this.inferenceTimes = inferenceTimes;
            this.serverSendTimes = serverSendTimes;
            this.tailerResultTimes = tailerResultTimes;
        }
    }

    // 故障日志事件
    public static class FaultEvent {
        public String deviceId;
        public String role;
        public String faultType;
        public long faultTime;
        public long recoveryTime;
        public String affectedQueryId;
        public FaultEvent(String deviceId, String role, String faultType, long faultTime, long recoveryTime, String affectedQueryId) {
            this.deviceId = deviceId;
            this.role = role;
            this.faultType = faultType;
            this.faultTime = faultTime;
            this.recoveryTime = recoveryTime;
            this.affectedQueryId = affectedQueryId;
        }
    }

    // 能耗日志事件
    public static class EnergyEvent {
        public String deviceId;
        public String role;
        public long timestamp;
        public int battery;
        public double cpuUsage;
        public double temperature;
        public EnergyEvent(String deviceId, String role, long timestamp, int battery, double cpuUsage, double temperature) {
            this.deviceId = deviceId;
            this.role = role;
            this.timestamp = timestamp;
            this.battery = battery;
            this.cpuUsage = cpuUsage;
            this.temperature = temperature;
        }
    }

    // 复合日志事件：一轮对话的所有日志打包
    public static class SessionLogEvent {
        public QueryLogEvent queryLog;
        public List<FaultEvent> faultEvents;
        public List<EnergyEvent> energyEvents;
        public SessionLogEvent(QueryLogEvent queryLog, List<FaultEvent> faultEvents, List<EnergyEvent> energyEvents) {
            this.queryLog = queryLog;
            this.faultEvents = faultEvents;
            this.energyEvents = energyEvents;
        }
    }
}
