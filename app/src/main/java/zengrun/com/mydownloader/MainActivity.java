package zengrun.com.mydownloader;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import zengrun.com.mydownloader.database.DownloadInfo;
import zengrun.com.mydownloader.database.FileHelper;
import zengrun.com.mydownloader.download.DLManager;
import zengrun.com.mydownloader.download.DLService;
import zengrun.com.mydownloader.download.DLWorker;
import zengrun.com.mydownloader.download.TaskInfo;

public class MainActivity extends AppCompatActivity {
    //private Button addButton;
    private ImageButton addButton;
    private TextView finished;
    private ListView listview;
    private EditText nameText;
    private EditText urlText;
    private ListAdapter adapter;
    private DLManager manager;

    private final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.activity_main);

        //下载管理器需要启动DLService,在刚启动应用的时候需要等Service启动起来后才能获取下载管理器，所以稍微延时获取下载管理器
        handler.sendEmptyMessageDelayed(1, 50);

        addButton = (ImageButton)this.findViewById(R.id.button);
        listview = (ListView)this.findViewById(R.id.listView);
        finished = (TextView)this.findViewById(R.id.finished);

        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                View showview = LayoutInflater.from(MainActivity.this).inflate(R.layout.dialog_layout, null);
                nameText = (EditText) showview.findViewById(R.id.file_name);
                urlText = (EditText) showview.findViewById(R.id.file_url);
                new AlertDialog.Builder(MainActivity.this).setView(showview).setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if ("".equals(nameText.getText().toString()) || "".equals(urlText.getText().toString())) {
                            Toast.makeText(MainActivity.this, "请输入文件名和下载路径", Toast.LENGTH_SHORT).show();
                        } else {
                            TaskInfo info = new TaskInfo();
                            info.setFileName(nameText.getText().toString());
                            /*服务器一般会有个区分不同文件的唯一ID，用以处理文件重名的情况*/
                            info.setTaskID(nameText.getText().toString());
                            info.setOnDownloading(true);
                            int state = getFileState(info.getTaskID(),info.getFileName(),null);
                            if(state==1){
                                //将任务添加到下载队列
                                manager.addTask(info.getTaskID(), urlText.getText().toString(), info.getFileName());
                                adapter.addItem(info);
                            }else if(state==0){
                                Toast.makeText(getApplicationContext(),"任务已在列表中",Toast.LENGTH_SHORT).show();
                            }else{
                                Toast.makeText(getApplicationContext(),"文件已存在",Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }).setNegativeButton("取消", null).show();

            }
        });

        //跳转到已完成任务列表
        finished.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setClass(MainActivity.this, FinishedActivity.class);
                startActivityForResult(intent,1);
            }
        });
    }



    Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            manager = DLService.getDLManager();
            manager.setSupportBreakpoint(true);
            adapter = new ListAdapter(MainActivity.this,manager,listview);
            listview.setAdapter(adapter);
        }
    };

    /**
     * 用户重新下载已完成任务，更新任务列表
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(resultCode==RESULT_OK){
            Log.v(TAG,"#####重新下载");
            String taskID = data.getStringExtra("taskID");
            String fileName = data.getStringExtra("fileName");
            TaskInfo task = new TaskInfo();
            task.setTaskID(taskID);
            task.setFileName(fileName);
            task.setOnDownloading(true);
            adapter.addItem(task);
        }
    }

    /**
     * 获取文件状态
     * @param TaskID 任务号
     * @param fileName 文件名
     * @param filepath 下载到本地的路径
     * @return  -1 : 文件已存在 ，0 ： 已存在任务列表 ， 1 ： 添加进任务列表
     */
    private int getFileState(String TaskID, String fileName, String filepath) {
        int taskSize = manager.workerList.size();
        for (int i = 0; i < taskSize; i++) {
            DLWorker downloader = manager.workerList.get(i);
            if (downloader.getTaskID().equals(TaskID)) {
                Log.w(TAG,"已存在任务列表");
                return 0;
            }
        }
        File file;
        if (filepath == null) {
            file = new File(FileHelper.getFileDefaultPath() + "/(" + FileHelper.filterIDChars(TaskID) + ")" + fileName);
            if (file.exists()) {
                Log.w(TAG,"文件已存在");
                return -1;
            }
        } else {
            file = new File(filepath);
            if (file.exists()) {
                Log.w(TAG,"文件已存在");
                return -1;
            }
        }
        return 1;
    }

}
