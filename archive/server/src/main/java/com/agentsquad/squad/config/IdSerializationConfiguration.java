package com.agentsquad.squad.config;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 业务 ID 的 JSON 序列化配置。
 * <p>
 * 业务 ID 在 Java 内统一为 Long，名称以 Id/Ids 结尾的属性输出十进制字符串，避免浏览器 number 精度丢失；
 * 版本号、计数等其它数值字段保持原有数字形态。
 */
@Configuration
public class IdSerializationConfiguration {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer idAsStringCustomizer() {
        // 追加安装，避免覆盖 Spring Boot 自动注册的 JavaTime、Jdk8 等基础模块。
        return builder -> builder.modulesToInstall(new IdAsStringModule());
    }
}
