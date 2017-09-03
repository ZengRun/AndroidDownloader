package zengrun.com.mydownloader.download;

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
