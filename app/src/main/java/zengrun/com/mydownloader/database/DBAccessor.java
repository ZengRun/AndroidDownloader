package zengrun.com.mydownloader.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by zengrun on 2017/8/21.
 */

public class DBAccessor {
    private DBHelper dbhelper;
    private SQLiteDatabase db;
    private int doSaveTimes = 0;
    public DBAccessor(Context context){
        this.dbhelper = new DBHelper(context);
    }
    /**
     * 保存下载任务到数据库
     * @param downloadInfo
     */
    public void saveDownLoadInfo(DownloadInfo downloadInfo){
        ContentValues cv = new ContentValues();
        cv.put("taskID", downloadInfo.getTaskID());
        cv.put("downLoadSize", downloadInfo.getDownloadSize());
        cv.put("fileName", downloadInfo.getFileName());
        cv.put("filePath", downloadInfo.getFilePath());
        cv.put("fileSize", downloadInfo.getFileSize());
        cv.put("url", downloadInfo.getUrl());
        Cursor cursor = null;
        try{
            db = dbhelper.getWritableDatabase();
            cursor = db.rawQuery(
                    "SELECT * from " + DBHelper.TABLE_NAME
                            + " WHERE taskID = ? ", new String[]{downloadInfo.getTaskID()});
            if(cursor.moveToNext()){
                db.update(DBHelper.TABLE_NAME, cv, "taskID = ? ", new String[]{downloadInfo.getTaskID()});
            }else{
                db.insert(DBHelper.TABLE_NAME, null, cv);
            }
            cursor.close();
            db.close();
        }catch(Exception e){
            doSaveTimes ++;
            if(doSaveTimes < 5){ //最多尝试5次
                saveDownLoadInfo(downloadInfo);
            }else{
                doSaveTimes = 0;
            }
            if(cursor != null){
                cursor.close();
            }
            if(db != null){
                db.close();
            }
        }
        doSaveTimes = 0;
    }

    public DownloadInfo getDownLoadInfo(String taskID){
        DownloadInfo downloadinfo= null;
        db = dbhelper.getWritableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT * from " + DBHelper.TABLE_NAME
                        + "WHERE taskID = ? ", new String[]{taskID});
        if(cursor.moveToNext()){
            downloadinfo = new DownloadInfo();
            downloadinfo.setDownloadSize(cursor.getLong(cursor.getColumnIndex("downLoadSize")));
            downloadinfo.setFileName(cursor.getString(cursor.getColumnIndex("fileName")));
            downloadinfo.setFilePath(cursor.getString(cursor.getColumnIndex("filePath")));
            downloadinfo.setFileSize(cursor.getLong(cursor.getColumnIndex("fileSize")));
            downloadinfo.setUrl(cursor.getString(cursor.getColumnIndex("url")));
            downloadinfo.setTaskID(cursor.getString(cursor.getColumnIndex("taskID")));
        }
        cursor.close();
        db.close();
        return downloadinfo;
    }

    public List<DownloadInfo> getAllDownLoadInfo(){
        List<DownloadInfo> list = new ArrayList<>();
        db = dbhelper.getWritableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT * from " + DBHelper.TABLE_NAME + " WHERE downLoadSize<fileSize or fileSize=0", null);
        while(cursor.moveToNext()){
            DownloadInfo downloadinfo = new DownloadInfo();
            downloadinfo.setDownloadSize(cursor.getLong(cursor.getColumnIndex("downLoadSize")));
            downloadinfo.setFileName(cursor.getString(cursor.getColumnIndex("fileName")));
            downloadinfo.setFilePath(cursor.getString(cursor.getColumnIndex("filePath")));
            downloadinfo.setFileSize(cursor.getLong(cursor.getColumnIndex("fileSize")));
            downloadinfo.setUrl(cursor.getString(cursor.getColumnIndex("url")));
            downloadinfo.setTaskID(cursor.getString(cursor.getColumnIndex("taskID")));
            list.add(downloadinfo);
        }
        cursor.close();
        db.close();
        return list;

    }

    public void deleteDownLoadInfo(String taskID){
        db = dbhelper.getWritableDatabase();
        db.delete(DBHelper.TABLE_NAME, "taskID = ? ", new String[]{taskID});
        db.close();
    }

    public void deleteAllDownLoadInfo(){
        db = dbhelper.getWritableDatabase();
        db.delete(DBHelper.TABLE_NAME, null, null);
        db.close();
    }

    /**
     * 获取所有已完成的任务
     * @return
     */
    public List<DownloadInfo> getAllFinished(){
        List<DownloadInfo> list = new ArrayList<>();
        db = dbhelper.getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT * from " + DBHelper.TABLE_NAME +
                                    " WHERE downLoadSize=fileSize and fileSize>0", null);
        while(cursor.moveToNext()){
            DownloadInfo downloadinfo = new DownloadInfo();
            downloadinfo.setDownloadSize(cursor.getLong(cursor.getColumnIndex("downLoadSize")));
            downloadinfo.setFileName(cursor.getString(cursor.getColumnIndex("fileName")));
            downloadinfo.setFilePath(cursor.getString(cursor.getColumnIndex("filePath")));
            downloadinfo.setFileSize(cursor.getLong(cursor.getColumnIndex("fileSize")));
            downloadinfo.setUrl(cursor.getString(cursor.getColumnIndex("url")));
            downloadinfo.setTaskID(cursor.getString(cursor.getColumnIndex("taskID")));
            list.add(downloadinfo);
        }
        cursor.close();
        db.close();
        return list;
    }
}
