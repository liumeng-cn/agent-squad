package com.agentsquad.squad.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * 技能包导入资源限制。
 * <p>
 * 仅控制文本导入上限，不赋予脚本、网络或宿主文件系统权限。
 */
@Component
@RefreshScope
@ConfigurationProperties(prefix = "agentsquad.skills")
@Getter
@Setter
public class SkillPackageProperties {

    /** 每个技能包允许导入的文件数上限 */
    private int maxFiles = 64;

    /** 单个技能文本文件的字节数上限 */
    private int maxFileBytes = 262144;

    /** 技能包全部文本的字节数上限 */
    private long maxPackageBytes = 1048576;
}
