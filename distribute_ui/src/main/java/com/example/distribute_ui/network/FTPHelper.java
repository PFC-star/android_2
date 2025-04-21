package com.example.distribute_ui.network;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;

public class FTPHelper {

    /**
     * 以匿名登录方式下载文件，并实时输出下载速率
     * @param server FTP服务器地址
     * @param port FTP服务器端口号
     * @param remoteFilePath 服务器上文件路径
     * @param localFilePath 本地保存文件路径
     */
    public void downloadFile(final String server, final int port, final String remoteFilePath, final String localFilePath) {
        FTPClient ftpClient = new FTPClient();
        FileOutputStream fos = null;
        try {
            ftpClient.connect(server, port);
            boolean login = ftpClient.login("anonymous", "");
            if (!login) {
                System.out.println("FTP 匿名登录失败");
                return;
            }
            ftpClient.enterLocalPassiveMode();
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            InputStream is = ftpClient.retrieveFileStream(remoteFilePath);
            if (is == null) {
                System.out.println("无法获取输入流");
                return;
            }
            fos = new FileOutputStream(localFilePath);

            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalBytes = 0;
            long startTime = System.currentTimeMillis();

            long totalDownloadedBytes = 0L;          // 累计全部下载字节
            final long overallStartTime = startTime; // 记录整个下载开始时间

            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
                totalDownloadedBytes += bytesRead;

                long currentTime = System.currentTimeMillis();
                // 每隔5秒输出一次下载速率
                if (currentTime - startTime >= 5000) {
                    double speed = totalBytes / ((currentTime - startTime) / 1000.0);
                    double speedMB = speed / 1048576.0;
                    System.out.println("【客户端】下载速率：" + String.format("%.2f", speedMB) + " MB/s");
                    totalBytes = 0;
                    startTime = currentTime;
                }
            }
            fos.flush();
            fos.close();
            is.close();

            // 计算平均速度
            long overallEndTime = System.currentTimeMillis();
            double totalTimeSec = (overallEndTime - overallStartTime) / 1000.0;
            double avgSpeedMB = (totalDownloadedBytes / 1048576.0) / totalTimeSec;
            System.out.printf("【客户端】平均下载速度：%.2f MB/s%n", avgSpeedMB);

            boolean completed = ftpClient.completePendingCommand();
            if (completed) {
                System.out.println("文件下载成功");
            } else {
                System.out.println("文件下载失败");
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
            if (ftpClient.isConnected()) {
                try {
                    ftpClient.logout();
                    ftpClient.disconnect();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }
}
