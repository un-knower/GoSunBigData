package com.hzgc.cluster.spark.spark.device;

import com.hzgc.common.hbase.HBaseHelper;
import com.hzgc.common.table.device.DeviceTable;
import com.hzgc.common.util.empty.IsEmpty;
import com.hzgc.common.util.object.ObjectUtil;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

public class DeviceUtilImpl implements Serializable {

    public String getplatfromID(String ipcID) {
        Table table = null;
        if (IsEmpty.strIsRight(ipcID)) {
            try {
                table = HBaseHelper.getTable(DeviceTable.TABLE_DEVICE);
                Get get = new Get(Bytes.toBytes(ipcID));
                Result result = table.get(get);
                if (result.containsColumn(DeviceTable.CF_DEVICE, DeviceTable.PLAT_ID)) {
                    return new String(result.getValue(DeviceTable.CF_DEVICE, DeviceTable.PLAT_ID));
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                HBaseHelper.closeTable(table);
            }
        }
        return "";
    }

    public Map<Integer, Map<String, Integer>> isWarnTypeBinding(String ipcID) {
        Table table = null;
        if (IsEmpty.strIsRight(ipcID)) {
            try {
                table = HBaseHelper.getTable(DeviceTable.TABLE_DEVICE);
                Get get = new Get(Bytes.toBytes(ipcID));
                Result result = table.get(get);
                if (result.containsColumn(DeviceTable.CF_DEVICE, DeviceTable.WARN)) {
                    byte[] map = result.getValue(DeviceTable.CF_DEVICE, DeviceTable.WARN);
                    if (map != null) {
                        return (Map<Integer, Map<String, Integer>>) ObjectUtil.byteToObject(map);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                HBaseHelper.closeTable(table);
            }
        }
        return null;
    }

    public Map<String, Map<String, Integer>> getThreshold() {
        try {
            Table table = HBaseHelper.getTable(DeviceTable.TABLE_DEVICE);
            Get get = new Get(DeviceTable.OFFLINERK);
            Result result = table.get(get);
            if (result.containsColumn(DeviceTable.CF_DEVICE, DeviceTable.OFFLINECOL)) {
                byte[] map = result.getValue(DeviceTable.CF_DEVICE, DeviceTable.OFFLINECOL);
                if (map != null) {
                    return (Map<String, Map<String, Integer>>)ObjectUtil.byteToObject(map);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
