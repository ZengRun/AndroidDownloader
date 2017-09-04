package zengrun.com.mydownloader.download;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

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
    private Context myContext;

    public List<DLWorker> workerList = new ArrayList<DLWorker>();

    private final int MAX_DOWNLOADING_THREADS = 6; // 最大线程数

    private DownloadSuccess downloadSuccess = null;

    //线程池
    private ThreadPoolExecutor pool;

    private Handler handler;

    private volatile static DLManager INSTANCE;

    private DLManager(Context context) {
        myContext = context;
        init();
    }

    private void init() {
        pool = new ThreadPoolExecutor(MAX_DOWNLOADING_THREADS, MAX_DOWNLOADING_THREADS, 10, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(60));

        downloadSuccess = new DownloadSuccess() {
            @Override
            public void  onTaskSuccess(String TaskID) {
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
        recoverData(myContext);
    }

    public static DLManager getInstance(Context context){
        if (INSTANCE == null) {
            synchronized (DLManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new DLManager(context);
                }
            }
        }
        return INSTANCE;
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
                DLWorker dworker = new DLWorker(context, downloadInfo, pool,false);
                dworker.setDownLoadSuccess(downloadSuccess);
                dworker.setTaskHandler(handler);
                workerList.add(dworker);
            }
        }
        Log.v(TAG,"#####recover from database!");
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
        DLWorker taskDLWorker = new DLWorker(myContext, downloadinfo, pool,true);
        taskDLWorker.setDownLoadSuccess(downloadSuccess);
        taskDLWorker.start();
        taskDLWorker.setTaskHandler(handler);
        workerList.add(taskDLWorker);
        return 1;
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
     * @param listHandler
     */
    public void setAllTaskHandler(Handler listHandler) {
        handler = listHandler;
        int listSize = workerList.size();
        for (int i = 0; i < listSize; i++) {
            DLWorker downloader = workerList.get(i);
            downloader.setTaskHandler(listHandler);
        }
    }

}
