package com.example.SecureConnection;

import static com.example.distribute_ui.service.BackgroundService.TAG;

import android.util.Log;

import org.zeromq.SocketType;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class  Server {
    /***
     On behalf of the server, communicate with the client devices.
     ***/

    public Map<String, Socket> nextNodes;

    public Server() {}

    public Socket establish_connection(ZContext context, SocketType type, int port) {
        Socket socket = context.createSocket(type);

        Log.d(TAG, " Socket socket = context.createSocket(type);成功");
        // 检查端口是否被占用
        try {
            socket.bind("tcp://*:" + port);
            Log.d(TAG, "成功绑定端口 " + port);
        } catch (ZMQException e) {
            Log.e(TAG, "端口 " + port + " 绑定失败: " + e.getMessage() + ", 错误码: " + e.getErrorCode());
            if (e.getErrorCode() == ZMQ.Error.EADDRINUSE.getCode()) {
                Log.e(TAG, "端口 " + port + " 已被占用，无法绑定");
            }
            // 重新抛出异常，保持原有行为
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "绑定端口 " + port + " 时发生未知异常: " + e.getMessage());
            throw e;
        }
        
        Log.d(TAG, "socket.bind(\"tcp://*:\" + port);成功");
        socket.setIdentity(Config.local.getBytes());
        Log.d(TAG, "  socket.setIdentity(Config.local.getBytes());成功");
        return socket;
    }


//    class CommunicationAsServer implements Runnable {
//        private String targetIP;
//        private Socket sender;
//        private final CountDownLatch countDown;
//
//        CommunicationAsServer(String targetIp, Socket sender, CountDownLatch countDown) {
//            this.targetIP = targetIP;
//            this.sender = sender;
//            this.countDown = countDown;
//        }
//
//        @Override
//        public void run() {
//            try {
////                String msg = new String(sender.recv(0));
//                ZMsg receivedMsg = ZMsg.recvMsg(sender);
//                // Get the identity frame (client ID)
//                ZFrame identity = receivedMsg.unwrap();
//                String msg = receivedMsg.getLast().toString();
//
//                if (msg.contains("Request data")) {
//                    System.out.println(param.choice);
//                    ZMsg replyMsg = new ZMsg();
//                    replyMsg.wrap(identity.duplicate());
//                    countDown.await();
//                    replyMsg.add(OutputData.get(param.choice));
//                    replyMsg.send(sender);
//                }
//            } catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            }
//
//        }
//    }
}