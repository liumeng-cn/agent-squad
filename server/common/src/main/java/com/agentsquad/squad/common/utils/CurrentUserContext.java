package com.agentsquad.squad.common.utils;

import com.agentsquad.squad.common.bo.CurrentUserBO;
import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.exceptions.BusinessException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * 当前用户入口
 * <p>
 * MVC从当前请求获取用户，响应式流程显式传入请求属性；不使用可泄漏到线程池的自建ThreadLocal
 */
public final class CurrentUserContext {
    private CurrentUserContext() {
    }

    /**
     * 获取当前用户
     */
    public static CurrentUserBO getUser() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return require(attributes.getRequest().getAttribute(IdentityAssertion.ATTRIBUTE));
        }
        throw new BusinessException(ApiCode.UNAUTHORIZED, "缺少有效用户身份");
    }

    /**
     * 获取用户编号
     */
    public static String getUserId() {
        return getUser().getUserId();
    }

    /**
     * 获取异步用户
     */
    public static CurrentUserBO getUser(Map<String, Object> attributes) {
        return require(attributes.get(IdentityAssertion.ATTRIBUTE));
    }

    private static CurrentUserBO require(Object value) {
        if (value instanceof CurrentUserBO user && user.getUserId() != null) {
            return user;
        }
        throw new BusinessException(ApiCode.UNAUTHORIZED, "缺少有效用户身份");
    }
}
