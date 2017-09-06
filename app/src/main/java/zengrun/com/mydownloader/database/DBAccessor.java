package zengrun.com.mydownloader.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import zengrun.com.mydownloader.bean.DownloadInfo;

/**
 * Created by ZR on 2017/8/27.
 */

public class DBAccessor {
    private DBHelper dbhelper;
    private SQLiteDatabase db;
    private int doSaveTimes = 0;
    public DBAccessor(Context context){
        this.dbhelper = new DBHelper(context);
    }
    /**
     * 淇濆瓨涓嬭浇浠诲姟鍒版暟鎹簱
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
        cv.put("start1",downloadInfo.getStart1());
        cv.put("start2",downloadInfo.getStart2());
        cv.put("start3",downloadInfo.getStart3());
        cv.put("end1",downloadInfo.getEnd1());
        cv.put("end2",downloadInfo.getEnd2());
        cv.put("end3",downloadInfo.getEnd3());
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
            if(doSaveTimes < 5){ //鏈€澶氬皾璇?娆?
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
                        + " WHERE taskID = ? ", new String[]{taskID});
        if(cursor.moveToNext()){
            downloadinfo = new DownloadInfo();
            downloadinfo.setDownloadSize(cursor.getLong(cursor.getColumnIndex("downLoadSize")));
            downloadinfo.setFileName(cursor.getString(cursor.getColumnIndex("fileName")));
            downloadinfo.setFilePath(cursor.getString(cursor.getColumnIndex("filePath")));
            downloadinfo.setFileSize(cursor.getLong(cursor.getColumnIndex("fileSize")));
            downloadinfo.setUrl(cursor.getString(cursor.getColumnIndex("url")));
            downloadinfo.setTaskID(cursor.getString(cursor.getColumnIndex("taskID")));
            downloadinfo.setStart1(cursor.getLong(cursor.getColumnIndex("start1")));
            downloadinfo.setStart2(cursor.getLong(cursor.getColumnIndex("start2")));
            downloadinfo.setStart3(cursor.getLong(cursor.getColumnIndex("start3")));
            downloadinfo.setEnd1(cursor.getLong(cursor.getColumnIndex("end1")));
            downloadinfo.setEnd2(cursor.getLong(cursor.getColumnIndex("end2")));
            downloadinfo.setEnd3(cursor.getLong(cursor.getColumnIndex("end3")));
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
            downloadinfo.setStart1(cursor.getLong(cursor.getColumnIndex("start1")));
            downloadinfo.setStart2(cursor.getLong(cursor.getColumnIndex("start2")));
            downloadinfo.setStart3(cursor.getLong(cursor.getColumnIndex("start3")));
            downloadinfo.setEnd1(cursor.getLong(cursor.getColumnIndex("end1")));
            downloadinfo.setEnd2(cursor.getLong(cursor.getColumnIndex("end2")));
            downloadinfo.setEnd3(cursor.getLong(cursor.getColumnIndex("end3")));
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
     * 鑾峰彇鎵€鏈夊凡瀹屾垚鐨勪换鍔?
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
