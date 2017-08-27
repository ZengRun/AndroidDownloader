package zengrun.com.mydownloader;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import zengrun.com.mydownloader.database.DownloadInfo;
import zengrun.com.mydownloader.download.DLListener;
import zengrun.com.mydownloader.download.DLManager;
import zengrun.com.mydownloader.download.TaskInfo;

/**
 * 未完成任务列表适配器
 * Created by zengrun on 2017/8/21.
 */

public class ListAdapter extends BaseAdapter {
    private final String TAG = "ListView";

    private List<TaskInfo> listData = new  ArrayList<TaskInfo>();
    private Context mcontext;
    private DLManager dlManager;
    private ListView listView;

    public ListAdapter(Context context,DLManager dlManager,ListView listView){
        this.mcontext = context;
        this.dlManager = dlManager;
        listData = dlManager.getAllTask();
        this.listView = listView;
        dlManager.setAllTaskListener(new DownloadManagerListener());
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
            convertView = LayoutInflater.from(mcontext).inflate(R.layout.list_item_layout, null);
            holder.fileName = (TextView)convertView.findViewById(R.id.file_name);
            holder.textProgress = (TextView)convertView.findViewById(R.id.file_size);
            holder.fileProgress = (ProgressBar)convertView.findViewById(R.id.progressbar);
            holder.downloadIcon = (CheckBox)convertView.findViewById(R.id.checkbox);
            holder.deleteButton = (ImageButton)convertView.findViewById(R.id.delButton);
            convertView.setTag(holder);
        }else{
            holder = (Holder)convertView.getTag();
        }
        holder.fileName.setText(listData.get(position).getFileName());
        holder.fileProgress.setProgress(listData.get(position).getProgress());
        holder.textProgress.setText(listData.get(position).getProgress() + "%");
        holder.downloadIcon.setOnCheckedChangeListener(new CheckedChangeListener(position));
        if(listData.get(position).isOnDownloading()){
            holder.downloadIcon.setChecked(true);
        }else{
            holder.downloadIcon.setChecked(false);
        }

        holder.deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(mcontext).setTitle("提示").setCancelable(false).setMessage("确定删除下载任务？")
                            .setPositiveButton("是", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    TaskInfo task = listData.get(position);
                                    dlManager.deleteTask(task.getTaskID());
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
        CheckBox downloadIcon = null;
        ImageButton deleteButton = null;
    }

    public void addItem(TaskInfo taskinfo){
        this.listData.add(taskinfo);
        this.notifyDataSetInvalidated();
    }

    private void updateItem(int position,TaskInfo taskInfo){
        int visibleFirstPosi = listView.getFirstVisiblePosition();
        int visibleLastPosi = listView.getLastVisiblePosition();
        if (position >= visibleFirstPosi && position <= visibleLastPosi) {
            View view = listView.getChildAt(position-visibleFirstPosi);
            Holder viewHolder = (Holder)view.getTag();
            viewHolder.textProgress.setText(taskInfo.getProgress() + "%");
            viewHolder.fileProgress.setProgress(taskInfo.getProgress());
        }
    }



    class CheckedChangeListener implements CompoundButton.OnCheckedChangeListener {
        int position;
        public CheckedChangeListener(int position){
            this.position = position;
        }
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if(isChecked){
                // 继续下载
                Log.v(TAG,"用户点击-继续下载");
                listData.get(position).setOnDownloading(true);
                dlManager.startTask(listData.get(position).getTaskID());
            }else{
                //停止下载
                Log.v(TAG,"用户点击-暂停下载");
                listData.get(position).setOnDownloading(false);
                dlManager.stopTask(listData.get(position).getTaskID());
            }
            ListAdapter.this.notifyDataSetChanged();
        }
    }


    private class DownloadManagerListener implements DLListener {
        @Override
        public void onStart(DownloadInfo downloadInfo) {}

        @Override
        public void onProgress(DownloadInfo downloadInfo, boolean isSupportBreakpoint) {
            //根据监听到的信息查找列表相对应的任务，更新相应任务的进度
            for(int i = 0;i<listData.size();i++){
                TaskInfo taskInfo = listData.get(i);
                if(taskInfo.getTaskID().equals(downloadInfo.getTaskID())){
                    taskInfo.setDownFileSize(downloadInfo.getDownloadSize());
                    taskInfo.setFileSize(downloadInfo.getFileSize());
                    updateItem(i,taskInfo);
                    break;
                }
            }

        }

        @Override
        public void onStop(DownloadInfo downloadInfo, boolean isSupportBreakpoint) {}

        @Override
        public void onSuccess(DownloadInfo downloadInfo) {
            //根据监听到的信息查找列表相对应的任务，删除对应的任务
            for(TaskInfo taskInfo : listData){
                if(taskInfo.getTaskID().equals(downloadInfo.getTaskID())){
                    listData.remove(taskInfo);
                    ListAdapter.this.notifyDataSetChanged();
                    Toast.makeText(mcontext,taskInfo.getFileName()+"下载完成！！",Toast.LENGTH_LONG).show();
                    break;
                }
            }
        }

        @Override
        public void onError(DownloadInfo downloadInfo,int errorCode) {
            //根据监听到的信息查找列表相对应的任务，停止该任务
            for(TaskInfo taskInfo : listData){
                if(taskInfo.getTaskID().equals(downloadInfo.getTaskID())){
                    taskInfo.setOnDownloading(false);
                    ListAdapter.this.notifyDataSetChanged();
                    if(errorCode==1||errorCode==2){
                        Toast.makeText(mcontext,taskInfo.getFileName()+"下载失败",Toast.LENGTH_LONG).show();
                    }else if(errorCode>=400){
                        Toast.makeText(mcontext,taskInfo.getFileName()+"网络错误："+errorCode,Toast.LENGTH_LONG).show();
                    }

                    break;
                }
            }
        }
    }
}

