package zengrun.com.mydownloader;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.media.Image;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import java.io.File;
import java.util.List;

import zengrun.com.mydownloader.database.DBAccessor;
import zengrun.com.mydownloader.database.DownloadInfo;
import zengrun.com.mydownloader.database.FileHelper;
import zengrun.com.mydownloader.download.DLManager;


/**
 * 已完成任务列表适配器
 * Created by zengrun on 2017/8/22.
 */

public class FinishedAdapter extends BaseAdapter {
    private List<DownloadInfo> list;
    private Context mcontext;
    private DBAccessor dbAccessor;
    private DLManager dlManager;
    private RedownloadClickListener redownloadClickListener;

    private final String TEMP_DIR = FileHelper.getTempDirPath();
    private final String DIR = FileHelper.getFileDefaultPath();

    public FinishedAdapter(Context context,DLManager dlManager){
        this.mcontext = context;
        this.dlManager = dlManager;
        this.dbAccessor = new DBAccessor(context);
        this.list = dbAccessor.getAllFinished();
    }

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public Object getItem(int position) {
        return list.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        Holder holder;
        if(convertView == null){
            holder = new Holder();
            convertView = LayoutInflater.from(mcontext).inflate(R.layout.finished_item_layout, null);
            holder.fileName = (TextView)convertView.findViewById(R.id.finished_file_name);
            holder.fileSize = (TextView)convertView.findViewById(R.id.finished_file_size);
            holder.deleteButton = (ImageButton)convertView.findViewById(R.id.delFinishedButton);
            holder.restartButton = (ImageButton) convertView.findViewById(R.id.restartFinishedButton);
            convertView.setTag(holder);
        }else{
            holder = (Holder)convertView.getTag();
        }
        holder.fileName.setText(list.get(position).getFileName());
        holder.fileSize.setText(String.format("%.1f",list.get(position).getFileSize()/1024/1024.0)+"M");
        holder.deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(mcontext).setTitle("提示").setMessage("是否删除文件")
                        .setPositiveButton("是", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                delete(position,true);
                            }
                        })
                        .setNegativeButton("否", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                delete(position,false);
                            }
                        }).show();
            }
        });

        holder.restartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DownloadInfo downloadInfo = list.get(position);
                File downloadFile = new File(downloadInfo.getFilePath());
                if(downloadFile.exists()){
                    downloadFile.delete(); //删除本地文件
                }
                dbAccessor.deleteDownLoadInfo(downloadInfo.getTaskID());//删除数据库的记录
                dlManager.addTask(downloadInfo.getTaskID(),downloadInfo.getUrl(),downloadInfo.getFileName());
                //传数据给FinishedActivity
                if(redownloadClickListener!=null){
                    redownloadClickListener.OnButtonClick(downloadInfo);
                }

                list.remove(downloadInfo);
                FinishedAdapter.this.notifyDataSetChanged();
            }
        });

        return convertView;
    }

    private void delete(int position,boolean deleteFile){
        DownloadInfo downloadInfo = list.get(position);
        dbAccessor.deleteDownLoadInfo(downloadInfo.getTaskID());//删除数据库的记录
        if(deleteFile){
            File file = new File(downloadInfo.getFilePath());
            if (file.exists()) {
                file.delete();
            }
        }
        list.remove(position);
        FinishedAdapter.this.notifyDataSetChanged();
    }

    static class Holder {
        TextView fileName = null;
        TextView fileSize = null;
        ImageButton deleteButton = null;
        ImageButton restartButton = null;
    }

    //每完成一个任务需要调用此方法更新数据
    public void addItem(DownloadInfo downloadInfo){
        this.list.add(downloadInfo);
        this.notifyDataSetInvalidated();
    }

    public void setListdata(List<DownloadInfo> listdata){
        this.list = listdata;
        this.notifyDataSetInvalidated();
    }

    public void setRedownloadClickListener(RedownloadClickListener restartClickListener) {
        this.redownloadClickListener = restartClickListener;
    }

    public interface RedownloadClickListener {
        void OnButtonClick(DownloadInfo downloadInfo);
    }
}
