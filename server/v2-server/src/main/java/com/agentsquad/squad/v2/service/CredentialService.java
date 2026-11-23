package com.agentsquad.squad.v2.service;

import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.v2.config.properties.CredentialProperties;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 外部调用凭据
 * <p>
 * 配置数据只保存凭据引用名；本服务列出可选引用名，并在连接或调用时解析密钥值。密钥值不返回给前端，也不写日志。
 */
@Service
public class CredentialService {
    @Resource
    private CredentialProperties properties;

    /**
     * 列出凭据引用名
     */
    public List<String> names() {
        return properties.getEntries().keySet().stream().sorted().toList();
    }

    /**
     * 校验凭据引用
     * <p>
     * 引用名为空表示不使用凭据；填写了但未配置时返回参数错误。
     */
    public void requireConfigured(String credentialRef) {
        if (credentialRef != null && !properties.getEntries().containsKey(credentialRef)) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "凭据 " + credentialRef + " 未在服务端配置");
        }
    }

    /**
     * 解析凭据值
     *
     * @return 未使用凭据时返回 null
     */
    public String resolve(String credentialRef) {
        if (credentialRef == null) {
            return null;
        }
        requireConfigured(credentialRef);
        return properties.getEntries().get(credentialRef);
    }
}
