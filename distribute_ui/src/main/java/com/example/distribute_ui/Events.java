package com.example.distribute_ui;

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
}
