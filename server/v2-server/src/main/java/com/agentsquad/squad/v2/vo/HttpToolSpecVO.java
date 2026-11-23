package com.agentsquad.squad.v2.vo;

import com.agentsquad.squad.v2.enums.HttpToolMethod;
import lombok.Data;

import java.util.List;

/**
 * HTTP 工具请求模板视图
 * <p>
 * 返回工具版本中保存的接口调用配置，凭据只返回引用名。
 */
@Data
public class HttpToolSpecVO {
    /**
     * 请求方法
     */
    private HttpToolMethod method;

    /**
     * 地址模板
     */
    private String url;

    /**
     * 请求头模板
     */
    private List<KeyValueVO> headers;

    /**
     * JSON 请求体模板
     */
    private String bodyTemplate;

    /**
     * 凭据引用名
     */
    private String credentialRef;

    /**
     * 超时毫秒数
     */
    private Integer timeoutMs;

    /**
     * 结果提取的 JSON Pointer
     */
    private String resultPointer;
}
