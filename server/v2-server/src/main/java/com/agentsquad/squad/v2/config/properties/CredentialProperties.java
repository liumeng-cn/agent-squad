package com.agentsquad.squad.v2.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 外部调用凭据配置
 * <p>
 * MCP 服务和 HTTP 工具只保存凭据引用名，密钥值由服务端配置提供，不入库、不出现在接口和日志中。
 */
@Data
@ConfigurationProperties("agentsquad.v2.credentials")
public class CredentialProperties {
    /**
     * 凭据引用名与密钥值。
     */
    private Map<String, String> entries = new LinkedHashMap<>();
}
