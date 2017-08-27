package zengrun.com.mydownloader.download;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ThreadPoolExecutor;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import zengrun.com.mydownloader.database.DBAccessor;
import zengrun.com.mydownloader.ErrorCode;
import zengrun.com.mydownloader.database.FileHelper;
import zengrun.com.mydownloader.database.DownloadInfo;
import zengrun.com.mydownloader.security.SslUtils;

/**
 * 下载任务执行类
 * Created by zengrun on 2017/8/21.
 */

public class DLWorker {
    private int TASK_START = 0;
    private int TASK_STOP = 1;
    private int TASK_PROGESS = 2;
    private int TASK_ERROR = 3;
    private int TASK_SUCCESS = 4;

    /**文件暂存路径*/
    private final String TEMP_DIR = FileHelper.getTempDirPath();
    private final String TAG = "DLWorker";
    private final int maxDownloadTimes = 3;//失败重新请求次数

    /**标识服务器是否支持断点续传*/
    private boolean isSupportBreakpoint = false;

    private DBAccessor dbAccessor;
    private DLListener dlListener;
    private DownloadSuccess downloadsuccess;
    private DownloadInfo downloadInfo;
    private DownLoadThread downLoadThread;
    private long fileSize = 0;//文件总大小
    private long downloadedSize = 0;//已经下载的文件的大小
    private int downloadTimes = 0;//当前尝试请求的次数
    /**当前任务的状态 */
    private boolean ondownload = false;
    /**线程池 */
    private ThreadPoolExecutor pool;


    /**
     * @param context 上下文
     * @param sqlFileInfo 任务信息对象
     * @param pool  线程池
     * @param isSupportBreakpoint  服务器是否支持断点续传
     * @param isNewTask 标识是新任务还是根据数据库构建的任务
     */
    public DLWorker(Context context, DownloadInfo sqlFileInfo, ThreadPoolExecutor pool, boolean isSupportBreakpoint, boolean isNewTask){
        this.isSupportBreakpoint = isSupportBreakpoint;
        this.pool = pool;
        fileSize = sqlFileInfo.getFileSize();
        downloadedSize = sqlFileInfo.getDownloadSize();
        dbAccessor = new DBAccessor(context);
        downloadInfo = sqlFileInfo;
        //新建任务，保存任务信息到数据库
        if(isNewTask){
            saveDownloadInfo();
        }
    }

    public void start(){
        if(downLoadThread == null){
            downloadTimes = 0;
            ondownload = true;
            handler.sendEmptyMessage(TASK_START);
            downLoadThread = new DownLoadThread();
            pool.execute(downLoadThread);
        }
    }

    public void stop(){
        if(downLoadThread != null){
            ondownload = false;
            downLoadThread.stopDownLoad();
            pool.remove(downLoadThread);
            downLoadThread = null;
        }
    }

    public void destroy(){
        if(downLoadThread != null){
            downLoadThread.stopDownLoad();
            downLoadThread = null;
        }
        dbAccessor.deleteDownLoadInfo(downloadInfo.getTaskID());
        File downloadFile = new File(TEMP_DIR + "/(" + FileHelper.filterIDChars(downloadInfo.getTaskID()) + ")" + downloadInfo.getFileName());
        if(downloadFile.exists()){
            downloadFile.delete();
        }
    }

    public String getTaskID(){
        return downloadInfo.getTaskID();
    }

    public void setDlListener(DLListener dlListener) {
        this.dlListener = dlListener;
    }

    public void setDownLoadSuccess(DownloadSuccess downloadsuccess){
        this.downloadsuccess = downloadsuccess;
    }

    /**当前任务进行的状态 */
    public boolean isDownLoading(){
        return ondownload;
    }

    /**
     * (获取当前任务对象)
     * @return
     */
    public DownloadInfo getSQLDownLoadInfo(){
        downloadInfo.setDownloadSize(downloadedSize);
        return downloadInfo;
    }


    public void setSupportBreakpoint(boolean isSupportBreakpoint) {
        this.isSupportBreakpoint = isSupportBreakpoint;
    }


    /**
     * 下载线程
     */
    class DownLoadThread extends Thread{
        private boolean isDownloading;
        private URL url;
        private RandomAccessFile localFile;
        private HttpURLConnection conn;
        private InputStream inputStream;
        private int progress = -1;

        public DownLoadThread(){
            isDownloading = true;
        }

        @Override
        public void run() {
            while(downloadTimes < maxDownloadTimes){ //做3次请求的尝试
                try {
                    url = new URL(downloadInfo.getUrl());
                    if(downloadInfo.getUrl().startsWith("https")){
                        SslUtils.ignoreSsl();
                        conn = (HttpsURLConnection)url.openConnection();
                    }else{
                        conn = (HttpURLConnection)url.openConnection();
                    }
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(10000);
                    int resCode = 0; //下载之前获取的http返回码
                    if(fileSize < 1){//第一次下载，初始化
                        resCode = preDownload();
                    }else{
                        if(new File(TEMP_DIR + "/(" + FileHelper.filterIDChars(downloadInfo.getTaskID()) + ")" + downloadInfo.getFileName()).exists()){
                            Log.v(TAG,"file already exists");
                            localFile = new RandomAccessFile (TEMP_DIR + "/(" + FileHelper.filterIDChars(downloadInfo.getTaskID()) + ")" + downloadInfo.getFileName(),"rwd");
                            localFile.seek(downloadedSize);
                            conn.setRequestProperty("Range", "bytes=" + downloadedSize + "-");
                        }else{
                            fileSize = 0;
                            downloadedSize = 0;
                            saveDownloadInfo();
                            resCode = preDownload();
                        }
                    }

                    if(resCode>=400){
                        Message msg = new Message();
                        msg.what = TASK_ERROR;
                        msg.arg1 = resCode;
                        handler.sendMessage(msg);
                        return;
                    }

                    Log.v(TAG,"file size:"+fileSize+"bytes");
                    Log.v(TAG,"start downloading.....");
                    inputStream = conn.getInputStream();
                    byte[] buffer = new byte[1024 * 4];
                    int length = -1;
                    while((length = inputStream.read(buffer)) != -1 && isDownloading){
                        localFile.write(buffer, 0, length);
                        downloadedSize += length;
                        int nowProgress = (int)((100 * downloadedSize)/fileSize);
                        if(nowProgress > progress){
                            progress = nowProgress;
                            handler.sendEmptyMessage(TASK_PROGESS);
                        }
                    }
                    //下载完了
                    if(downloadedSize == fileSize ){
                        boolean renameResult = RenameFile();
                        if(renameResult){
                            handler.sendEmptyMessage(TASK_SUCCESS); //转移文件成功
                        }else{
                            new File(TEMP_DIR + "/(" + FileHelper.filterIDChars(downloadInfo.getTaskID()) + ")" + downloadInfo.getFileName()).delete();
                            Message msg = new Message();
                            msg.what=TASK_ERROR;
                            msg.arg1= ErrorCode.FILE_TRANS_ERROR;
                            handler.sendMessage(msg);//转移文件失败
                        }
                        //保存下载完成的状态
                        saveDownloadInfo();
                        downLoadThread = null;
                        ondownload = false;
                        Log.v(TAG,downloadInfo.getFileName()+"下载完成!!!!!");
                    }
                    downloadTimes = maxDownloadTimes;
                } catch (Exception e) {
                    if(isDownloading){
                        if(isSupportBreakpoint){
                            downloadTimes++;
                            if(downloadTimes >= maxDownloadTimes){
                                if(fileSize > 0){
                                    saveDownloadInfo();
                                }
                                pool.remove(downLoadThread);
                                downLoadThread = null;
                                ondownload = false;
                                Message msg = new Message();
                                msg.what= TASK_ERROR;
                                msg.arg1 = ErrorCode.DOWNLOAD_ERROR;
                                handler.sendMessage(msg);
                            }
                        }else{
                            downloadedSize = 0;
                            downloadTimes = maxDownloadTimes;
                            ondownload = false;
                            downLoadThread = null;
                            Message msg = new Message();
                            msg.what= TASK_ERROR;
                            msg.arg1 = ErrorCode.DOWNLOAD_ERROR;
                            handler.sendMessage(msg);
                        }
                    }else{
                        downloadTimes = maxDownloadTimes;
                    }
                    e.printStackTrace();
                }finally{
                    try {
                        if(conn != null){
                            conn.disconnect();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }try {
                        if(inputStream != null){
                            inputStream.close();
                        }
                    }catch (Exception e) {
                        e.printStackTrace();
                    }try {
                        if(localFile != null){
                            localFile.close();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        public void stopDownLoad(){
            isDownloading = false;
            downloadTimes = maxDownloadTimes;
            if(fileSize > 0){
                saveDownloadInfo();
                Log.v(TAG,"下载暂停，存储断点信息");
            }
            handler.sendEmptyMessage(TASK_STOP);
        }

        private int preDownload() throws Exception{
            int code = conn.getResponseCode();
            Log.v(TAG,"##################http ret code: "+code);
            if(code==HttpURLConnection.HTTP_MOVED_TEMP){//302 重定向
                Log.v(TAG,"302:redirect");
                //String redirect = conn.getHeaderField("Location");
                //url = new URL(redirect);
                url = conn.getURL();
                conn = (HttpURLConnection)url.openConnection();
            }else if(code>=400){
                Log.e(TAG,"request error happened:"+code);
                ondownload = false;
                downloadTimes = maxDownloadTimes;
                downLoadThread = null;
                return code;
            }
            long contentSize = conn.getContentLength();
            if(contentSize > 0){
                isDirExist();
                localFile = new RandomAccessFile (TEMP_DIR + "/(" + FileHelper.filterIDChars(downloadInfo.getTaskID()) + ")" + downloadInfo.getFileName(),"rwd");
                localFile.setLength(contentSize);
                downloadInfo.setFileSize(contentSize);
                fileSize = contentSize;
                if(isDownloading){
                    saveDownloadInfo();
                }
            }
            return code;
        }
    }


    /**
     * 文件夹是否存在，不存在则创建
     * @return
     */
    private boolean isDirExist(){
        boolean result = false;
        try{
            String filepath = TEMP_DIR;
            File file = new File(filepath);
            if(!file.exists()){
                if(file.mkdirs()){
                    result = true;
                }
            }else{
                result = true;
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 保存下载信息至数据库
     */
    private void saveDownloadInfo(){
        if(isSupportBreakpoint){
            downloadInfo.setDownloadSize(downloadedSize);
            dbAccessor.saveDownLoadInfo(downloadInfo);
        }
    }

    /**
     * 通知监听器，任务开始
     */
    private void startNotice(){
        if(dlListener!=null){
            dlListener.onStart(getSQLDownLoadInfo());
        }
    }

    /**
     * 通知监听器，当前进度
     */
    private void onProgressNotice(){
        if(dlListener!=null){
            dlListener.onProgress(getSQLDownLoadInfo(),isSupportBreakpoint);
        }
    }

    /**
     * 通知监听器，任务停止
     */
    private void stopNotice(){
        if(!isSupportBreakpoint){
            downloadedSize = 0;
        }
        if(dlListener!=null){
            dlListener.onStop(getSQLDownLoadInfo(),isSupportBreakpoint);
        }
    }

    /**
     * 通知监听器，任务异常，并停止
     */
    private void errorNotice(int errorCode){
        if(dlListener!=null){
            dlListener.onError(getSQLDownLoadInfo(),errorCode);
        }
    }

    /**
     * 通知监听器，任务完成
     */
    private void successNotice(){
        if(dlListener!=null){
            dlListener.onSuccess(getSQLDownLoadInfo());
        }
        if(downloadsuccess != null){
            downloadsuccess.onTaskSuccess(downloadInfo.getTaskID());
        }
    }

    Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            // TODO Auto-generated method stub
            if(msg.what == TASK_START){ //开始下载
                startNotice();
            }else if(msg.what == TASK_STOP){ //停止下载
                stopNotice();
            }else if(msg.what == TASK_PROGESS){ //改变进程
                onProgressNotice();
            }else if(msg.what == TASK_ERROR){ //下载出错
                errorNotice(msg.arg1);
            }else if(msg.what == TASK_SUCCESS){ //下载完成
                successNotice();
            }
        }
    };

    public boolean RenameFile(){
        File newfile = new File(downloadInfo.getFilePath());
        if(newfile.exists()){
            newfile.delete();
        }
        File olefile = new File(TEMP_DIR + "/(" + FileHelper.filterIDChars(downloadInfo.getTaskID()) + ")" + downloadInfo.getFileName());

        String filepath = downloadInfo.getFilePath();
        filepath = filepath.substring(0, filepath.lastIndexOf("/"));
        File file = new File(filepath);
        if(!file.exists()){
            file.mkdirs();
        }
        return olefile.renameTo(newfile);
    }
}
