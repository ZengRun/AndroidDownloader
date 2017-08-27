package zengrun.com.mydownloader.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by zengrun on 2017/8/21.
 */

public class DBHelper extends SQLiteOpenHelper {
    private static final String mDatabaseName = "downloader";
    public static final String TABLE_NAME = "download_info";
    private static SQLiteDatabase.CursorFactory mFactory = null;
    private static final int mVersion = 1;

    public DBHelper(Context context) {
        super(context, mDatabaseName, mFactory, mVersion);
    }

    @Override
    public void onCreate(SQLiteDatabase sdb) {
        String sql = "CREATE TABLE IF NOT EXISTS "+ TABLE_NAME +" ("
                + "id INTEGER PRIMARY KEY  AUTOINCREMENT  NOT NULL , "
                + "taskID VARCHAR, "
                + "url VARCHAR, "
                + "filePath VARCHAR, "
                + "fileName VARCHAR, "
                + "fileSize VARCHAR, "
                + "downLoadSize VARCHAR "
                + ")";
        sdb.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sdb, int oldVersion, int newVersion) {
    }

    @Override
    public void onOpen(SQLiteDatabase sdb) {
        super.onOpen(sdb);
    }
}
