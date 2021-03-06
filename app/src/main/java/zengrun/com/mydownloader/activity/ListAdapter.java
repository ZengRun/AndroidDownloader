package zengrun.com.mydownloader.activity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.res.ResourcesCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import zengrun.com.mydownloader.ErrorCode;
import zengrun.com.mydownloader.MessageType;
import zengrun.com.mydownloader.R;
import zengrun.com.mydownloader.bean.DownloadInfo;
import zengrun.com.mydownloader.download.DLManager;
import zengrun.com.mydownloader.thread.DeleteThread;
import zengrun.com.mydownloader.thread.StartThread;
import zengrun.com.mydownloader.thread.StopThread;
import zengrun.com.mydownloader.bean.TaskInfo;

/**
 * 未完成任务列表适配器
 * Created by zengrun on 2017/8/21.
 */

public class ListAdapter extends BaseAdapter {
    private final String TAG = "ListView";

    private List<TaskInfo> listData = new  ArrayList<TaskInfo>();
    private Context mContext;
    private DLManager dlManager;
    private ListView listView;
    private Timer timer;

    public ListAdapter(Context context,DLManager dlManager,ListView listView){
        this.mContext = context;
        this.dlManager = dlManager;
        this.timer = new Timer();
        listData = dlManager.getAllTask();
        this.listView = listView;
        dlManager.setAllTaskHandler(handler);

        TimerTask progressTask = new TimerTask() {
            @Override
            public void run() {
                refreshHandler.sendEmptyMessage(0);
            }
        };
        timer.scheduleAtFixedRate(progressTask, 0, 1000);
    }

    @Override
    public int getCount() {
        return listData.size();
    }

    @Override
    public Object getItem(int position) {
        return listData.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        Holder holder;
        if(convertView == null){
            holder = new Holder();
            convertView = LayoutInflater.from(mContext).inflate(R.layout.list_item_layout, null);
            holder.fileName = (TextView)convertView.findViewById(R.id.file_name);
            holder.textProgress = (TextView)convertView.findViewById(R.id.file_size);
            holder.fileProgress = (ProgressBar)convertView.findViewById(R.id.progressbar);
            holder.downloadIcon = (ImageButton)convertView.findViewById(R.id.change);
            holder.deleteButton = (ImageButton)convertView.findViewById(R.id.delButton);
            convertView.setTag(holder);
        }else{
            holder = (Holder)convertView.getTag();
        }
        holder.fileName.setText(listData.get(position).getFileName());
        holder.fileProgress.setProgress(listData.get(position).getProgress());
        holder.textProgress.setText(listData.get(position).getProgress() + "%");
        holder.downloadIcon.setOnClickListener(new ImageButtonClickListener(position));
        if(listData.get(position).isOnDownloading()){
            holder.downloadIcon.setImageDrawable(ResourcesCompat.getDrawable(mContext.getResources(), R.mipmap.pause, null));
        }else{
            holder.downloadIcon.setImageDrawable(ResourcesCompat.getDrawable(mContext.getResources(), R.mipmap.start, null));
        }

        holder.deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(mContext).setTitle("提示").setCancelable(false).setMessage("确定删除下载任务？")
                        .setPositiveButton("是", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                TaskInfo task = listData.get(position);
                                //dlManager.deleteTask(task.getTaskID());
                                new DeleteThread(dlManager,task.getTaskID()).start();
                                listData.remove(position);
                                ListAdapter.this.notifyDataSetChanged();
                            }
                        })
                        .setNegativeButton("否", null).show();
            }

        });
        return convertView;
    }

    static class Holder {
        TextView fileName = null;
        TextView textProgress = null;
        ProgressBar fileProgress = null;
        ImageButton downloadIcon = null;
        ImageButton deleteButton = null;
    }

    public void addItem(TaskInfo taskinfo){
        this.listData.add(taskinfo);
        this.notifyDataSetInvalidated();
    }

//    private void updateItem(int position,TaskInfo taskInfo){
//        int visibleFirstPosi = listView.getFirstVisiblePosition();
//        int visibleLastPosi = listView.getLastVisiblePosition();
//        if (position >= visibleFirstPosi && position <= visibleLastPosi) {
//            View view = listView.getChildAt(position-visibleFirstPosi);
//            Holder viewHolder = (Holder)view.getTag();
//            viewHolder.textProgress.setText(taskInfo.getProgress() + "%");
//            viewHolder.fileProgress.setProgress(taskInfo.getProgress());
//        }
//    }

    private void updateItems(){
        for(int i=0;i<listData.size();i++){
            TaskInfo taskInfo = listData.get(i);
            View view = listView.getChildAt(i);
            Holder viewHolder = (Holder)view.getTag();
            viewHolder.textProgress.setText(taskInfo.getProgress() + "%");
            viewHolder.fileProgress.setProgress(taskInfo.getProgress());
        }
    }



    class ImageButtonClickListener implements View.OnClickListener{
        int position;
        static final int MIN_CLICK_DELAY_TIME = 1000;
        long lastClickTime = 0;
        public ImageButtonClickListener(int position){
            this.position = position;
        }
        @Override
        public void onClick(View v) {
            long currentTime = Calendar.getInstance().getTimeInMillis();

            if(currentTime-lastClickTime<MIN_CLICK_DELAY_TIME) {
                //lastClickTime = currentTime;
                return;
            }
            if(listData.get(position).isOnDownloading()){
                Log.v(TAG,"用户点击-暂停下载");
                listData.get(position).setOnDownloading(false);
                ((ImageButton)v).setImageDrawable(ResourcesCompat.getDrawable(mContext.getResources(), R.mipmap.start, null));
                new StopThread(dlManager,listData.get(position).getTaskID()).start();
            }else{
                Log.v(TAG,"用户点击-继续下载");
                listData.get(position).setOnDownloading(true);
                ((ImageButton)v).setImageDrawable(ResourcesCompat.getDrawable(mContext.getResources(), R.mipmap.pause, null));
                new StartThread(dlManager,listData.get(position).getTaskID()).start();
            }
            lastClickTime = currentTime;
        }
    }


    public Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            // TODO Auto-generated method stub
            if(msg.what == MessageType.TASK_START){ //开始下载
                start((DownloadInfo) msg.obj);
            }else if(msg.what == MessageType.TASK_STOP){ //停止下载
                stop((DownloadInfo) msg.obj);
            }else if(msg.what == MessageType.TASK_PROGRESS){ //改变进程
                progress((DownloadInfo)msg.obj);
            }else if(msg.what == MessageType.TASK_ERROR){ //下载出错
                error((DownloadInfo)msg.obj,msg.arg1);
            }else if(msg.what == MessageType.TASK_SUCCESS){ //下载完成
                success((DownloadInfo)msg.obj);
            }
        }
    };

    private Handler refreshHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // TODO Auto-generated method stub
            updateItems();
        }
    };


    private void progress(DownloadInfo downloadInfo){
        //根据监听到的信息查找列表相对应的任务，更新相应任务的进度
        for(int i = 0;i<listData.size();i++){
            TaskInfo taskInfo = listData.get(i);
            if(taskInfo.getTaskID().equals(downloadInfo.getTaskID())){
                taskInfo.setDownFileSize(downloadInfo.getDownloadSize());
                taskInfo.setFileSize(downloadInfo.getFileSize());
                //updateItem(i,taskInfo);
                break;
            }
        }
    }

    private void success(DownloadInfo downloadInfo){
        //根据监听到的信息查找列表相对应的任务，删除对应的任务
        for(TaskInfo taskInfo : listData){
            if(taskInfo.getTaskID().equals(downloadInfo.getTaskID())){
                listData.remove(taskInfo);
                ListAdapter.this.notifyDataSetChanged();
                Toast.makeText(mContext,taskInfo.getFileName()+"下载完成！！",Toast.LENGTH_LONG).show();
                break;
            }
        }
    }

    private void error(DownloadInfo downloadInfo,int errorCode){
        //根据监听到的信息查找列表相对应的任务，停止该任务
        for(TaskInfo taskInfo : listData){
            if(taskInfo.getTaskID().equals(downloadInfo.getTaskID())){
                taskInfo.setOnDownloading(false);
                if(errorCode==1||errorCode==2){
                    Toast.makeText(mContext,taskInfo.getFileName()+"下载失败,请删除任务重试",Toast.LENGTH_LONG).show();
                }else if(errorCode>=400){
                    Toast.makeText(mContext,taskInfo.getFileName()+"网络错误："+errorCode,Toast.LENGTH_LONG).show();
                }else if(errorCode== ErrorCode.FILE_SIZE_ZERO){
                    Toast.makeText(mContext,taskInfo.getFileName()+"获取文件信息异常",Toast.LENGTH_LONG).show();
                }else if(errorCode==ErrorCode.MALFORMED_URL){
                    Toast.makeText(mContext,"错误的URL连接",Toast.LENGTH_LONG).show();
                }else if(errorCode==ErrorCode.FILE_ERROR){
                    Toast.makeText(mContext,"创建文件异常，请检查文件名并打开应用SD卡读写权限",Toast.LENGTH_LONG).show();
                }else{
                    Toast.makeText(mContext,taskInfo.getFileName()+"未知错误，请删除任务重试",Toast.LENGTH_LONG).show();
                }

                if(downloadInfo.getDownloadSize()==0){//还没开始下载，删除条目
                    listData.remove(taskInfo);
                    new DeleteThread(dlManager,taskInfo.getTaskID()).start();//并删除下载任务
                }else{
                    //dlManager.stopTask(taskInfo.getTaskID());//如果是下载到一半出错则暂停任务并保存断点
                    new StopThread(dlManager,taskInfo.getTaskID()).start();
                }
                ListAdapter.this.notifyDataSetChanged();
                break;
            }
        }
    }

    private void start(DownloadInfo downloadInfo){}

    private void stop(DownloadInfo downloadInfo){}
}

