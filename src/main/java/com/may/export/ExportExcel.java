package com.may.export;

import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.db.DbUtil;
import cn.hutool.db.ds.DSFactory;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import cn.hutool.log.dialect.jdk.JdkLogFactory;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.may.utils.ConfigVO;
import com.may.utils.ExportUtil;

import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Queue;

/**
 * excel 导出程序
 */
public class ExportExcel {
    static {
        JdkLogFactory jdkLogFactory = new JdkLogFactory();
        LogFactory.setCurrentLogFactory(jdkLogFactory);
    }

    private static final Log log = LogFactory.get();

    public static void main(String[] args) throws SQLException {
        TimeInterval interval = new TimeInterval();
        log.info("程序执行开始...");
        interval.start();

        List<File> sqlFiles = ConfigVO.getSqlFiles();
        for (File sqlFile : sqlFiles) {
            dealExport(sqlFile);
        }
        log.info("程序执行结束,总耗时: {}", interval.intervalPretty());
    }

    public static void dealExport(File sqlFile) throws SQLException {
        TimeInterval interval = new TimeInterval();
        //得到导出指示（内部已经封装了：获取查询总行数 -> 获取需导出的 excel 个数）
        Queue<Integer> countLatch = ExportUtil.getExcelCountLatch(sqlFile);

        //开始导出
        Connection conn = DSFactory.get().getConnection();
        String sqlStr = ConfigVO.getSqlWithoutFastCountTag(sqlFile);
        PreparedStatement statement = conn.prepareStatement(sqlStr, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        statement.setFetchSize(Integer.MIN_VALUE);
        statement.setFetchDirection(ResultSet.FETCH_REVERSE);
        ResultSet result = statement.executeQuery();
        ResultSetMetaData metaData = result.getMetaData();
        int columnCount = metaData.getColumnCount();// 列数

        //-- 导出多个 excel 时的流程控制
        boolean exportStart = false; //导出开始状态
        boolean exportEnd = false; // 导出结束状态
        ExcelWriter writer = null; // 这里实际是创建了一个 SXSSFBook
        String excelFileName = null; //sql 文件名作为导出的 excel 文件名
        //-- 导出多个 excel 时的流程控制

        while (result.next()) {

            //构造一行数据
            HashMap<String, Object> map = new LinkedHashMap<>(columnCount);
            for (int j : NumberUtil.range(1, columnCount)) {
                String columnLabel = metaData.getColumnLabel(j);// 索引从 1 开始
                int columnType = metaData.getColumnType(j);
                Object columnValue = result.getObject(j);
                columnValue = ExportUtil.dealColumnValue(columnType, columnValue);
                map.put(columnLabel, columnValue);
            }

            //开始导出
            if (!exportStart) {
                //-- 创建 Excel 对象
                if (ConfigVO.getExcelNumber(sqlFile) > 1) {//如果有多页
                    excelFileName = StrUtil.format("{}{}-{}.xlsx", ConfigVO.getExcelDirectory(), StrUtil.removeSuffix(sqlFile.getName(), ".sql"), countLatch.peek());
                } else {
                    excelFileName = ConfigVO.getExcelDirectory() + StrUtil.removeSuffix(sqlFile.getName(), ".sql") + ".xlsx";
                }
                FileUtil.del(excelFileName);//如果存在文件，先删除
                writer = ExcelUtil.getBigWriter(excelFileName);
                //-- 创建 Excel 对象

                log.info("[{}] 开始导出第[ {}/{} ]个excel...", sqlFile.getName(), countLatch.peek(), ConfigVO.getExcelNumber(sqlFile));//获取队列头，但不移除
                interval.restart();
                writer.writeHeadRow(map.keySet());//标题行写入一次
                ExportUtil.dealExcelHeadStyle(writer, columnCount);//写完标题行后即可处理头部样式

                //变更为开始写入状态
                exportStart = true;
                exportEnd = false;
            }

            //没有到达预设的 excel 行数时，持续写入数据
            if (writer.getCurrentRow() % ConfigVO.getExcelRows() != 0) {
                writer.writeRow(map.values());
                ExportUtil.bodyRowContentCenter(writer, writer.getCurrentRow(), columnCount);//每写完一行，设置字体居中
            } else {
                //excel 写入完成
                writer.writeRow(map.values());//当读取行数等于 excelRows 时，该行也要被写入当前正在导出的 excel
                ExportUtil.bodyRowContentCenter(writer, writer.getCurrentRow(), columnCount);//每写完一行，设置字体居中
                writer.close();// 将数据刷入磁盘，刷新后会关闭流，释放 writer 对象
                log.info("[{}] 导出成功：{} ,耗时: {}", sqlFile.getName(), excelFileName, interval.intervalPretty());
                //为下一次导出初始化
                ExportUtil.restBodyCellStyle();
                exportStart = false;
                exportEnd = true;
                countLatch.poll();//移除队列头
            }
        }

        //考虑到，当导出的查询行数不足 excelRows 时，需要在这里释放 writer 对象
        if (!exportEnd) {
            writer.close();
            log.info("[{}] 导出成功：{} ,耗时: {}", sqlFile.getName(), excelFileName, interval.intervalPretty());
            //为下一次导出初始化
            ExportUtil.restBodyCellStyle();
        }
        //一个 sql 文件的查询导出完毕，关闭连接
        DbUtil.close(conn);
        //重置部分数据
        ConfigVO.resetPartDict();
    }

    /**
     * 自动调整合适的分页，平均分配（这样不太好，导出的 excel 会多出一些）
     *
     * @param cnt sql查询结果的总行数
     * @return 分页大小
     */
    public Integer autoPageSize(int cnt) {
        // 设定分页大小的限制是不超过 60w
        int pow = 0;
        while (true) {
            // 假如600000.001，这里向下取整（舍弃）即可，在计算页数那会向上取整
            int size = cnt / (int) Math.pow(2, pow);
            if (size <= 600000) {
                return size;
            }
            pow += 1;
        }
    }
}
