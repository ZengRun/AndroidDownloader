package zengrun.com.mydownloader.download;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import javax.net.ssl.HttpsURLConnection;

import zengrun.com.mydownloader.DownloadSuccess;
import zengrun.com.mydownloader.ErrorCode;
import zengrun.com.mydownloader.MessageType;
import zengrun.com.mydownloader.bean.DataInfo;
import zengrun.com.mydownloader.database.DBAccessor;
import zengrun.com.mydownloader.bean.DownloadInfo;
import zengrun.com.mydownloader.database.FileHelper;
import zengrun.com.mydownloader.security.SslUtils;

/**
 * Created by zengrun on 2017/8/24.
 */

public class DLWorker {

    //临时文件路径
    private final String TEMP_DIR = FileHelper.getTempDirPath();
    private final String TAG = "DLWorker";
    private final int maxDownloadTimes = 3;//失败重新请求次数
    private final int THREADS_PER_TASK = 3;//每个任务并发线程数

    private DBAccessor dbAccessor;
    private DownloadSuccess downloadsuccess;
    private DownloadInfo downloadInfo;
    private DownLoadThread[] downLoadThreads;
    private WriteThread writeThread;
    private long fileSize = 0;//文件大小
    private volatile long downloadedSize;//已经下载的大小
    private int downloadTimes = 0;//当前尝试请求的次数
    private boolean onDownload = false;//任务的状态
    private ThreadPoolExecutor pool;
    private RandomAccessFile tmpFile;
    private int progress = -1;
    private Handler handler;

    DLWorker(Context context, DownloadInfo downloadInfo, ThreadPoolExecutor pool){
        this.pool = pool;
        fileSize = downloadInfo.getFileSize();
        downloadedSize = downloadInfo.getDownloadSize();
        dbAccessor = new DBAccessor(context);
        this.downloadInfo = downloadInfo;
    }

    void start(){
        if(downLoadThreads==null||downLoadThreads.length==0){
            new BeforeDownloadThread().start();
        }
    }

    void stop(){
        if(downLoadThreads!=null){
            Log.v(TAG,"下载暂停，存储断点信息");
            onDownload = false;
            for(int i=0;i<THREADS_PER_TASK;i++){
                downLoadThreads[i].stopDownLoad();
            }
            while(!(downLoadThreads[0].isDead&&downLoadThreads[1].isDead&&downLoadThreads[2].isDead)){}
            saveDownloadInfo();
            handler.sendEmptyMessage(MessageType.TASK_STOP);
        }
    }

    void destroy(){
        if(downLoadThreads!=null){
            onDownload = false;
            for(int i=0;i<downLoadThreads.length;i++){
                downLoadThreads[i].stopDownLoad();
            }
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

    public void setTaskHandler(Handler handler){
        this.handler = handler;
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

    /**
     * 通知监听器，任务完成
     */
    private void successNotice(){
        if(downloadsuccess != null){
            downloadsuccess.onTaskSuccess(downloadInfo.getTaskID());
        }
    }

    public int RenameFile(){
        File olefile = new File(TEMP_DIR + "/(" + FileHelper.filterIDChars(downloadInfo.getTaskID()) + ")" + downloadInfo.getFileName());
        if(!olefile.exists()) return 0;

        File newfile = new File(downloadInfo.getFilePath());
        if(newfile.exists()){
            newfile.delete();
        }

        String filepath = downloadInfo.getFilePath();
        filepath = filepath.substring(0, filepath.lastIndexOf("/"));
        File file = new File(filepath);
        if(!file.exists()){
            file.mkdirs();
        }
        if(olefile.renameTo(newfile))
            return 1;
        else
            return -1;
    }



    /**
     * 下载线程
     */
    class DownLoadThread extends Thread{
        public volatile boolean isDead;
        private boolean isDownloading;
        private HttpURLConnection conn;
        private InputStream inputStream;
        //RandomAccessFile accessFile;
        private long start;//开始下载的位置
        private long end;//结束位置
        private long subTaskDownloadSize;//当前线程下载量
        private int index;

        public DownLoadThread(long start,long end,int index){
            this.start = start;
            this.end = end;
            this.index = index;
            isDownloading = true;
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

                    //accessFile = new RandomAccessFile (TEMP_DIR + "/(" + FileHelper.filterIDChars(downloadInfo.getTaskID()) + ")" + downloadInfo.getFileName(),"rwd");
                    //MappedByteBuffer mbb = accessFile.getChannel().map(FileChannel.MapMode.READ_WRITE,start,end-start+1);
                    conn.setRequestProperty("Range", "bytes=" + start + "-" + end);
                    inputStream = conn.getInputStream();
                    byte[] buffer = new byte[1024 * 4];
                    int length;
                    while((length = inputStream.read(buffer)) != -1 && isDownloading){
                        //mbb.put(buffer,0,length);
                        writeThread.add(new DataInfo(index,buffer,length));
                        subTaskDownloadSize +=length;  //更新当前线程下载量
//                        if(subTaskDownloadSize==(end-start+1))
//                            mbb.force();
                        synchronized (DLWorker.this){
                            downloadedSize += length; //更新文件全局下载量
                        }
                        int nowProgress = (int)((100 * downloadedSize)/fileSize);
                        if(nowProgress > progress){
                            progress = nowProgress;
                            Message msg = new Message();
                            msg.what = MessageType.TASK_PROGRESS;
                            msg.obj = getSQLDownLoadInfo();
                            handler.sendMessage(msg);
                        }
                    }
                    Log.v(TAG,"############"+subTaskDownloadSize+"-downloadsize:"+downloadedSize);
                    downloadTimes = maxDownloadTimes;
                } catch (Exception e) {
                    if(isDownloading){
                        downloadTimes++;
                        if(downloadTimes >= maxDownloadTimes){
                            Message msg = new Message();
                            msg.what= MessageType.TASK_ERROR;
                            msg.arg1 = ErrorCode.DOWNLOAD_ERROR;
                            msg.obj = getSQLDownLoadInfo();
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
                    }
                }
            }
            //Log.v(TAG,"#####线程退出！");
            this.isDead = true;
        }

        public void stopDownLoad(){
            isDownloading = false;
            downloadTimes = maxDownloadTimes;
        }

    }

    /**
     * 辅助线程：下载之前获取网络状态、文件分块、初始化下载线程等操作
     */
    class BeforeDownloadThread extends Thread{
        int statusCode = 0;
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
                    before();
                    return;
                }

                //先判断数据库是否已经有下载信息如果有，直接读取开始下载
                DownloadInfo di = dbAccessor.getDownLoadInfo(downloadInfo.getTaskID());
                if(di!=null){
                    Log.v(TAG,"#####继续任务");
                    downLoadThreads = new DownLoadThread[THREADS_PER_TASK];
                    downLoadThreads[0] = new DownLoadThread(di.getStart1(),di.getEnd1(),1);
                    downLoadThreads[1] = new DownLoadThread(di.getStart2(),di.getEnd2(),2);
                    downLoadThreads[2] = new DownLoadThread(di.getStart3(),di.getEnd3(),3);
                    downloadedSize = di.getDownloadSize();
                    fileSize = di.getFileSize();
                    writeThread = new WriteThread(di.getStart1(),di.getStart2(),di.getStart3(),
                            di.getEnd1(),di.getEnd2(),di.getEnd3());
                    before();
                    return;
                }

                //新任务，对文件分片
                int contentSize = urlConn.getContentLength();
                if(contentSize > 0){
                    isDirExist();
                    tmpFile = new RandomAccessFile (TEMP_DIR + "/(" + FileHelper.filterIDChars(downloadInfo.getTaskID()) + ")" + downloadInfo.getFileName(),"rwd");
                    tmpFile.setLength(contentSize);
                    downLoadThreads = new DownLoadThread[THREADS_PER_TASK];
                    long []s = new long[3];
                    long []e = new long[3];
                    //计算每个线程下载范围并创建下载线程
                    long blockSize = contentSize / THREADS_PER_TASK;
                    for(int i=0;i<THREADS_PER_TASK;i++){
                        long start = i*blockSize;
                        long end = (i+1)*blockSize-1;
                        if(i==THREADS_PER_TASK-1){
                            end=contentSize-1;
                        }
                        downLoadThreads[i] = new DownLoadThread(start,end,i+1);
                        s[i] = start;
                        e[i] = end;
                        Log.v(TAG,"#####线程"+i+": form "+start+" to "+end);
                    }
                    downloadInfo.setFileSize(contentSize);
                    fileSize = contentSize;
                    writeThread = new WriteThread(s[0],s[1],s[2],e[0],e[1],e[2]);
                }else{
                    statusCode = ErrorCode.FILE_SIZE_ZERO;
                }
                before();
            } catch (MalformedURLException e) {
                statusCode=ErrorCode.MALFORMED_URL;
                before();
                e.printStackTrace();
            } catch (IOException e) {
                statusCode=ErrorCode.FILE_ERROR;
                before();
                e.printStackTrace();
            } catch (Exception e) {
                statusCode=ErrorCode.OTHERS;
                before();
                e.printStackTrace();
            }
        }

        private void before(){
            if(statusCode==0){
                downloadTimes = 0;
                onDownload =true;
                Log.v(TAG,"file size:"+fileSize+" bytes");
                Log.v(TAG,"start downloading.....");
                handler.sendEmptyMessage(MessageType.TASK_START);
                writeThread.start();
                for(DownLoadThread thread:downLoadThreads){
                    pool.execute(thread);
                }
            }else{
                Message msg = new Message();
                msg.what = MessageType.TASK_ERROR;
                msg.arg1 = statusCode;
                msg.obj = getSQLDownLoadInfo();
                handler.sendMessage(msg);
            }
        }
    }


    class WriteThread extends Thread{
        long start1;
        long start2;
        long start3;
        long end1;
        long end2;
        long end3;

        RandomAccessFile accessFile1;
        RandomAccessFile accessFile2;
        RandomAccessFile accessFile3;
        MappedByteBuffer mbb1;
        MappedByteBuffer mbb2;
        MappedByteBuffer mbb3;

        LinkedBlockingQueue<DataInfo> queue;

        public WriteThread(long start1,long start2,long start3,long end1,long end2,long end3) {
            this.start1 = start1;
            this.start2 = start2;
            this.start3 = start3;
            this.end1 = end1;
            this.end2 = end2;
            this.end3 = end3;
            queue = new LinkedBlockingQueue<>();
            try {
                accessFile1 = new RandomAccessFile(TEMP_DIR + "/(" + FileHelper.filterIDChars(downloadInfo.getTaskID()) + ")" + downloadInfo.getFileName(), "rwd");
                accessFile2 = new RandomAccessFile(TEMP_DIR + "/(" + FileHelper.filterIDChars(downloadInfo.getTaskID()) + ")" + downloadInfo.getFileName(), "rwd");
                accessFile3 = new RandomAccessFile(TEMP_DIR + "/(" + FileHelper.filterIDChars(downloadInfo.getTaskID()) + ")" + downloadInfo.getFileName(), "rwd");
                mbb1 = accessFile1.getChannel().map(FileChannel.MapMode.READ_WRITE, start1, end1 - start1 + 1);
                mbb2 = accessFile1.getChannel().map(FileChannel.MapMode.READ_WRITE, start2, end2 - start2 + 1);
                mbb3 = accessFile1.getChannel().map(FileChannel.MapMode.READ_WRITE, start3, end3 - start3 + 1);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            while(true){
                if(!queue.isEmpty()){
                    DataInfo di = queue.poll();
                    byte[] buff = di.getBuff();
                    int index = di.getIndex();
                    int len = di.getLen();
                    if(index ==1){
                        mbb1.put(buff,0,len);
                    }else if(index==2){
                        mbb2.put(buff,0,len);
                    }else{
                        mbb3.put(buff,0,len);
                    }
                }

                if(queue.isEmpty()&&downLoadThreads[0].isDead&&downLoadThreads[1].isDead&&downLoadThreads[2].isDead){
                    //下载完成
                    if(downloadedSize == fileSize){
                        mbb1.force();
                        mbb2.force();
                        mbb3.force();
                        Log.v(TAG,"##################最后一个线程下载完成！！！");
                        int renameResult = RenameFile();
                        if(renameResult==1){        //转移文件成功
                            Message msg = new Message();
                            msg.what = MessageType.TASK_SUCCESS;
                            msg.obj = getSQLDownLoadInfo();
                            handler.sendMessage(msg);
                            successNotice();
                            Log.v(TAG,downloadInfo.getFileName()+"下载完成!!!!!");
                        }else if(renameResult==-1){  //转移文件失败
                            new File(TEMP_DIR + "/(" + FileHelper.filterIDChars(downloadInfo.getTaskID()) + ")" + downloadInfo.getFileName()).delete();
                            Message msg = new Message();
                            msg.what=MessageType.TASK_ERROR;
                            msg.arg1= ErrorCode.FILE_TRANS_ERROR;
                            msg.obj = getSQLDownLoadInfo();
                            handler.sendMessage(msg);
                        }
                        //保存下载完成的状态
                        saveDownloadInfo();
                        onDownload = false;
                    }
                    try {
                        accessFile1.close();
                        accessFile2.close();
                        accessFile3.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    for(int i=0;i<THREADS_PER_TASK;i++){
                        pool.remove(downLoadThreads[i]);
                        downLoadThreads[i]=null;
                    }
                    downLoadThreads=null;
                    return;
                }
            }
        }

        public void add(DataInfo di){
            queue.offer(di);
        }
    }

}