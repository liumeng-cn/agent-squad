package com.agentsquad.squad.v2.identity;

import com.agentsquad.squad.v2.config.properties.SquadV2ExecutionProperties;
import com.agentsquad.squad.v2.utils.SquadIdUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/** V2 用户身份解析器；生产环境只信任网关注入的内部用户 ID。 */
@Component
public class SquadUserIdentityResolver {
    @Resource private Environment environment;
    @Resource private SquadV2ExecutionProperties properties;
    public Long resolve(HttpServletRequest request) {
        String value = request.getHeader("X-User-Id");
        if (StringUtils.hasText(value)) {
            try { return SquadIdUtils.parsePositiveLong(value); }
            catch (RuntimeException invalid) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户身份无效");
            }
        }
        if (environment.acceptsProfiles(Profiles.of("local"))) return properties.getLocalUserId();
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少网关用户上下文");
    }
}