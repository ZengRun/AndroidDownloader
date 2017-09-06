package zengrun.com.mydownloader.thread;

import zengrun.com.mydownloader.download.DLManager;

/**
 * Created by ZR on 2017/9/3.
 */

public class StartThread extends Thread {
    private DLManager manager;
    private String taskID;
    public StartThread(DLManager manager,String taskID){
        this.manager=manager;
        this.taskID=taskID;
    }

    @Override
    public void run() {
        manager.startTask(taskID);
    }
}
