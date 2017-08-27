package zengrun.com.mydownloader.database;

/**
 * Created by zengrun on 2017/8/21.
 */

public class DownloadInfo {
    private String taskID;
    private String url;
    private String filePath;
    private String fileName;
    private long fileSize;
    private long downloadSize;

    public String getTaskID() {
        return taskID;
    }
    public void setTaskID(String taskID) {
        this.taskID = taskID;
    }
    public String getUrl() {
        return url;
    }
    public void setUrl(String url) {
        this.url = url;
    }
    public String getFilePath() {
        return filePath;
    }
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    public String getFileName() {
        return fileName;
    }
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    public long getFileSize() {
        return fileSize;
    }
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }
    public long getDownloadSize() {
        return downloadSize;
    }
    public void setDownloadSize(long downloadSize) {
        this.downloadSize = downloadSize;
    }

    @Override
    public String toString() {
        return "taskID="+taskID+";url="+url+";filePath="+filePath+";fileName="+fileName+";fileSize="+fileSize+";downloadSize="+downloadSize;
    }
}
