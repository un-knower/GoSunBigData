package com.hzgc.service.device.dao;

import com.hzgc.common.hbase.HBaseHelper;
import com.hzgc.common.table.device.DeviceTable;
import com.hzgc.common.util.empty.IsEmpty;
import com.hzgc.common.util.json.JSONUtil;
import com.hzgc.common.util.object.ObjectUtil;
import com.hzgc.service.device.bean.WarnRule;
import com.hzgc.service.device.util.JsonToMap;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.*;

@Repository
public class HBaseDao {

    private static Logger LOG = Logger.getLogger(HBaseDao.class);

    public HBaseDao() {
        HBaseHelper.getHBaseConnection();
    }

    public boolean bindDevice(String platformId, String ipcID, String notes) {
        Table table = HBaseHelper.getTable(DeviceTable.TABLE_DEVICE);
        if (IsEmpty.strIsRight(ipcID) && IsEmpty.strIsRight(platformId)) {
            String ipcIDTrim = ipcID.trim();
            try {
                Put put = new Put(Bytes.toBytes(ipcIDTrim));
                put.addColumn(DeviceTable.CF_DEVICE, DeviceTable.PLAT_ID, Bytes.toBytes(platformId));
                put.addColumn(DeviceTable.CF_DEVICE, DeviceTable.NOTES, Bytes.toBytes(notes));
                table.put(put);
                LOG.info("Put data[" + ipcIDTrim + ", " + platformId + "] successful");
                return true;
            } catch (Exception e) {
                LOG.error("Current bind is failed!");
                return false;
            } finally {
                HBaseHelper.closeTable(table);
            }
        } else {
            LOG.error("Please check the arguments!");
            return false;
        }
    }

    public boolean unbindDevice(String platformId, String ipcID) {
        Table table = HBaseHelper.getTable(DeviceTable.TABLE_DEVICE);
        if (IsEmpty.strIsRight(platformId) && IsEmpty.strIsRight(ipcID)) {
            String ipcIDTrim = ipcID.trim();
            try {
                Delete delete = new Delete(Bytes.toBytes(ipcIDTrim));
                //根据设备ID（rowkey），删除该行的平台ID列，而非删除这整行数据。
                delete.addColumns(DeviceTable.CF_DEVICE, DeviceTable.PLAT_ID);
                table.delete(delete);
                LOG.info("Unbind device:" + ipcIDTrim + " and " + platformId + " successful");
                return true;
            } catch (Exception e) {
                LOG.error("Current unbind is failed!");
                return false;
            } finally {
                HBaseHelper.closeTable(table);
            }
        } else {
            LOG.error("Please check the arguments!");
            return false;
        }
    }

    public boolean renameNotes(String notes, String ipcID) {
        Table table = HBaseHelper.getTable(DeviceTable.TABLE_DEVICE);
        if (IsEmpty.strIsRight(ipcID)) {
            String ipcIDTrim = ipcID.trim();
            try {
                Put put = new Put(Bytes.toBytes(ipcIDTrim));
                put.addColumn(DeviceTable.CF_DEVICE, DeviceTable.NOTES, Bytes.toBytes(notes));
                table.put(put);
                LOG.info("Rename " + ipcIDTrim + "'s notes successful!");
                return true;
            } catch (Exception e) {
                LOG.error("Current renameNotes is failed!");
                return false;
            }
        } else {
            LOG.error("Please check the arguments!");
            return false;
        }
    }

    public Map <String, Boolean> configRules(List <String> ipcIDs, List <WarnRule> rules) {
        //从Hbase读device表
        Table deviceTable = HBaseHelper.getTable(DeviceTable.TABLE_DEVICE);
        //初始化设备布控预案对象Map<告警类型，Map<对象类型，阈值>>
        Map <Integer, Map <String, Integer>> commonRule = new HashMap <>();
        //初始化离线告警对象Map<对象类型，Map<设备ID，离线天数>>
        Map <String, Map <String, Integer>> offlineMap = new HashMap <>();
        List <Put> putList = new ArrayList <>();
        //reply表示：是否添加成功
        Map <String, Boolean> reply = new HashMap <>();

        // 把传进来的rules：List<WarnRule> rules转化为commonRule：Map<Integer, Map<String, Integer>>格式
        String jsonString = parseDeviceRule(rules, ipcIDs, commonRule);
        byte[] commonRuleBytes = ObjectUtil.objectToByte(jsonString);
        for (String ipcID : ipcIDs) {
            //“以传入的设备ID为行键，device为列族，告警类型w为列，commonRuleBytes为值”，的Put对象添加到putList列表
            Put put = new Put(Bytes.toBytes(ipcID));
            put.addColumn(DeviceTable.CF_DEVICE, DeviceTable.WARN, commonRuleBytes);
            putList.add(put);
            //对于每一条规则
            for (WarnRule rule : rules) {
                //解析离线告警：在离线告警offlineMap中添加相应的对象类型、ipcID和规则中的离线天数阈值DayThreshold
                parseOfflineWarn(rule, ipcID, offlineMap);
            }
            reply.put(ipcID, true);
        }
        try {
            //把putList列表添加到表device表中
            deviceTable.put(putList);
            LOG.info("configRules are " + jsonString);
            //config模式下，把离线告警offlineMap对象插入到device表中
            configOfflineWarn(offlineMap, deviceTable);
        } catch (IOException e) {
            LOG.error(e.getMessage());
        } finally {
            HBaseHelper.closeTable(deviceTable);
        }
        return reply;
    }

    public Map <String, Boolean> addRules(List <String> ipcIDs, List <WarnRule> rules) {
        Table deviceTable = HBaseHelper.getTable(DeviceTable.TABLE_DEVICE);
        Map <Integer, Map <String, Integer>> commonRule = new HashMap <>();
        Map <String, Map <String, Integer>> offlineMap = new HashMap <>();
        List <Put> putList = new ArrayList <>();
        Map <String, Boolean> reply = new HashMap <>();

        //把传进来的rules：List<WarnRule> rules转化为commonRule：Map<Integer, Map<String, Integer>>格式
        String commonRuleString = parseDeviceRule(rules, ipcIDs, commonRule);
        byte[] commonRuleBytes = ObjectUtil.objectToByte(commonRuleString);
        for (String ipcID : ipcIDs) {
            Put put = new Put(Bytes.toBytes(ipcID));
            put.addColumn(DeviceTable.CF_DEVICE, DeviceTable.WARN, commonRuleBytes);
            putList.add(put);
            for (WarnRule rule : rules) {
                //解析离线告警：在离线告警offlineMap中添加相应的对象类型、ipcID和规则中的离线天数阈值DayThreshold
                parseOfflineWarn(rule, ipcID, offlineMap);
            }
            reply.put(ipcID, true);
        }
        //try部分与config模式下不同
        try {
            for (String ipcID : ipcIDs) {
                Get get = new Get(Bytes.toBytes(ipcID));
                Result result = deviceTable.get(get);
                //若device表中的值不为空
                if (!result.isEmpty()) {
                    byte[] bytes = result.getValue(DeviceTable.CF_DEVICE, DeviceTable.WARN);
                    String tempMapStr = null;
                    if (bytes != null) {
                        //告警类型      对象类型,阈值
                        tempMapStr = Bytes.toString(bytes);
                    }
                    //json字符串转化成对象
                    Map <Integer, Map <String, Integer>> tempMap = JsonToMap.stringToMap(tempMapStr);
                    //对于每一种告警类型：
                    for (Integer code : commonRule.keySet()) {
                        //若device表中存在这种告警类型
                        if (tempMap.containsKey(code)) {
                            //对于布控规则commonRule中每一个告警类型的对象类型
                            for (String type : commonRule.get(code).keySet()) {
                                //把commonRule中的每一个对象类型添加到device表
                                tempMap.get(code).put(type, commonRule.get(code).get(type));
                            }
                        } else {//若device表中不存在这种告警类型，直接添加
                            tempMap.put(code, commonRule.get(code));
                        }
                    }
                    Put put = new Put(Bytes.toBytes(ipcID));
                    String tempMapString = JSONUtil.toJson(tempMap);
                    put.addColumn(DeviceTable.CF_DEVICE, DeviceTable.WARN, Bytes.toBytes(tempMapString));
                    putList.add(put);
                    LOG.info("addRules are " + tempMapString);
                } else {
                    //若device表中的值为空，直接添加
                    Put put = new Put(Bytes.toBytes(ipcID));
                    put.addColumn(DeviceTable.CF_DEVICE, DeviceTable.WARN, commonRuleBytes);
                    putList.add(put);
                }
            }
            deviceTable.put(putList);
            configOfflineWarn(offlineMap, deviceTable);
            LOG.info("addRules are " + commonRuleString);
        } catch (IOException e) {
            LOG.error(e.getMessage());
        } finally {
            HBaseHelper.closeTable(deviceTable);
        }
        return reply;
    }

    public List <WarnRule> getCompareRules(String ipcID) {
        List <WarnRule> reply = new ArrayList <>();
        Table table = HBaseHelper.getTable(DeviceTable.TABLE_DEVICE);
        if (IsEmpty.strIsRight(ipcID)) {
            try {
                Get get = new Get(Bytes.toBytes(ipcID));
                Result result = table.get(get);
                //若device表中存在告警w列
                if (result.containsColumn(DeviceTable.CF_DEVICE, DeviceTable.WARN)) {
                    //获取告警列中的值
                    byte[] deviceByte = result.getValue(DeviceTable.CF_DEVICE, DeviceTable.WARN);
                    //并转化为Map嵌套格式（反序列化）
                    String deviceMapStr = null;
                    if (deviceByte != null) {
                        deviceMapStr = Bytes.toString(deviceByte);
                    }
                    Map <Integer, Map <String, Integer>> map = new HashMap <>();
                    Map <Integer, Map <String, Integer>> deviceMap = JsonToMap.stringToMap(deviceMapStr);
                    /*
                     * 设备布控预案数据类型Map<Integer, Map<String, Integer>>    （即deviceMap）
                     *                                     告警类型,      对象类型,阈值
                     */
                    //对于deviceMap中的每个告警类型
                    for (Integer code : deviceMap.keySet()) {
                        //获取tempMap：<String, Integer>
                        //                     对象类型，阈值
                        Map <String, Integer> tempMap = deviceMap.get(code);
                        //对于tempMap中的每个对象类型
                        for (String type : deviceMap.get(code).keySet()) {
                            WarnRule warnRule = new WarnRule();
                            warnRule.setCode(code);
                            //把规则中的告警类型code和对象类型type放到warnRule中，用于返回
                            warnRule.setObjectType(type);
                            //对于告警类型中是离线告警的
                            if (code == DeviceTable.OFFLINE) {
                                //获取离线告警天数阈值
                                warnRule.setDayThreshold(tempMap.get(type));
                            } else {
                                //对于告警类型中不是离线告警的，获取sim阈值
                                warnRule.setThreshold(tempMap.get(type));
                            }
                            reply.add(warnRule);
                        }
                    }
                    return reply;
                }
            } catch (IOException e) {
                LOG.error(e.getMessage());
            } finally {
                HBaseHelper.closeTable(table);
            }
        }
        return reply;
    }

    public Map <String, Boolean> deleteRules(List <String> ipcIDs) {
        //获取device表
        Table deviceTable = HBaseHelper.getTable(DeviceTable.TABLE_DEVICE);
        Map <String, Boolean> reply = new HashMap <>();
        List <Delete> deviceDelList = new ArrayList <>();
        String id = "";
        //若传入的设备ID不为空
        if (ipcIDs != null) {
            try {
                //对于每一个设备ID，删除其在device表中对应的列族、列
                for (String ipc : ipcIDs) {
                    id = ipc;
                    Delete deviceDelete = new Delete(Bytes.toBytes(ipc));
                    //列族：device，列：w
                    deviceDelete.addColumns(DeviceTable.CF_DEVICE, DeviceTable.WARN);
                    deviceDelList.add(deviceDelete);
                    //reply：（设备ID，删除成功）
                    reply.put(ipc, true);
                    LOG.info("release the rule binding, the device ID is:" + ipc);
                }
                //获取离线告警数据的行键
                Get offlineGet = new Get(DeviceTable.OFFLINERK);
                Result offlineResult = deviceTable.get(offlineGet);
                //若离线告警数据非空
                if (!offlineResult.isEmpty()) {
                    //将离线告警数据反序列化
                    byte[] bytes = offlineResult.getValue(DeviceTable.CF_DEVICE, DeviceTable.OFFLINECOL);
                    String offlineStr = null;
                    if (bytes != null) {
                        offlineStr = Bytes.toString(bytes);
                    }
                    Map <String, Map <String, Integer>> offlineMap = JsonToMap.stringToMap(offlineStr);
                    /*
                     * offlineMap：Map<String, Map<String, Integer>>
                     *                      对象类型      设备ID,离线天数
                     */
                    //对于离线告警数据中的每个对象类型
                    for (String type : offlineMap.keySet()) {
                        //对于每个设备ID
                        for (String ipc : ipcIDs) {
                            //删除设备ID对应的键值内容
                            offlineMap.get(type).remove(ipc);
                        }
                    }
                    //把删除后的离线告警数据存入device表中
                    Put offlinePut = new Put(DeviceTable.OFFLINERK);
                    String offline = JSONUtil.toJson(offlineMap);
                    offlinePut.addColumn(DeviceTable.CF_DEVICE, DeviceTable.OFFLINECOL, Bytes.toBytes(offline));
                    deviceTable.put(offlinePut);
                    LOG.info("delete rule is " + offline);
                }
                deviceTable.delete(deviceDelList);
                return reply;
            } catch (IOException e) {
                reply.put(id, false);
                LOG.error(e.getMessage());
            } finally {
                HBaseHelper.closeTable(deviceTable);
            }
        }
        return reply;
    }

    public List <String> objectTypeHasRule(String objectType) {
        return null;
    }

    public int deleteObjectTypeOfRules(String objectType, List <String> ipcIDs) {
        return 0;
    }

    /**
     * config模式下配置离线告警（内部方法）（config模式下，把离线告警offlineMap对象插入到device表中）
     * 离线告警数据类型offlineMap：Map<String, Map<String, Integer>>
     * 对象类型,   设备ID, 离线天数
     * tempMap：device表中的值
     */
    private void configOfflineWarn(Map <String, Map <String, Integer>> offlineMap, Table deviceTable) {
        try {
            String offlineString = null;
            Get offlinGet = new Get(DeviceTable.OFFLINERK);
            //获取device表中offlineWarnRowKey行键对应的数据
            Result offlineResult = deviceTable.get(offlinGet);
            //若device表中offlineWarnRowKey行键对应的数据offlineResult非空（value中有值）
            if (!offlineResult.isEmpty()) {
                //反序列化该值类型（转化为Object）
                byte[] bytes = offlineResult.getValue(DeviceTable.CF_DEVICE, DeviceTable.OFFLINECOL);
                String tempMapStr = null;
                if (bytes != null) {
                    tempMapStr = Bytes.toString(bytes);
                }
                Map <String, Map <String, Integer>> tempMap = JsonToMap.stringToMap(tempMapStr);
                //对于离线告警offlineMap中的每个对象类型
                for (String type : offlineMap.keySet()) {
                    //假如Hbase数据库中的device表中包含离线告警offlineMap的对象类型
                    if (tempMap.containsKey(type)) {
                        //对于离线告警offlineMap中的每一个设备ID
                        for (String ipc : offlineMap.get(type).keySet()) {
                            //覆盖device表中原有的值。offlineMap.get(type).get(ipc)：离线天数
                            tempMap.get(type).put(ipc, offlineMap.get(type).get(ipc));
                        }
                    } else {
                        /*
                         * 假如Hbase数据库中的device表中不包含离线告警offlineMap的对象类型，
                         * 直接向device表中添加离线告警offlineMap中的值
                         */
                        tempMap.put(type, offlineMap.get(type));
                    }
                }
                Put offlinePut = new Put(DeviceTable.OFFLINERK);
                offlineString = JSONUtil.toJson(offlineMap);
                offlinePut.addColumn(DeviceTable.CF_DEVICE, DeviceTable.OFFLINECOL, Bytes.toBytes(offlineString));
                deviceTable.put(offlinePut);
            } else {
                //若hbase的device表中offlineWarnRowKey行键对应的数据为空，直接把offlineMap的值加入到device表
                Put offlinePut = new Put(DeviceTable.OFFLINERK);
                offlineString = JSONUtil.toJson(offlineMap);
                offlinePut.addColumn(DeviceTable.CF_DEVICE, DeviceTable.OFFLINECOL, Bytes.toBytes(offlineString));
                deviceTable.put(offlinePut);
            }
            LOG.info("configRules are " + offlineString);
        } catch (IOException e) {
            LOG.error(e.getMessage());
        }
    }


    /**
     * 解析configRules()传入的布控规则，并在解析的同时同步其他相关数据（内部方法）
     * 把传进来的rules：List<WarnRule> rules转化为commonRule：Map<Integer, Map<String, Integer>>格式
     */

    private String parseDeviceRule(List <WarnRule> rules,
                                   List <String> ipcIDs,
                                   Map <Integer, Map <String, Integer>> commonRule) {
        //判断：规则不为空，设备ID不为空
        if (rules != null && commonRule != null && ipcIDs != null) {
            for (WarnRule rule : rules) {
                /*
                 * code：告警类型。0：识别告警；1：新增告警；2：离线告警
                 * rules：传入的规则，List<WarnRule>格式；
                 * commonRule：设备布控预案，需转化成的Map<Integer, Map<String, Integer>>格式
                 */

                //IDENTIFY：识别告警；ADDED：新增告警。若传入的规则rules中的告警类型为这两者
                if (Objects.equals(rule.getCode(), DeviceTable.IDENTIFY) || Objects.equals(rule.getCode(), DeviceTable.ADDED)) {
                    //判断commonRule的键（告警类型）是否是传入的规则rule中的告警类型。
                    if (commonRule.containsKey(rule.getCode())) {
                        //如果之前存在对比规则，覆盖之前的规则（相当于清除后写入）
                        commonRule.get(rule.getCode()).put(rule.getObjectType(), rule.getThreshold());
                    } else {
                        //如果之前不存在对比规则，直接写入
                        Map <String, Integer> temMap = new HashMap <>();
                        //getThreshold()：识别或新增告警需要的相似度阈值
                        temMap.put(rule.getObjectType(), rule.getThreshold());
                        commonRule.put(rule.getCode(), temMap);
                    }
                }

                //OFFLINE：离线告警。若传入的规则rules中的告警类型为离线告警
                if (Objects.equals(rule.getCode(), DeviceTable.OFFLINE)) {
                    //如果之前存在对比规则，先清除之前的规则，再重新写入
                    if (commonRule.containsKey(rule.getCode())) {
                        commonRule.get(rule.getCode()).put(rule.getObjectType(), rule.getDayThreshold());
                    } else {
                        //如果之前不存在对比规则，直接写入
                        Map <String, Integer> tempMap = new HashMap <>();
                        //getDayThreshold()：离线告警需要的离线天数
                        tempMap.put(rule.getObjectType(), rule.getDayThreshold());
                        commonRule.put(rule.getCode(), tempMap);
                    }
                }
            }
        }
        return JSONUtil.toJson(commonRule);
    }

    /**
     * 解析离线告警（内部方法）
     * 离线告警数据类型Map<String, Map<String, Integer>>
     * 对象类型,   设备ID, 离线天数
     */
    private void parseOfflineWarn(WarnRule rule,
                                  String ipcID,
                                  Map <String, Map <String, Integer>> offlineMap) {
        //离线告警中存在传入规则中的对象类型
        if (offlineMap.containsKey(rule.getObjectType())) {
            //在离线告警相应的对象类型中添加ipcID和规则中的离线天数阈值DayThreshold
            offlineMap.get(rule.getObjectType()).put(ipcID, rule.getDayThreshold());
        } else {
            //若离线告警中不存在传入规则中的对象类型
            Map <String, Integer> ipcMap = new HashMap <>();
            ipcMap.put(ipcID, rule.getDayThreshold());
            offlineMap.put(rule.getObjectType(), ipcMap);
        }
    }

    /**
     * objectType数据类型：Map<Integer, String>（内部方法）
     * 反序列化此数据类型
     */
    private Map <String, Map <Integer, String>> deSerializObjToDevice(byte[] bytes) {
        if (bytes != null) {
            return (Map <String, Map <Integer, String>>) ObjectUtil.byteToObject(bytes);
        }
        return null;
    }

    /**
     * 设备布控预案数据类型：Map<Integer, Map<String, Integer>>（内部方法）
     * 反序列化此数据类型
     */
    private String deSerializDevice(byte[] bytes) {
        if (bytes != null) {
            return (String) ObjectUtil.byteToObject(bytes);
        }
        return null;
    }

    /**
     * 离线告警数据类型：Map<String, Map<String, Integer>>（内部方法）
     * 反序列化此数据类型
     */
    private String deSerializOffLine(byte[] bytes) {
        if (bytes != null) {
            return (String) ObjectUtil.byteToObject(bytes);
        }
        return null;
    }
}
