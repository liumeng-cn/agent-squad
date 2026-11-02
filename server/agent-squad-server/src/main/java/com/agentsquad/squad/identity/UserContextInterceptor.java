package com.agentsquad.squad.identity;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 业务服务的网关身份接入边界。
 *
 * <p>除 CORS 预检外，所有 `/api/**` 请求都必须携带可验证的网关签名断言；
 * 裸 `X-User-*` 不再作为主体来源，浏览器直连业务端口无法伪造身份。</p>
 */
@Component
public class UserContextInterceptor implements HandlerInterceptor {

    @Resource
    private AssertionVerifier assertionVerifier;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        request.setAttribute(UserContext.ATTRIBUTE, assertionVerifier.verify(request));
        return true;
    }
}
