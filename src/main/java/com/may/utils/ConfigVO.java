package com.may.utils;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Console;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.setting.Setting;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * ConfigVO 中的属性只读，只提供 get 方法，返回 ConfDict 中的值，将处理后的值放进去，参与系统操作。
 * ConfDict 只维护在 ConfigVO 中，对外暴露 ConfigVO 的方法。
 */
public class ConfigVO {
    private static final Setting setting = new Setting("config/config.setting");
    private static final Integer excelRows = setting.getInt("excel_rows");
    private static final String excelDirectory = setting.getStr("excel_directory");
    private static final String sqlDirectory = setting.getStr("sql_directory");
    private static final Long totalCount = setting.getLong("query_total_count");
    private static final String fastCountPrefix = setting.getStr("fast_count_prefix");
    private static final String fastCountSuffix = setting.getStr("fast_count_suffix");
    private static final ConfDict confDict = ConfDict.getConfDict();

    public static String getExcelDirectory() {
        return Optional.ofNullable(confDict.getStr(ConfEnum.EXCEL_DIRECTORY)).orElseGet(() -> {
            String val = Optional.ofNullable(excelDirectory)
                    .orElse(StrUtil.format("{}/export_{}", FileUtil.getParent(getSqlDirectory(), 1), LocalDate.now()));
            confDict.put(ConfEnum.EXCEL_DIRECTORY, ExportUtil.fmtDirectory(val));
            return confDict.getStr(ConfEnum.EXCEL_DIRECTORY);
        });
    }

    public static Integer getExcelRows() {
        return Optional.ofNullable(confDict.getInt(ConfEnum.EXCEL_ROWS)).orElseGet(() -> {
            if (excelRows == null || excelRows <= 0) throw new RuntimeException("别瞎搞，请设置正确的 excel_rows");
            confDict.put(ConfEnum.EXCEL_ROWS, excelRows);
            return confDict.getInt(ConfEnum.EXCEL_ROWS);
        });
    }

    public static Long getTotalCount(File sqlFile) {
        return Optional.ofNullable(confDict.getLong(ConfEnum.TOTAL_COUNT)).orElseGet(() -> {
            //仅当只有一个 sql 文件时，预设置的 totalCount 才会生效
            if (totalCount != null && getSqlFileCount() == 1) {
                if (totalCount <= 0 || !NumberUtil.isLong(Convert.toStr(totalCount)))//非正整数为不合法
                    throw new RuntimeException("别捣蛋！！！请传入合法的 query_total_count");
                Console.error("已预设总行数：[ {} ]", totalCount);
                confDict.put(ConfEnum.TOTAL_COUNT, totalCount);
            } else {//否则走查询
                confDict.put(ConfEnum.TOTAL_COUNT, ExportUtil.queryTotalCount(sqlFile));
            }
            return confDict.getLong(ConfEnum.TOTAL_COUNT);
        });
    }

    public static String getSqlDirectory() {
        return Optional.ofNullable(confDict.getStr(ConfEnum.SQL_DIRECTORY)).orElseGet(() -> {
            String val = Optional.ofNullable(sqlDirectory).orElse("sql");
            confDict.put(ConfEnum.SQL_DIRECTORY, ExportUtil.fmtDirectory(val));
            return confDict.getStr(ConfEnum.SQL_DIRECTORY);
        });
    }

    /**
     * 遍历一次 sql 文件之后，会缓存进 confDict 中，key 为 ConfEnum.SQL_FILES
     *
     * @return
     */
    public static List<File> getSqlFiles() {
        return (List<File>) Optional.ofNullable(confDict.getList(ConfEnum.SQL_FILES)).orElseGet(() -> {
            //lambda 表达式中不能返回捕获的类型，所以这里的返回值必须是不带泛型的，否则语法检查就会报错
            List sqlFiles = ExportUtil.getSqlFiles();
            confDict.put(ConfEnum.SQL_FILES, sqlFiles);
            confDict.put(ConfEnum.SQL_FILE_COUNT, sqlFiles.size());
            return confDict.getList(ConfEnum.SQL_FILES);
        });
    }

    public static Integer getSqlFileCount() {
        return Optional.ofNullable(confDict.getInt(ConfEnum.SQL_FILE_COUNT)).orElseGet(() -> {
            int count = getSqlFiles().size();
            confDict.put(ConfEnum.SQL_FILE_COUNT, count);
            return confDict.getInt(ConfEnum.SQL_FILE_COUNT);
        });
    }

    public static Integer getExcelNumber(File sqlFile) {
        return Optional.ofNullable(confDict.getInt(ConfEnum.EXCEL_NUMBER)).orElseGet(() -> {
            Integer excelNumber = ExportUtil.getExcelNumber(sqlFile);
            Console.error("[{}] 需要导出的 excel 文件个数为：[ {} ]", sqlFile.getName(), excelNumber);
            confDict.put(ConfEnum.EXCEL_NUMBER, excelNumber);
            return confDict.getInt(ConfEnum.EXCEL_NUMBER);
        });
    }

    /**
     * 一个 sql 文件导完后，需要重置 ConfDict 中的部分元素
     */
    public static void resetPartDict() {
        //只有一个 sql 时就没必要做了
        if (confDict.getInt(ConfEnum.SQL_FILE_COUNT) == 1) return;
        confDict.remove(ConfEnum.TOTAL_COUNT);
        confDict.remove(ConfEnum.EXCEL_NUMBER);
    }

    public static Pair<String, String> getFastCountTag() {
        return Optional.ofNullable(confDict.getPair(ConfEnum.FAST_COUNT_TAG)).orElseGet(() -> {
            Pair<String, String> fastCountTag;
            if (fastCountPrefix == null || fastCountSuffix == null) {//必须要同时设置才行，避免只设置了前缀或者后缀
                fastCountTag = Pair.of("@fast{", "}@");
            } else {
                fastCountTag = Pair.of(fastCountPrefix, fastCountSuffix);
            }
            confDict.put(ConfEnum.FAST_COUNT_TAG, fastCountTag);
            return confDict.getPair(ConfEnum.FAST_COUNT_TAG);
        });

    }

    /**
     * 检查，sql 语句中是否包含快速分页的标记
     *
     * @param sqlStr
     * @return
     */
    public static Boolean containFastTag(String sqlStr) {
        Pair<String, String> pair = getFastCountTag();
        //前后缀同时包含才算 true
        return StrUtil.contains(sqlStr, pair.getLeft()) && StrUtil.contains(sqlStr, pair.getRight());
    }

    /**
     * 得到被 fast_count_prefix 和 fast_count_suffix 包裹的字符串
     *
     * @param file 原始 sql 文件
     * @return 返回 select count(1) xxx;
     */
    public static String getCountSql(File file) {
        String sqlStr = ExportUtil.getSqlStr(file);
        if (containFastTag(sqlStr)) {
            Pair<String, String> pair = confDict.getPair(ConfEnum.FAST_COUNT_TAG);
            String fastCountStr = StrUtil.subBetween(sqlStr, pair.getLeft(), pair.getRight());
            return StrUtil.format("select count(1) cnt from {};", fastCountStr);
        } else {
            return StrUtil.format("select count(1) cnt {};", StrUtil.sub(sqlStr, StrUtil.indexOfIgnoreCase(sqlStr, "from"), -1));
        }
    }

    /**
     * 去除掉 fast_count_prefix 和 fast_count_suffix
     *
     * @param file 原始 sql 文件
     * @return 返回正常的原始 sql，供查询导出使用
     */
    public static String getSqlWithoutFastCountTag(File file) {
        String sqlStr = ExportUtil.getSqlStr(file);
        if (!containFastTag(sqlStr)) return sqlStr;//如果不包含快速分页标记，直接返回
        Pair<String, String> pair = confDict.getPair(ConfEnum.FAST_COUNT_TAG);
        sqlStr = StrUtil.replace(sqlStr, pair.getLeft(), " ");
        sqlStr = StrUtil.replace(sqlStr, pair.getRight(), " ");
        return sqlStr;
    }
}
