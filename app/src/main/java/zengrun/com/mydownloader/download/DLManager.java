package zengrun.com.mydownloader.download;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import zengrun.com.mydownloader.database.DBAccessor;
import zengrun.com.mydownloader.database.FileHelper;
import zengrun.com.mydownloader.database.DownloadInfo;

/**
 * 下载任务管理调度
 * Created by zengrun on 2017/8/21.
 */

public class DLManager {
    private final String TAG = "DLManager";
    private Context mycontext;

    public List<DLWorker> workerList = new ArrayList<DLWorker>();

    private final int MAX_DOWNLOADING_TASK = 5; // 最大同时下载数

    private DownloadSuccess downloadSuccess = null;

    /**服务器是否支持断点续传*/
    private boolean isSupportBreakpoint = false;

    //线程池
    private ThreadPoolExecutor pool;

    private DLListener tasklistener;

    public DLManager(Context context) {
        mycontext = context;
        init(context);
    }

    private void init(Context context) {
        pool = new ThreadPoolExecutor(MAX_DOWNLOADING_TASK, MAX_DOWNLOADING_TASK, 30, TimeUnit.SECONDS,
                                      new ArrayBlockingQueue<Runnable>(2000));

        downloadSuccess = new DownloadSuccess() {
            @Override
            public void onTaskSuccess(String TaskID) {
                int size = workerList.size();
                for (int i = 0; i < size; i++) {
                    DLWorker downloader = workerList.get(i);
                    if (downloader.getTaskID().equals(TaskID)) {
                        workerList.remove(downloader);
                        return;
                    }
                }
            }
        };
        recoverData(mycontext);
    }


    /**
     * (从数据库恢复下载任务信息)
     * @param context 上下文
     */
    private void recoverData(Context context){
        stopAllTask();
        workerList = new ArrayList<>();
        DBAccessor accessor = new DBAccessor(context);
        List<DownloadInfo> sqlDownloadInfoList = accessor.getAllDownLoadInfo();
        if (sqlDownloadInfoList.size() > 0) {
            int listSize = sqlDownloadInfoList.size();
            for (int i = 0; i < listSize; i++) {
                DownloadInfo downloadInfo = sqlDownloadInfoList.get(i);
                DLWorker dloader = new DLWorker(context, downloadInfo, pool,isSupportBreakpoint,false);
                dloader.setDownLoadSuccess(downloadSuccess);
                dloader.setDlListener(tasklistener);
                workerList.add(dloader);
            }
        }
    }


    /**
     * (设置下载管理是否支持断点续传)
     * @param isSupportBreakpoint
     */
    public void setSupportBreakpoint(boolean isSupportBreakpoint) {
        if((!this.isSupportBreakpoint) && isSupportBreakpoint){
            int taskSize = workerList.size();
            for (int i = 0; i < taskSize; i++) {
                DLWorker downloader = workerList.get(i);
                downloader.setSupportBreakpoint(true);
            }
        }
        this.isSupportBreakpoint = isSupportBreakpoint;
    }


    /**
     * 新增任务，默认开始执行下载任务
     * @param TaskID 任务号
     * @param url 请求下载的路径
     * @param fileName 文件名
     * @return -1 : 文件已存在 ，0 ： 已存在任务列表 ， 1 ： 添加进任务列表
     */
    public int addTask(String TaskID, String url, String fileName) {
        return addTask(TaskID, url, fileName, null);
    }

    /**
     * 新增任务，默认开始执行下载任务
     * @param TaskID 任务号
     * @param url 请求下载的路径
     * @param fileName 文件名
     * @param filepath 下载到本地的路径
     * @return -1 : 文件已存在 ，0 ： 已存在任务列表 ， 1 ： 添加进任务列表
     */
    public int addTask(String TaskID, String url, String fileName, String filepath) {
        if(TaskID == null){
            TaskID = fileName;
        }
        DownloadInfo downloadinfo = new DownloadInfo();
        downloadinfo.setDownloadSize(0);
        downloadinfo.setFileSize(0);
        downloadinfo.setTaskID(TaskID);
        downloadinfo.setFileName(fileName);
        downloadinfo.setUrl(url);
        if (filepath == null) {
            downloadinfo.setFilePath(FileHelper.getFileDefaultPath() + "/(" + FileHelper.filterIDChars(TaskID) + ")" + fileName);
        } else {
            downloadinfo.setFilePath(filepath);
        }
        DLWorker taskDLWorker = new DLWorker(mycontext, downloadinfo, pool,isSupportBreakpoint,true);
        taskDLWorker.setDownLoadSuccess(downloadSuccess);
        taskDLWorker.start();
        taskDLWorker.setDlListener(tasklistener);
        workerList.add(taskDLWorker);
        return 1;
    }

    /**
     * 任务列表的所有任务ID
     * @return
     */
    public List<String> getAllTaskID() {
        List<String> taskIDlist = new ArrayList<String>();
        int listSize = workerList.size();
        for (int i = 0; i < listSize; i++) {
            DLWorker downloader = workerList.get(i);
            taskIDlist.add(downloader.getTaskID());
        }
        return taskIDlist;
    }

    /**
     * (获取当前任务列表的所有任务，以TaskInfo列表的方式返回)
     *
     * @return
     */
    public List<TaskInfo> getAllTask() {
        ArrayList<TaskInfo> taskInfolist = new ArrayList<TaskInfo>();
        int listSize = workerList.size();
        for (int i = 0; i < listSize; i++) {
            DLWorker DLWorker = workerList.get(i);
            DownloadInfo downloadinfo = DLWorker.getSQLDownLoadInfo();
            TaskInfo taskinfo = new TaskInfo();
            taskinfo.setFileName(downloadinfo.getFileName());
            taskinfo.setOnDownloading(DLWorker.isDownLoading());
            taskinfo.setTaskID(downloadinfo.getTaskID());
            taskinfo.setFileSize(downloadinfo.getFileSize());
            taskinfo.setDownFileSize(downloadinfo.getDownloadSize());
            taskInfolist.add(taskinfo);
        }
        return taskInfolist;
    }

    /**
     * 开始任务
     * @param taskID
     */
    public void startTask(String taskID) {
        int listSize = workerList.size();
        for (int i = 0; i < listSize; i++) {
            DLWorker dlworker = workerList.get(i);
            if (dlworker.getTaskID().equals(taskID)) {
                dlworker.start();
                break;
            }
        }
    }

    /**
     * 停止任务
     * @param taskID
     */
    public void stopTask(String taskID) {
        int listSize = workerList.size();
        for (int i = 0; i < listSize; i++) {
            DLWorker worker = workerList.get(i);
            if (worker.getTaskID().equals(taskID)) {
                worker.stop();
                break;
            }
        }
    }

    /**
     * 重新下载
     * @param taskID
     */
    public void restartTask(String taskID){
        int listSize = workerList.size();
        for(int i=0;i<listSize;i++){
            DLWorker worker = workerList.get(i);
            if(worker.getTaskID().equals(taskID)){
                worker.destroy();
                worker.start();
                break;
            }
        }
    }

    /**
     * 删除任务同时删除文件
     * @param taskID
     */
    public void deleteTask(String taskID) {
        int size = workerList.size();
        for (int i = 0; i < size; i++) {
            DLWorker worker = workerList.get(i);
            if (worker.getTaskID().equals(taskID)) {
                worker.destroy();
                workerList.remove(worker);
                break;
            }
        }
    }

    /**
     * 开始所有
     */
    public void startAllTask() {
        int listSize = workerList.size();
        for (int i = 0; i < listSize; i++) {
            DLWorker downloader = workerList.get(i);
            downloader.start();
        }
    }

    /**
     * 停止所有
     */
    public void stopAllTask() {
        int size = workerList.size();
        for (int i = 0; i < size; i++) {
            DLWorker worker = workerList.get(i);
            worker.stop();
        }
    }

    /**
     * 设置监听器
     * @param listener
     */
    public void setAllTaskListener(DLListener listener) {
        tasklistener = listener;
        int listSize = workerList.size();
        for (int i = 0; i < listSize; i++) {
            DLWorker downloader = workerList.get(i);
            downloader.setDlListener(listener);
        }
    }

    /**
     * 是否正在下载
     * @param taskID
     * @return
     */
    public boolean isTaskdownloading(String taskID) {
        DLWorker DLWorker = getDownloader(taskID);
        if (DLWorker != null) {
            return DLWorker.isDownLoading();
        }
        return false;
    }

    /**
     * 获取下载器
     */
    private DLWorker getDownloader(String taskID) {
        for (int i = 0; i < workerList.size(); i++) {
            DLWorker downloader = workerList.get(i);
            if (taskID != null && taskID.equals(downloader.getTaskID())) {
                return downloader;
            }
        }
        return null;
    }

    /**
     * 获取下载任务
     */
    public TaskInfo getTaskInfo(String taskID) {
        DLWorker downloader = getDownloader(taskID);
        if (downloader==null) {
            return null;
        }
        DownloadInfo sqldownloadinfo = downloader.getSQLDownLoadInfo();
        if (sqldownloadinfo==null) {
            return null;
        }
        TaskInfo taskinfo = new TaskInfo();
        taskinfo.setFileName(sqldownloadinfo.getFileName());
        taskinfo.setOnDownloading(downloader.isDownLoading());
        taskinfo.setTaskID(sqldownloadinfo.getTaskID());
        taskinfo.setDownFileSize(sqldownloadinfo.getDownloadSize());
        taskinfo.setFileSize(sqldownloadinfo.getFileSize());
        return taskinfo;
    }
}
