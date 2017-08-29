package zengrun.com.mydownloader.database;

/**
 * Created by ZR on 2017/8/27.
 */

public class DownloadInfo {
    private String taskID;
    private String url;
    private String filePath;
    private String fileName;
    private long fileSize;
    private long downloadSize;
    private long start1;
    private long start2;
    private long start3;

    private long end1;
    private long end2;
    private long end3;

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

    public long getStart1() {
        return start1;
    }

    public void setStart1(long start1) {
        this.start1 = start1;
    }

    public long getStart2() {
        return start2;
    }

    public void setStart2(long start2) {
        this.start2 = start2;
    }

    public long getStart3() {
        return start3;
    }

    public void setStart3(long start3) {
        this.start3 = start3;
    }

    public long getEnd1() {
        return end1;
    }

    public void setEnd1(long end1) {
        this.end1 = end1;
    }

    public long getEnd2() {
        return end2;
    }

    public void setEnd2(long end2) {
        this.end2 = end2;
    }

    public long getEnd3() {
        return end3;
    }

    public void setEnd3(long end3) {
        this.end3 = end3;
    }

    public long getDownloadSize() {
        return downloadSize;
    }

    public void setDownloadSize(long downloadSize) {
        this.downloadSize = downloadSize;
    }

    @Override
    public String toString() {
        return "DownloadInfo2{" +
                "taskID='" + taskID + '\'' +
                ", url='" + url + '\'' +
                ", filePath='" + filePath + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileSize=" + fileSize +
                ", downloadSize=" + downloadSize +
                '}';
    }
}
