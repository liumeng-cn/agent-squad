package com.agentsquad.squad.skill.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 技能包导入资源限制。
 * <p>
 * 仅控制文本导入上限，不赋予脚本、网络或宿主文件系统权限。
 */
@Component
@ConfigurationProperties(prefix = "agentsquad.skills")
@Getter
@Setter
public class SkillPackageLimits {

    private int maxFiles = 64;

    private int maxFileBytes = 262144;

    private long maxPackageBytes = 1048576;
}
