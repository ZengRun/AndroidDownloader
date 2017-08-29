package zengrun.com.mydownloader.download;

import zengrun.com.mydownloader.database.DownloadInfo;

/**
 * Created by zengrun on 2017/8/22.
 */

public interface DLListener {

    /**
     * (开始下载文件)
     * @param downloadInfo 下载任务对象
     */
    void onStart(DownloadInfo downloadInfo);

    /**
     * (文件下载进度情况)
     * @param downloadInfo 下载任务对象
     * @param isSupportBreakpoint 服务器是否支持断点续传
     */
    void onProgress(DownloadInfo downloadInfo, boolean isSupportBreakpoint);

    /**
     * (停止下载完毕)
     * @param downloadInfo 下载任务对象
     * @param isSupportBreakpoint 服务器是否支持断点续传
     */
    void onStop(DownloadInfo downloadInfo, boolean isSupportBreakpoint);

    /**
     * (文件下载失败)
     * @param downloadInfo 下载任务对象
     */
    void onError(DownloadInfo downloadInfo,int errorCode);


    /**
     * (文件下载成功)
     * @param downloadInfo 下载任务对象
     */
    void onSuccess(DownloadInfo downloadInfo);
}
