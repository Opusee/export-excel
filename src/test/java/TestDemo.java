import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Console;
import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import cn.hutool.log.dialect.jdk.JdkLogFactory;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLLimit;
import com.alibaba.druid.sql.ast.statement.*;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlSelectQueryBlock;
import com.alibaba.druid.sql.dialect.mysql.parser.MySqlStatementParser;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlOutputVisitor;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlSchemaStatVisitor;
import com.alibaba.druid.stat.TableStat;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public class TestDemo {

    @Test
    public void test1() {
        Queue<Integer> queue = new LinkedList<>();
        IntStream.rangeClosed(1, 5).forEach(queue::offer);
        Console.log("入队列：{}", queue);
        System.out.print("出队列：");
        while (!queue.isEmpty()) {
            System.out.print(queue.poll() + " ");
        }
        System.out.println();

        Stack<Integer> stack = new Stack<>();
        IntStream.rangeClosed(1, 5).forEach(stack::push);
        Console.log("入栈：{}", stack);
        System.out.print("出栈：");
        while (!stack.empty()) {
            System.out.print(stack.pop() + " ");
        }
    }

    @Test
    public void test2() {
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("姓名", "张三");
        row1.put("年龄", 23);
        row1.put("成绩", 88.32);
        row1.put("是否合格", true);
        row1.put("考试日期", DateUtil.date());

        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("姓名", "李四");
        row2.put("年龄", 33);
        row2.put("成绩", 59.50);
        row2.put("是否合格", false);
        row2.put("考试日期", DateUtil.date());

        ArrayList<Map<String, Object>> rows = CollUtil.newArrayList(row1, row2);
        ExcelWriter writer = ExcelUtil.getWriter("/Users/may/ztmp/demo1.xlsx");
        // 一次性写出内容，使用默认样式，强制输出标题

        writer.getSheet().createFreezePane(0, 1, 0, 1);
        Sheet sheet = writer.getSheet();
        int columnCount = writer.getColumnCount();

        CellStyle cellStyle = writer.getCellStyle();
        CellStyle headCellStyle = writer.getHeadCellStyle();
        Font font = writer.createFont();
        font.setBold(true);
        cellStyle.setFont(font);

        writer.writeHeadRow(row1.keySet());
        IntStream.range(0, columnCount).forEach(i -> {
            CellRangeAddress cellAddresses = new CellRangeAddress(0, 0, 0, i);
            sheet.setAutoFilter(cellAddresses);

            Row row = sheet.getRow(0);//得到表头
            Cell cell = row.getCell(i);

            cell.setCellStyle(cellStyle);
            System.out.println(cell.getStringCellValue());
            sheet.setColumnWidth(i, cell.getStringCellValue().getBytes().length * 2 * 256);
        });

        for (Map map : rows) {
            writer.writeRow(map.values());
        }
//        writer.write(rows, true);

        // 关闭writer，释放内存
        writer.close();
    }

    @Test
    public void test3() {
        String pre = "@fast{";
        String sfx = "}@";
        String reg = StrUtil.format("{}(\\S+){}", pre, sfx);
        System.out.println(reg);
        Pattern pattern = Pattern.compile(reg);
        Matcher matcher = pattern.matcher("@fast{ from tab }@");

        if (matcher.matches()) {
            System.out.println(matcher.group(1));
        }
    }

    @Test
    public void test4() {
        System.out.println(StrUtil.contains("ddddd}@", "}@"));

        System.out.println(StrUtil.replace("sele * from @fast{ tab }@ where", "@fast{", "}@"));

        System.out.println(StrUtil.subBetween("sele * from @fast{ tab }@ where", "@fast{", "}@"));
    }

    @Test
    public void test5() {
        String sqlStr = "select * from @fast{ table }@ where...";
        sqlStr = StrUtil.replace(sqlStr, "@fast{", " ");
        sqlStr = StrUtil.replace(sqlStr, "}@", " ");
        System.out.println(sqlStr);

        System.out.println(StrUtil.sub(sqlStr, StrUtil.indexOfIgnoreCase(sqlStr, "from"), -1));
    }

    @Test
    public void test6() {
        System.out.println(System.getProperty("user.home"));
        JdkLogFactory jdkLogFactory = new JdkLogFactory();
        LogFactory.setCurrentLogFactory(jdkLogFactory);
        Log log = LogFactory.get();
        System.out.println(log.getName());
    }

    @Test
    public void test7() {
        /*String sql = """
                select 
                    a.name,group_concat(a.price) price
                 from tb_order a left join tb_order_detail b on a.order_id = b.order_id
                 where id in (select id from tb_order where id = 1) 
                 group by a.name
                 having count(*) > 1 
                 order by a.id desc
                 limit 0,100
                """;

        MySqlStatementParser mySqlStatementParser = new MySqlStatementParser(sql);
        SQLSelectStatement sqlSelectStatement = (SQLSelectStatement) mySqlStatementParser.parseSelect();

        SQLSelect sqlSelect = sqlSelectStatement.getSelect();
        SQLSelectQuery sqlSelectQuery = sqlSelect.getQuery();
        if (sqlSelectQuery instanceof MySqlSelectQueryBlock mySqlSelectQueryBlock) {
            MySqlOutputVisitor where = new MySqlOutputVisitor(new StringBuilder());
            // 获取where 条件
            mySqlSelectQueryBlock.getWhere().accept(where);
            System.out.println("##########where###############");
            System.out.println(where.getAppender());

            SQLTableSource from = mySqlSelectQueryBlock.getFrom();

            // 获取表名
            System.out.println("############table_name##############");
            MySqlOutputVisitor tableName = new MySqlOutputVisitor(new StringBuilder());
            from.accept(tableName);
            System.out.println(tableName.getAppender());

            MySqlOutputVisitor join = new MySqlOutputVisitor(new StringBuilder());
            SQLJoinTableSource from1 = (SQLJoinTableSource) from;
            SQLExpr condition = from1.getCondition();
            condition.accept(join);
            System.out.println(join.getAppender());

            //获取 group by
            System.out.println("############group by##############");
            MySqlOutputVisitor groupBy = new MySqlOutputVisitor(new StringBuilder());
            mySqlSelectQueryBlock.getGroupBy().accept(groupBy);
            System.out.println(groupBy.getAppender());

            //获取 order by
            System.out.println("############order by##############");
            MySqlOutputVisitor orderBy = new MySqlOutputVisitor(new StringBuilder());
            mySqlSelectQueryBlock.getOrderBy().accept(orderBy);
            System.out.println(orderBy.getAppender());

            //获取 limit
            System.out.println("############limit##############");
            MySqlOutputVisitor limit = new MySqlOutputVisitor(new StringBuilder());
            mySqlSelectQueryBlock.getLimit().accept(groupBy);
            System.out.println(limit.getAppender());

            //   获取查询字段
            System.out.println("############查询字段##############");
            System.out.println(mySqlSelectQueryBlock.getSelectList());
        }*/
    }
}
