package com.may.utils;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Console;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Db;
import cn.hutool.db.ds.DSFactory;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import cn.hutool.log.dialect.jdk.JdkLogFactory;
import cn.hutool.poi.excel.ExcelWriter;
import com.mysql.cj.MysqlType;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;

import java.io.File;
import java.io.FileFilter;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * excel 导出相关的工具
 */
public class ExportUtil {
    static {
        JdkLogFactory jdkLogFactory = new JdkLogFactory();

        LogFactory.setCurrentLogFactory(jdkLogFactory);
    }

    private static final Log log = LogFactory.get();
    private static final Map<Integer, Function<Object, Object>> resultTypeHandler = new HashMap<>();
    private static CellStyle bodyCellStyle;

    static {
        resultTypeHandlerInit();//初始化，注册结果集数据类型处理器
    }

    /**
     * 格式化结果集数据类型
     *
     * @param columnType
     * @param columnValue
     * @return
     */
    public static Object dealColumnValue(Integer columnType, Object columnValue) {
        //System.out.println(columnType);//可以打印字段类型值，去 MysqlType 中查找对应的枚举加在 resultTypeHandler 中
        return resultTypeHandler.get(columnType).apply(columnValue);
    }

    /**
     * 每个 excel 导出完，重置下样式
     */
    public static void restBodyCellStyle(){
        bodyCellStyle = null;
    }

    /**
     * 设置居中显示
     * @param writer
     * @return
     */
    public static CellStyle getCellStyle(ExcelWriter writer){
        return Optional.ofNullable(bodyCellStyle).orElseGet(() -> {
            bodyCellStyle = writer.createRowStyle(0);
            bodyCellStyle.setAlignment(HorizontalAlignment.CENTER);
            return bodyCellStyle;
        });
    }

    /**
     * 处理 excel 头部样式（居中显示、字体加粗、首行冻结、自动筛选）。至少需要在写完头部数据之后方可处理
     *
     * @param writer      excel 对象
     * @param columnCount 列数
     */
    public static void dealExcelHeadStyle(ExcelWriter writer, Integer columnCount) {
        //禁用默认样式，StyleSet被置 null
        writer.disableDefaultStyle();

        CellStyle headCellStyle = writer.createRowStyle(0);//创建标题的样式

        //创建单元格样式，设置字体加粗
        Font font = writer.createFont();
        font.setBold(true);
        headCellStyle.setFont(font);
        headCellStyle.setAlignment(HorizontalAlignment.CENTER);//字体居中
        Sheet sheet = writer.getSheet();
        /*
        四个参数的含义：
            ａ表示要冻结的列数；
            ｂ表示要冻结的行数；
            ｃ表示右边区域[可见]的首列序号；
            ｄ表示下边区域[可见]的首行序号；
            举例：
            CreateFreezePane(1,0,1,0):冻结第一列，冻结列右侧的第一列为B列
            CreateFreezePane(2,0,5,0):冻结左侧两列，冻结列右侧的第一列为F列
            CreateFreezePane(0,1,0,1):冻结第一行,冻结行下侧第一行的左边框显示“2”
         */
        sheet.createFreezePane(0, 1, 0, 1);//首行冻结

        Row row = sheet.getRow(0);//得到首行
        //处理标题行样式
        for (int i : ArrayUtil.range(columnCount)) {
            CellRangeAddress cellAddresses = new CellRangeAddress(0, 0, 0, i);//首行的单元格对象
            sheet.setAutoFilter(cellAddresses);//设置数据筛选
            Cell cell = row.getCell(i);
            cell.setCellStyle(headCellStyle);//设置标题行样式
            //设置头部单元格列宽（处理表头是中文的情况）
            sheet.setColumnWidth(i, cell.getStringCellValue().getBytes().length * 2 * 256);
        }
    }

    /**
     * 设置行内容居中
     *
     * @param writer
     * @param currentRow
     * @param columnCount
     */
    public static void bodyRowContentCenter(ExcelWriter writer, Integer currentRow, Integer columnCount) {
        CellStyle rowStyle = getCellStyle(writer);//得到通用样式
        Row row = writer.getSheet().getRow(currentRow - 1);//行、列索引从0开始，而currentRow从1开始
        IntStream.range(0, columnCount).forEach(i -> {
            Cell cell = row.getCell(i);
            cell.setCellStyle(rowStyle);
        });
    }

    /**
     * 列出待导出的 sql 文件
     *
     * @return
     */
    public static List<File> getSqlFiles() throws RuntimeException {
        String sqlDirectory = ConfigVO.getSqlDirectory();
        FileFilter fileFilter = file -> FileUtil.pathEndsWith(file, "sql");//只包含 sql 文件
        // 识别 classpath 下的文件，且兼容 spring风格，sql\\ 和 sql/ 都可以被识别
        List<File> files = FileUtil.loopFiles(sqlDirectory, fileFilter);//如果没有 sql 文件，会返回一个空的 ArrayList
        List<String> fileNames = files.stream().map(File::getName).collect(Collectors.toList());
        String tip = StrUtil.format("已读取 sql 文件总数：[ {} ] === {}", files.size(), fileNames);
        if (files.size() == 0) throw new RuntimeException(tip);
        Console.error(tip);
        return files;
    }

    /**
     * 读取 sql 文件，统一加上尾部的 ; ，避免 StrUtil.sub 截断问题
     *
     * @param sqlFile
     * @return
     */
    public static String getSqlStr(File sqlFile) {
        String sqlStr = FileUtil.readString(sqlFile, "utf-8");
        /*if (!StrUtil.endWith(sqlStr, ";")) {
            sqlStr += ";";
        }*/
        //如果不包含指定后缀就加上。同理还有前缀的方法
        return StrUtil.addSuffixIfNot(sqlStr, ";");//补全分号，后面字符串好统一操作
    }

    /**
     * 查询总行数
     *
     * @param sqlFile
     * @return
     * @throws SQLException
     */
    public static Long queryTotalCount(File sqlFile) {
        TimeInterval interval = new TimeInterval();
        String countSql = ConfigVO.getCountSql(sqlFile);
        Number totalCount = 0;
        try {
            log.info("[{}] 开始查询总行数...", sqlFile.getName());
            interval.restart();//启动一个计时器
            totalCount = Db.use(DSFactory.get()).queryNumber(countSql);//默认数据源
            log.info("[{}] 查询结束,总行数为：[ {} ] ,耗时: {}", sqlFile.getName(), totalCount.longValue(), interval.intervalPretty());
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return totalCount.longValue();
    }

    /**
     * 计算一个 sql 查询，需要导出的 excel 个数
     *
     * @param sqlFile
     * @return
     */
    public static Integer getExcelNumber(File sqlFile) {
        //获取总行数
        Long totalCount = ConfigVO.getTotalCount(sqlFile);
        Double excelNumber = Math.ceil(Convert.toDouble(totalCount) / ConfigVO.getExcelRows());
        return excelNumber.intValue();
    }

    /**
     * 返回 excel 个数长度的队列，用于指示 '当前正在导出第 x 个 excel'
     *
     * @return x
     */
    public static Queue<Integer> getExcelCountLatch(File sqlFile) {
        Integer excelNumber = ConfigVO.getExcelNumber(sqlFile);
        Queue<Integer> queue = new LinkedList<>();
        IntStream.rangeClosed(1, excelNumber).forEach(queue::offer);
        return queue;
    }

    /**
     * 格式化目录，统一在结尾补上 \ 或 /
     *
     * @param directory
     * @return
     */
    public static String fmtDirectory(String directory) {
        String suffix = System.getProperty("os.name").toLowerCase().startsWith("win") ? "\\" : "/";//判断当前系统
        if (!StrUtil.endWith(directory, suffix)) directory += suffix;//补全目录，便于操作统一}
        return directory;
    }

    /**
     * 表驱动方式，干掉 if...else
     * 如果使用时发现某种数据类型没有对应的处理器，则会抛 nullPointException ，此时直接在尾部添加对应的类型处理器即可
     */
    private static void resultTypeHandlerInit() {
        resultTypeHandler.put(MysqlType.VARCHAR.getJdbcType(), Convert::toStr);
        resultTypeHandler.put(MysqlType.JSON.getJdbcType(), Convert::toStr);
        resultTypeHandler.put(MysqlType.ENUM.getJdbcType(), Convert::toStr);
        resultTypeHandler.put(MysqlType.SET.getJdbcType(), Convert::toStr);
        resultTypeHandler.put(MysqlType.TEXT.getJdbcType(), Convert::toStr);
        resultTypeHandler.put(MysqlType.TINYTEXT.getJdbcType(), Convert::toStr);
        resultTypeHandler.put(MysqlType.BLOB.getJdbcType(), Convert::toStr);
        resultTypeHandler.put(MysqlType.TINYBLOB.getJdbcType(), Convert::toStr);
        resultTypeHandler.put(MysqlType.MEDIUMBLOB.getJdbcType(), Convert::toStr);
        resultTypeHandler.put(MysqlType.MEDIUMTEXT.getJdbcType(), Convert::toStr);
        resultTypeHandler.put(MysqlType.LONGBLOB.getJdbcType(), Convert::toStr);
        resultTypeHandler.put(MysqlType.LONGTEXT.getJdbcType(), Convert::toStr);
        resultTypeHandler.put(MysqlType.CHAR.getJdbcType(), Convert::toStr);
        resultTypeHandler.put(MysqlType.BINARY.getJdbcType(), Convert::toStr);
        resultTypeHandler.put(MysqlType.GEOMETRY.getJdbcType(), Convert::toStr);
        resultTypeHandler.put(MysqlType.UNKNOWN.getJdbcType(), Convert::toStr);
        // MysqlType.BIT 对应数据库中的 tinyint 数据类型
        resultTypeHandler.put(MysqlType.BIT.getJdbcType(), Convert::toBool);//toShort 将数值原样输出；toBool 会将 1/0 转换为 true/false
        resultTypeHandler.put(MysqlType.TINYINT.getJdbcType(), Convert::toInt);
        resultTypeHandler.put(MysqlType.TINYINT_UNSIGNED.getJdbcType(), Convert::toInt);
        resultTypeHandler.put(MysqlType.SMALLINT.getJdbcType(), Convert::toInt);
        resultTypeHandler.put(MysqlType.SMALLINT_UNSIGNED.getJdbcType(), Convert::toInt);
        resultTypeHandler.put(MysqlType.INT.getJdbcType(), Convert::toInt);
        resultTypeHandler.put(MysqlType.MEDIUMINT.getJdbcType(), Convert::toInt);
        resultTypeHandler.put(MysqlType.MEDIUMINT_UNSIGNED.getJdbcType(), Convert::toInt);
        resultTypeHandler.put(MysqlType.INT_UNSIGNED.getJdbcType(), Convert::toLong);
        resultTypeHandler.put(MysqlType.BIGINT.getJdbcType(), Convert::toLong);
        resultTypeHandler.put(MysqlType.BIGINT_UNSIGNED.getJdbcType(), Convert::toBigInteger);
        resultTypeHandler.put(MysqlType.FLOAT.getJdbcType(), Convert::toFloat);
        resultTypeHandler.put(MysqlType.FLOAT_UNSIGNED.getJdbcType(), Convert::toFloat);
        resultTypeHandler.put(MysqlType.DOUBLE.getJdbcType(), Convert::toDouble);
        resultTypeHandler.put(MysqlType.DOUBLE_UNSIGNED.getJdbcType(), Convert::toDouble);
        resultTypeHandler.put(MysqlType.DECIMAL.getJdbcType(), Convert::toBigDecimal);
        resultTypeHandler.put(MysqlType.DECIMAL_UNSIGNED.getJdbcType(), Convert::toBigDecimal);
        resultTypeHandler.put(MysqlType.BOOLEAN.getJdbcType(), Convert::toBool);
        resultTypeHandler.put(MysqlType.NULL.getJdbcType(), Convert::toStr);
        resultTypeHandler.put(MysqlType.VARBINARY.getJdbcType(), Convert::toStr);
        //日期类型需要格式化
        resultTypeHandler.put(MysqlType.YEAR.getJdbcType(), obj -> DateUtil.formatDate(Convert.toDate(obj)));
        resultTypeHandler.put(MysqlType.DATE.getJdbcType(), obj -> DateUtil.formatDate(Convert.toDate(obj)));
        resultTypeHandler.put(MysqlType.TIME.getJdbcType(), obj -> DateUtil.formatTime(Convert.toDate(obj)));
        resultTypeHandler.put(MysqlType.DATETIME.getJdbcType(), obj -> DateUtil.formatDateTime(Convert.toDate(obj)));
        resultTypeHandler.put(MysqlType.TIMESTAMP.getJdbcType(), obj -> DateUtil.formatDateTime(Convert.toDate(obj)));
    }
}
