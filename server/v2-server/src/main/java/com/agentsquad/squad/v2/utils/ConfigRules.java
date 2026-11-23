package com.agentsquad.squad.v2.utils;

import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.common.utils.CurrentUserContext;

/**
 * 配置管理通用规则
 * <p>
 * 集中处理配置管理中重复出现的操作人、业务ID解析和条件更新结果判断，保证各配置服务的冲突语义一致。
 */
public final class ConfigRules {
    private ConfigRules() {
    }

    /**
     * 当前操作人ID
     */
    public static long actor() {
        return Long.parseLong(CurrentUserContext.getUserId());
    }

    /**
     * 解析业务ID
     * <p>
     * 接口以字符串传递雪花ID，格式无效时返回参数错误。
     */
    public static long parseId(String value, String label) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException | NullPointerException invalid) {
            throw new BusinessException(ApiCode.BAD_REQUEST, label + "无效");
        }
    }

    /**
     * 确认条件更新命中一行
     * <p>
     * 状态或修订号已被其他操作改变时返回冲突，不覆盖他人修改。
     */
    public static void requireChanged(int changedRows, String conflictMessage) {
        if (changedRows != 1) {
            throw new BusinessException(ApiCode.CONFLICT, conflictMessage);
        }
    }

    /**
     * 业务ID转字符串
     */
    public static String text(Long id) {
        return id == null ? null : id.toString();
    }
}
