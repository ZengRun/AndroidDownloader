package zengrun.com.mydownloader;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.ListView;

import zengrun.com.mydownloader.database.DownloadInfo;
import zengrun.com.mydownloader.download.DLManager;

/**
 * 已完成任务列表
 * Created by zengrun on 2017/8/22.
 */

public class FinishedActivity extends AppCompatActivity {
    private final String TAG = "FinishedActivity";
    private ListView finishedListView;
    private FinishedAdapter adapter;
    private DLManager manager;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.activity_finished);

        finishedListView = (ListView)findViewById(R.id.finishedListView);
        manager = DLManager.getInstance(FinishedActivity.this);
        adapter = new FinishedAdapter(FinishedActivity.this,manager);
        adapter.setRedownloadClickListener(new FinishedAdapter.RedownloadClickListener() {
            @Override
            public void OnButtonClick(DownloadInfo downloadInfo) {
                Intent intent = new Intent();
                intent.putExtra("taskID",downloadInfo.getTaskID());
                intent.putExtra("fileName",downloadInfo.getFileName());
                setResult(RESULT_OK,intent);
                finish();
            }
        });
        finishedListView.setAdapter(adapter);
    }
}
