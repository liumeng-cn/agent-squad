package com.agentsquad.squad.v2.bo;

import com.agentsquad.squad.v2.enums.HttpToolMethod;
import lombok.Data;

import java.net.URI;
import java.util.Map;

/**
 * HTTP 工具实际请求
 * <p>
 * 按模板和本次参数渲染出的请求；summary 已遮蔽凭据，可用于日志和试运行回显。
 */
@Data
public class HttpToolRequestBO {
    /**
     * 请求方法
     */
    private HttpToolMethod method;

    /**
     * 完整请求地址
     */
    private URI uri;

    /**
     * 请求头
     */
    private Map<String, String> headers;

    /**
     * 请求体，无请求体时为空
     */
    private String body;

    /**
     * 已遮蔽凭据的请求摘要
     */
    private String summary;
}
