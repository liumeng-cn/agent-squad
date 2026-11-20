package com.agentsquad.squad.common.vo;

import lombok.Data;

import java.util.List;

/**
 * 通用分页结果
 * <p>
 * 返回当前页数据、总条数及页码大小，供业务列表统一使用。
 */
@Data
public class PageVO<T> {
    /**
     * 当前页记录
     */
    private List<T> records;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 当前页码
     */
    private long current;

    /**
     * 每页条数
     */
    private long size;
}
