package com.agentsquad.squad.bo;

import com.agentsquad.squad.vo.AuthVO;

/**
 * 登录结果：原始会话令牌只返回给控制器写入 Cookie，服务端仅持久化摘要。
 */
public record LoginResultBO(String token, AuthVO view) {
}
