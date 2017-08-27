package zengrun.com.mydownloader.download;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Created by zengrun on 2017/8/21.
 */

public class DLService extends Service {
    private static DLManager DLManager;

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public void onCreate() {
        super.onCreate();
        DLManager = new DLManager(DLService.this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //释放downLoadManager
        DLManager.stopAllTask();
        DLManager = null;
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        if(DLManager == null){
            DLManager = new DLManager(DLService.this);
        }
    }

    public static DLManager getDLManager(){
        return DLManager;
    }
}
