package com.agentsquad.squad.skill.controller;

import com.agentsquad.squad.identity.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import com.agentsquad.squad.skill.dto.SkillDetail;
import com.agentsquad.squad.skill.dto.SkillGrantRequest;
import com.agentsquad.squad.skill.dto.SkillGrantView;
import com.agentsquad.squad.skill.dto.SkillImportRequest;
import com.agentsquad.squad.skill.dto.SkillPage;
import com.agentsquad.squad.skill.dto.SkillRevokeRequest;
import com.agentsquad.squad.skill.dto.SkillVersionView;
import com.agentsquad.squad.skill.service.SkillService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户的技能导入、查询与授权 HTTP 边界。
 * <p>
 * 身份只来自服务端 Cookie；写请求验证 CSRF，业务事务与不可变规则交由技能服务。
 */
@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {

    @Resource
    private SkillService skills;

    @GetMapping
    public SkillPage list(HttpServletRequest servletRequest,
                          @RequestParam(required = false) String cursor,
                          @RequestParam(defaultValue = "20") int limit) {
        return skills.list(UserContext.current(servletRequest).userId(), cursor, limit);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SkillVersionView importPackage(HttpServletRequest servletRequest,
                                          @RequestBody SkillImportRequest request) {
        return skills.importPackage(UserContext.current(servletRequest).userId(), request);
    }

    @GetMapping("/{versionId}")
    public SkillDetail detail(HttpServletRequest servletRequest,
                              @PathVariable String versionId) {
        return skills.detail(UserContext.current(servletRequest).userId(), versionId);
    }

    @PutMapping("/{versionId}/grant")
    public SkillGrantView grant(HttpServletRequest servletRequest,
                                @PathVariable String versionId, @Valid @RequestBody SkillGrantRequest request) {
        return skills.changeGrant(UserContext.current(servletRequest).userId(), versionId, request);
    }

    @PostMapping("/{versionId}/revoke")
    public SkillDetail revoke(HttpServletRequest servletRequest,
                              @PathVariable String versionId, @Valid @RequestBody SkillRevokeRequest request) {
        return skills.revoke(UserContext.current(servletRequest).userId(), versionId,
                request.expectedVersion());
    }
}
