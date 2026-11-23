package com.agentsquad.squad.v2.bo;

import com.agentsquad.squad.v2.enums.HttpToolMethod;
import lombok.Data;

import java.util.List;

/**
 * HTTP 工具请求模板
 * <p>
 * 工具版本中保存的接口调用配置，保存校验与运行时渲染共用；凭据只保存引用名。
 */
@Data
public class HttpToolSpecBO {
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
    private List<KeyValueBO> headers;

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
