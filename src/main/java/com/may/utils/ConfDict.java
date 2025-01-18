package com.may.utils;

import cn.hutool.core.lang.Dict;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * 自定义一个字典，存放处理后的配置参数，便于其他地方取
 */
public class ConfDict extends Dict {

    private static final ConfDict confDict = new ConfDict();

    private ConfDict() {
    }

    public static ConfDict getConfDict() {
        return confDict;
    }

    /**
     * 限定 key 值范围
     *
     * @param conf
     * @param value
     * @return
     */
    public Object put(ConfEnum conf, Object value) {
        return super.put(conf.getCode(), value);
    }

    public String getStr(ConfEnum conf) {
        return super.getStr(conf.getCode());
    }

    public Long getLong(ConfEnum conf) {
        return super.getLong(conf.getCode());
    }

    public Integer getInt(ConfEnum conf) {
        return super.getInt(conf.getCode());
    }

    public List getList(ConfEnum conf) {
        return (List) super.getObj(conf.getCode());
    }

    public Pair<String, String> getPair(ConfEnum conf) {
        //不给瞎搞，只有枚举为 FAST_COUNT_TAG 才能取到值
        if (!conf.getCode().equals(ConfEnum.FAST_COUNT_TAG.getCode()))
            throw new RuntimeException("别瞎搞，目前只能取 FAST_COUNT_TAG 的值");
        return (Pair<String, String>) super.getObj(ConfEnum.FAST_COUNT_TAG.getCode());

    }

    public Object remove(ConfEnum conf) {
        return super.remove(conf.getCode());
    }
}
