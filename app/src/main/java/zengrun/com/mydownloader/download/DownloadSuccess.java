package zengrun.com.mydownloader.download;

/**
 * 任务完成通知DLManager,以便将已完成的任务移出任务列表
 * Created by zengrun on 2017/8/21.
 */

public interface DownloadSuccess {
    void onTaskSuccess(String TaskID);
}
