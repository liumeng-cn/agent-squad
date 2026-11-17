package com.agentsquad.squad.config;

import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * 业务 ID 字符串化模块
 * <p>
 * 注册到 Spring Boot 的 ObjectMapper 后，以 Id/Ids 结尾的 Long 属性按十进制字符串输出。
 */
public class IdAsStringModule extends SimpleModule {

    public IdAsStringModule() {
        super("id-as-string");
        setSerializerModifier(new IdPropertySerializerModifier());
    }
}
