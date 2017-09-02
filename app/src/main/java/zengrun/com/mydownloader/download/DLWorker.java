package zengrun.com.mydownloader.download;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.ThreadPoolExecutor;

import javax.net.ssl.HttpsURLConnection;

import zengrun.com.mydownloader.ErrorCode;
import zengrun.com.mydownloader.database.DBAccessor;
import zengrun.com.mydownloader.database.DownloadInfo;
import zengrun.com.mydownloader.database.FileHelper;
import zengrun.com.mydownloader.security.SslUtils;

/**
 * Created by zengrun on 2017/8/24.
 */

public class DLWorker {
    private int TASK_START = 0;
    private int TASK_STOP = 1;
    private int TASK_PROGESS = 2;
    private int TASK_ERROR = 3;
    private int TASK_SUCCESS = 4;

    //临时文件路径
    private final String TEMP_DIR = FileHelper.getTempDirPath();
    private final String TAG = "DLWorker";
    private final int maxDownloadTimes = 3;//失败重新请求次数
    private final int THREADS_PER_TASK = 3;//每个任务并发线程数

    private boolean isSupportBreakpoint = false;
    private DBAccessor dbAccessor;
    private DLListener dlListener;
    private DownloadSuccess downloadsuccess;
    private DownloadInfo downloadInfo;
    private DownLoadThread[] downLoadThreads;
    private long fileSize = 0;//文件大小
    private long downloadedSize;//已经下载的大小
    private int downloadTimes = 0;//当前尝试请求的次数
    private boolean onDownload = false;//任务的状态
    private ThreadPoolExecutor pool;
    private RandomAccessFile tmpFile;
    private int progress = -1;
    private int statusCode = 0;

    public DLWorker(Context context, DownloadInfo downloadInfo, ThreadPoolExecutor pool, boolean isSupportBreakpoint, boolean isNewTask){
        this.isSupportBreakpoint = isSupportBreakpoint;
        this.pool = pool;
        fileSize = downloadInfo.getFileSize();
        downloadedSize = downloadInfo.getDownloadSize();
        dbAccessor = new DBAccessor(context);
        this.downloadInfo = downloadInfo;
    }

    public void start(){
        if(downLoadThreads==null||downLoadThreads.length==0){
            BeforeDownloadThread beforeDownloadThread = new BeforeDownloadThread();
            beforeDownloadThread.start();
            try {
                beforeDownloadThread.join();      //让主线程等待
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if(statusCode==0){
                downloadTimes = 0;
                onDownload =true;
                Log.v(TAG,"file size:"+fileSize+" bytes");
                Log.v(TAG,"start downloading.....");
                handler.sendEmptyMessage(TASK_START);
                for(DownLoadThread thread:downLoadThreads){
                    pool.execute(thread);
                }
            }else{
                Message msg = new Message();
                msg.what = TASK_ERROR;
                msg.arg1 = statusCode;
                handler.sendMessage(msg);
            }
        }
    }

    public void stop(){
        if(downLoadThreads!=null){
            Log.v(TAG,"下载暂停，存储断点信息");
            onDownload = false;
//            for(int i=0;i<downLoadThreads.length;i++){
//                downLoadThreads[i].stopDownLoad();
//                pool.remove(downLoadThreads[i]);
//            }
//            saveDownloadInfo();
//            for(int i=0;i<downLoadThreads.length;i++){
//                downLoadThreads[i]=null;
//            }

            for(int i=0;i<THREADS_PER_TASK;i++){
                downLoadThreads[i].stopDownLoad();
            }
            while(!(downLoadThreads[0].isDead&&downLoadThreads[1].isDead&&downLoadThreads[2].isDead)){}
            saveDownloadInfo();
            for(int i=0;i<THREADS_PER_TASK;i++){
                pool.remove(downLoadThreads[i]);
                downLoadThreads[i]=null;
            }

            downLoadThreads=null;
            handler.sendEmptyMessage(TASK_STOP);
        }
    }

    public void destroy(){
        if(downLoadThreads!=null){
            onDownload = false;
            for(int i=0;i<downLoadThreads.length;i++){
                downLoadThreads[i].stopDownLoad();
                pool.remove(downLoadThreads[i]);
                downLoadThreads[i]=null;
            }
            downLoadThreads=null;
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
        return onDownload;
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
            downloadInfo.setStart1(downLoadThreads[0].start+downLoadThreads[0].subTaskDownloadSize);
            downloadInfo.setStart2(downLoadThreads[1].start+downLoadThreads[1].subTaskDownloadSize);
            downloadInfo.setStart3(downLoadThreads[2].start+downLoadThreads[2].subTaskDownloadSize);
            downloadInfo.setDownloadSize(downloadedSize);
            downloadInfo.setEnd1(downLoadThreads[0].end);
            downloadInfo.setEnd2(downLoadThreads[1].end);
            downloadInfo.setEnd3(downLoadThreads[2].end);
            Log.v(TAG,"#####save info:"+downLoadThreads[0].subTaskDownloadSize+"-"+downLoadThreads[1].subTaskDownloadSize+"-"
                    +downLoadThreads[2].subTaskDownloadSize+" :"+downloadedSize);
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
            downloadedSize = 0L;
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



    /**
     * 下载线程
     */
    class DownLoadThread extends Thread{
        public volatile boolean isDead;
        private boolean isdownloading;
        private HttpURLConnection conn;
        private InputStream inputStream;
        RandomAccessFile accessFile;
        private long start;//开始下载的位置
        private long end;//结束位置
        private long subTaskDownloadSize;//当前线程下载量

        public DownLoadThread(long start,long end){
            this.start = start;
            this.end = end;
            isdownloading = true;
            this.isDead = false;
        }

        @Override
        public void run() {
            while(downloadTimes < maxDownloadTimes){ //做3次请求的尝试
                try {
                    if(start>end) {
                        Log.v(TAG,"#####该线程任务已完成，直接退出");
                        isDead = true;
                        return;
                    }
                    URL url = new URL(downloadInfo.getUrl());
                    if(downloadInfo.getUrl().startsWith("https")){
                        SslUtils.ignoreSsl();
                        conn = (HttpsURLConnection)url.openConnection();
                    }else{
                        conn = (HttpURLConnection)url.openConnection();
                    }
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(10000);

                    accessFile = new RandomAccessFile (TEMP_DIR + "/(" + FileHelper.filterIDChars(downloadInfo.getTaskID()) + ")" + downloadInfo.getFileName(),"rwd");
                    MappedByteBuffer mbb = accessFile.getChannel().map(FileChannel.MapMode.READ_WRITE,start,end-start+1);
                    //accessFile.seek(start);
                    conn.setRequestProperty("Range", "bytes=" + start + "-" + end);
                    inputStream = conn.getInputStream();
                    byte[] buffer = new byte[1024 * 4];
                    int length;
                    while((length = inputStream.read(buffer)) != -1 && isdownloading){
                        //accessFile.write(buffer, 0, length);
                        mbb.put(buffer,0,length);
                        subTaskDownloadSize +=length;  //更新当前线程下载量
                        synchronized (DLWorker.this){
                            downloadedSize += length; //更新文件全局下载量
                        }
                        int nowProgress = (int)((100 * downloadedSize)/fileSize);
                        if(nowProgress > progress){
                            progress = nowProgress;
                            handler.sendEmptyMessage(TASK_PROGESS);
                        }
                    }
                    mbb.force();
                    Log.v(TAG,"############"+subTaskDownloadSize+"-downloadsize:"+downloadedSize);
                    //下载完
                    if(downloadedSize == fileSize){ //最后一个完成的线程去转移文件和存数据库。
                        Log.v(TAG,"##################最后一个线程下载完成！！！");
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
                        downLoadThreads = null;
                        onDownload = false;
                        Log.v(TAG,downloadInfo.getFileName()+"下载完成!!!!!");
                    }
                    downloadTimes = maxDownloadTimes;
                } catch (Exception e) {
                    if(isdownloading){
                        if(isSupportBreakpoint){
                            downloadTimes++;
                            if(downloadTimes >= maxDownloadTimes){
                                //for(int i =0;i<THREADS_PER_TASK;i++){
                                //    downLoadThreads[i].stopDownLoad();
                                //    pool.remove(downLoadThreads[i]);
                                //}
                                //if(downloadedSize>0)
                                //    saveDownloadInfo();
                                //downLoadThreads = null;
                                //onDownload = false;
                                Message msg = new Message();
                                msg.what= TASK_ERROR;
                                msg.arg1 = ErrorCode.DOWNLOAD_ERROR;
                                handler.sendMessage(msg);
                            }
                        }else{
                            downloadedSize = 0L;
                            downloadTimes = maxDownloadTimes;
                            onDownload = false;
                            downLoadThreads = null;
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
                        if(accessFile != null){
                            accessFile.close();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            //Log.v(TAG,"#####线程退出！");
            this.isDead = true;
        }

        public void stopDownLoad(){
            isdownloading = false;
            downloadTimes = maxDownloadTimes;
        }

    }

    /**
     * 辅助线程：下载之前获取网络状态、文件分块、初始化下载线程等操作
     */
    class BeforeDownloadThread extends Thread{
        @Override
        public void run() {
            try {
                URL url = new URL(downloadInfo.getUrl());
                HttpURLConnection urlConn;
                if(downloadInfo.getUrl().startsWith("https")){
                    SslUtils.ignoreSsl();
                    urlConn = (HttpsURLConnection)url.openConnection();
                }else{
                    urlConn = (HttpURLConnection)url.openConnection();
                }
                urlConn.setConnectTimeout(5000);
                urlConn.setReadTimeout(10000);
                int code = urlConn.getResponseCode();
                while(code==HttpURLConnection.HTTP_MOVED_TEMP){
                    Log.v(TAG,"302:redirect");
                    String redirect = urlConn.getHeaderField("Location");
                    Log.v(TAG,"######redirect to: "+redirect);
                    url = new URL(redirect);
                    downloadInfo.setUrl(redirect);
                    if(downloadInfo.getUrl().startsWith("https")){
                        SslUtils.ignoreSsl();
                        urlConn = (HttpsURLConnection)url.openConnection();
                    }else{
                        urlConn = (HttpURLConnection)url.openConnection();
                    }
                    urlConn.setConnectTimeout(5000);
                    urlConn.setReadTimeout(10000);
                    code = urlConn.getResponseCode();
                }
                if(code>=400){
                    Log.e(TAG,"#######request error happened:"+code);
                    statusCode = code;
                    return;
                }
                //先判断数据库是否已经有下载信息如果有，直接读取开始下载
                DownloadInfo di = dbAccessor.getDownLoadInfo(downloadInfo.getTaskID());
                if(di!=null){
                    Log.v(TAG,"#####继续任务");
                    downLoadThreads = new DownLoadThread[THREADS_PER_TASK];
                    downLoadThreads[0] = new DownLoadThread(di.getStart1(),di.getEnd1());
                    downLoadThreads[1] = new DownLoadThread(di.getStart2(),di.getEnd2());
                    downLoadThreads[2] = new DownLoadThread(di.getStart3(),di.getEnd3());
                    downloadedSize = di.getDownloadSize();
                    return;
                }

                //新任务，对文件分片
                int contentSize = urlConn.getContentLength();
                if(contentSize > 0){
                    isDirExist();
                    tmpFile = new RandomAccessFile (TEMP_DIR + "/(" + FileHelper.filterIDChars(downloadInfo.getTaskID()) + ")" + downloadInfo.getFileName(),"rwd");
                    tmpFile.setLength(contentSize);
                    downLoadThreads = new DownLoadThread[THREADS_PER_TASK];
                    //计算每个线程下载范围并创建下载线程
                    long blockSize = contentSize / THREADS_PER_TASK;
                    for(int i=0;i<THREADS_PER_TASK;i++){
                        long start = i*blockSize;
                        long end = (i+1)*blockSize-1;
                        if(i==THREADS_PER_TASK-1){
                            end=contentSize-1;
                        }
                        downLoadThreads[i] = new DownLoadThread(start,end);
                        Log.v(TAG,"#####线程"+i+": form "+start+" to "+end);
                    }
                    downloadInfo.setFileSize(contentSize);
                    fileSize = contentSize;
                }else{
                    statusCode = ErrorCode.FILE_SIZE_ZERO;
                    return;
                }
            } catch (MalformedURLException e) {
                statusCode=ErrorCode.MALFORMED_URL;
                e.printStackTrace();
            } catch (IOException e) {
                statusCode=ErrorCode.FILE_ERROR;
                e.printStackTrace();
            } catch (Exception e) {
                statusCode=ErrorCode.OTHERS;
                e.printStackTrace();
            }
        }
    }


}