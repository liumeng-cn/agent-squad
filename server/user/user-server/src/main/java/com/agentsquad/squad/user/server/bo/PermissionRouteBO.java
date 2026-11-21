package com.agentsquad.squad.user.server.bo;

import lombok.Data;

/**
 * 接口权限规则投影
 * <p>
 * 用户模块查询数据库规则，不直接作为对外协议模型返回。
 */
@Data
public class PermissionRouteBO {
    /**
     * 所需功能权限编码
     */
    private String permissionCode;

    /**
     * HTTP 请求方法
     */
    private String httpMethod;

    /**
     * 管理前缀后的完整匹配表达式
     */
    private String pathPattern;

    /**
     * 规则匹配顺序
     */
    private Integer sortOrder;
}
