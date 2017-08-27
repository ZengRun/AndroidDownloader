package zengrun.com.mydownloader;

import android.app.Application;
import android.content.Intent;

import zengrun.com.mydownloader.download.DLService;

/**
 * Created by zengrun on 2017/8/21.
 */

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        this.startService(new Intent(this, DLService.class));
    }

}
