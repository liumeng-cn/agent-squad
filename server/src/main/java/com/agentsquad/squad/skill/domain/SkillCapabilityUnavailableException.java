package com.agentsquad.squad.skill.domain;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;

/**
 * 技能依赖能力不可用。
 * <p>
 * 只表示当前执行未被授予所需工具，不触发自动安装、授权或更换实现扩大权限。
 */
public class SkillCapabilityUnavailableException extends RuntimeBusinessException {
    public SkillCapabilityUnavailableException() {
        super(RuntimeExceptionCode.CONFLICT, "CAPABILITY_UNAVAILABLE：当前执行没有技能所需的工具权限");
    }
}
