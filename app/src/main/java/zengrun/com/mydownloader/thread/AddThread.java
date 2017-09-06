package zengrun.com.mydownloader.thread;

import zengrun.com.mydownloader.download.DLManager;

/**
 * Created by ZR on 2017/9/6.
 */

public class AddThread extends Thread {
    private DLManager manager;
    private String taskID;
    private String url;
    private String fileName;

    public AddThread(DLManager manager,String taskID,String url,String fileName){
        this.manager = manager;
        this.taskID = taskID;
        this.url = url;
        this.fileName = fileName;
    }

    @Override
    public void run() {
        manager.addTask(taskID,url,fileName);
    }
}
