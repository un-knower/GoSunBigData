package com.hzgc.service.starepo.service;

import com.hzgc.common.table.starepo.ObjectInfoTable;
import com.hzgc.service.starepo.bean.PrisonCountResult;
import com.hzgc.service.starepo.bean.PrisonCountResults;
import com.hzgc.service.starepo.bean.PrisonSearchOpts;
import com.hzgc.service.starepo.util.PhoenixJDBCHelper;
import lombok.extern.slf4j.Slf4j;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class PrisonService {

    /**
     * 用来更新人员位置，
     * @param prisonSearchOpts，对应的是pkeysUpdate 参数
     * @return 0,表示更新成功，1，表示失败
     */
    public int updateLocation(PrisonSearchOpts prisonSearchOpts){
        if (prisonSearchOpts == null) {
            log.info("PrsionSearchOpts 为空，传过来的参数不能为空。");
            return 1;
        }
        Map<String, List<String>> pkeysUpdate = prisonSearchOpts.getPkeysUpate();
        if (pkeysUpdate == null) {
            log.info("更新参数为空.");
            return 1;
        }
        java.sql.Connection conn;
        PreparedStatement pstm = null;
        String sql = "upsert into " + ObjectInfoTable.TABLE_NAME + "("
                + ObjectInfoTable.ROWKEY + ", "
                +  ObjectInfoTable.LOCATION + ")" + "values(?, ?)";
        try {
            conn = PhoenixJDBCHelper.getInstance().getConnection();
            conn.setAutoCommit(false);
            for (Map.Entry<String, List<String>> entry : pkeysUpdate.entrySet()) {
                String location = entry.getKey();
                List<String> ids = entry.getValue();
                pstm = conn.prepareStatement(sql);
                for (int i = 0;i < ids.size(); i++) {
                    String id = ids.get(i);
                    pstm.setString(1, id);
                    if (location != null && "".equals(location)) {
                        pstm.setString(2, null);
                    } else {
                        pstm.setString(2, location);
                    }
                    pstm.addBatch();
                    if (i % 200 == 0){
                        pstm.executeBatch();
                        conn.commit();
                    }
                }
                pstm.executeBatch();
                conn.commit();
            }
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            log.info("Sql 插入异常......");
            e.printStackTrace();
            return 1;
        } finally {
           PhoenixJDBCHelper.closeConnection(null, pstm);
        }
        log.info(sql);
        return 0;
    }

    /**
     * 用来重置人员位置
     * @param prisonSearchOpts 对应的是pkeysReset
     * @return 0,表示重置成功，1，表示失败
     */
    public int resetLocation(PrisonSearchOpts prisonSearchOpts) {
        if (prisonSearchOpts == null) {
            log.info("PrsionSearchOpts 为空，传过来的参数不能为空......");
            return 1;
        }
        List<String> pkeysReset = prisonSearchOpts.getPkeysReset();
        if (pkeysReset == null) {
            log.info("重置参数为空.");
            return 1;
        }
        java.sql.Connection conn;
        PreparedStatement pstm = null;
        String sql = "upsert into " + ObjectInfoTable.TABLE_NAME + "(" + ObjectInfoTable.ROWKEY + ", " +
                ObjectInfoTable.LOCATION + ") select id, ? from " +  ObjectInfoTable.TABLE_NAME + " where " +
                ObjectInfoTable.PKEY + " = ?";
        try {
            conn = PhoenixJDBCHelper.getInstance().getConnection();
            pstm = conn.prepareStatement(sql);
            for (String pkey : pkeysReset) {
                pstm.setString(1, null);
                pstm.setString(2, pkey);
                pstm.executeUpdate();
                conn.commit();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return 1;
        } finally {
            PhoenixJDBCHelper.closeConnection(null, pstm);
        }
        log.info(sql);
        return 0;
    }

    /**
     * 用来获取对象类型下，各个位置的人的个数
     * @param prisonSearchOpts 对应的是pkeysCount
     * @return PrisonCountResults, 封装好的结果。
     */
    public PrisonCountResults countByLocation(PrisonSearchOpts prisonSearchOpts) {
        if (prisonSearchOpts == null) {
            log.info("传进来的参数为空.");
            return new PrisonCountResults();
        }
        List<String> pkeysCount = prisonSearchOpts.getPkeysCount();
        if (pkeysCount == null) {
            log.info("参数列表为空.");
            return new PrisonCountResults();
        }

        // sql 封装
        StringBuilder sql = new StringBuilder("select " + ObjectInfoTable.PKEY + ", " + ObjectInfoTable.LOCATION +
                ", count(" + ObjectInfoTable.LOCATION + ") as count from " + ObjectInfoTable.TABLE_NAME);
        for (int i = 0; i < pkeysCount.size(); i++) {
            if (i == 0 && pkeysCount.size() > 1) {
                sql.append(" where (" + ObjectInfoTable.PKEY + " = ? ");
            } else if(i == 0 && pkeysCount.size() == 1) {
                sql.append(" where " + ObjectInfoTable.PKEY + " = ? ");
            } else if (i == pkeysCount.size() - 1){
                sql.append(" or " + ObjectInfoTable.PKEY + " = ? )");
            } else {
                sql.append(" or " + ObjectInfoTable.PKEY + " = ? ");
            }
        }
        sql.append(" group by " + ObjectInfoTable.PKEY + ", " + ObjectInfoTable.LOCATION);

        log.info(sql.toString());

        //获取连接，执行查询
        java.sql.Connection conn;
        PreparedStatement pstm = null;
        ResultSet resultSet = null;
        PrisonCountResult prisonCountResult;
        PrisonCountResults prisonCountResults = new PrisonCountResults();
        List<PrisonCountResult> prisonCountResultsList = new ArrayList<>();
        Map<String, Integer> locationCounts = new HashMap<>();
        List<String> pkeysTmp = new ArrayList<>();
        try {
            conn = PhoenixJDBCHelper.getInstance().getConnection();
            pstm = conn.prepareStatement(sql.toString());
            for (int i = 0; i < pkeysCount.size(); i++) {
                pstm.setString(i+1, pkeysCount.get(i));
            }
            resultSet = pstm.executeQuery();
            resultSet.setFetchSize(100);

            int lable = 0;
            // 结果封装
            while (resultSet.next()) {
                String pkey = resultSet.getString(ObjectInfoTable.PKEY);
                if(!pkeysTmp.contains(pkey)) {
                    pkeysTmp.add(pkey);
                    if(pkeysTmp.size() > 1) {
                        prisonCountResult = new PrisonCountResult();
                        prisonCountResult.setPkey(pkeysTmp.get(lable));
                        prisonCountResult.setLocationCounts(locationCounts);
                        prisonCountResultsList.add(prisonCountResult);
                        locationCounts = new HashMap<>();
                        lable ++;
                    }
                }
                String location = resultSet.getString(ObjectInfoTable.LOCATION);
                Integer count = resultSet.getInt("count");
                locationCounts.put(location, count);
            }
            prisonCountResult = new PrisonCountResult();
            prisonCountResult.setPkey(pkeysTmp.get(lable));
            prisonCountResult.setLocationCounts(locationCounts);
            prisonCountResultsList.add(prisonCountResult);
            prisonCountResults.setResults(prisonCountResultsList);
        } catch (SQLException e) {
            e.printStackTrace();
            return prisonCountResults;
        } finally {
            PhoenixJDBCHelper.closeConnection(null, pstm, resultSet);
        }
        log.info(prisonSearchOpts.toString());
        log.info(sql.toString());
        log.info(prisonCountResults.toString());
        return prisonCountResults;
    }
}
