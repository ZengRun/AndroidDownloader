package zengrun.com.mydownloader.bean;

import java.util.Arrays;

/**
 * Created by ZR on 2017/9/8.
 */

public class DataInfo {
    int index;
    byte[] buff;
    int len;

    public DataInfo(int index,byte[] buffer,int len){
        this.index = index;
        this.buff = Arrays.copyOf(buffer,len);
        this.len = len;
    }

    public int getIndex() {
        return index;
    }

    public byte[] getBuff() {
        return buff;
    }

    public int getLen() {
        return len;
    }
}
