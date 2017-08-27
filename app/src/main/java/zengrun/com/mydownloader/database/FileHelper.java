package zengrun.com.mydownloader.database;

import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Created by zengrun on 2017/8/21.
 */

public class FileHelper {
    private static String baseFilePath = Environment.getExternalStorageDirectory().toString()+ "/MyDownLoader";
    private static String dowloadFilePath = baseFilePath  + "/files";
    /**下载文件的临时路径*/
    private static String tempDirPath = baseFilePath  + "/tempDir";



    private static String[] wrongChars = {
            "/", "\\*", "\\?", "<", ">", "\"", "|"};

    /**
     * 获取默认文件存放路径
     */
    public static String getFileDefaultPath() {
        return dowloadFilePath;
    }

    /**获取下载文件的临时路径*/
    public static String getTempDirPath() {
        return tempDirPath;
    }


    /**
     * 过滤附件ID中某些不能存在在文件名中的字符
     */
    public static String filterIDChars(String attID) {
        if (attID != null) {
            for (int i = 0; i < wrongChars.length; i++) {
                String c = wrongChars[i];
                if (attID.contains(c)) {
                    attID = attID.replaceAll(c, "");
                }
            }
        }
        return attID;
    }

}
