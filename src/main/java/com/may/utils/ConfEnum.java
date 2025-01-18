package com.may.utils;

import java.util.Arrays;

/**
 * 一些需要放在系统中便于使用的变量
 */
public enum ConfEnum {
    EXCEL_ROWS("单个excel的行数", "excel_rows"),
    EXCEL_DIRECTORY("excel的路径", "excel_directory"),
    SQL_DIRECTORY("sql文件的路径", "sql_directory"),
    TOTAL_COUNT("sql查询的总行数", "total_count"),
    SQL_FILE_COUNT("待处理的 sql 文件个数", "sql_file_count"),
    SQL_FILES("待处理的 sql", "sql_files"),
    EXCEL_NUMBER("需要导出的 excel 文件个数", "excel_number"),
    FAST_COUNT_TAG("快速分页标记", "fast_count_tag");

    private final String description;
    private final String code;

    ConfEnum(String description, String code) {
        this.description = description;
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public String getCode() {
        return code;
    }

    /**
     * 判断是否包含在枚举中
     *
     * @param code
     * @return
     */
    public static Boolean contain(String code) {
        return Arrays.stream(ConfEnum.values()).anyMatch(conf -> conf.code.equals(code));
    }
}
