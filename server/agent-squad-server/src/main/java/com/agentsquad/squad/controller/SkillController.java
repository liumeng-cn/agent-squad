package com.agentsquad.squad.controller;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.identity.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import com.agentsquad.squad.model.vo.SkillDetailVO;
import com.agentsquad.squad.model.dto.SkillGrantDTO;
import com.agentsquad.squad.model.vo.SkillGrantVO;
import com.agentsquad.squad.model.dto.SkillImportDTO;
import com.agentsquad.squad.model.vo.SkillPageVO;
import com.agentsquad.squad.model.dto.SkillRevokeDTO;
import com.agentsquad.squad.model.vo.SkillVersionVO;
import com.agentsquad.squad.service.SkillService;
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
 * 当前用户的技能导入、查询与授权接口
 * <p>
 * 身份只来自服务端 Cookie；写请求验证 CSRF，业务事务与不可变规则交由技能服务。
 */
@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {

    @Resource
    private SkillService skills;

    @GetMapping
    public ApiResult<SkillPageVO> list(HttpServletRequest servletRequest,
                                     @RequestParam(required = false) String cursor,
                                     @RequestParam(defaultValue = "20") int limit) {
        return ApiResult.success(skills.list(UserContext.current(servletRequest).userId(), cursor, limit));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<SkillVersionVO> importPackage(HttpServletRequest servletRequest,
                                                     @RequestBody SkillImportDTO request) {
        return ApiResult.success(skills.importPackage(UserContext.current(servletRequest).userId(), request));
    }

    @GetMapping("/{versionId}")
    public ApiResult<SkillDetailVO> detail(HttpServletRequest servletRequest,
                                         @PathVariable String versionId) {
        return ApiResult.success(skills.detail(UserContext.current(servletRequest).userId(), versionId));
    }

    @PutMapping("/{versionId}/grant")
    public ApiResult<SkillGrantVO> grant(HttpServletRequest servletRequest,
                                           @PathVariable String versionId, @Valid @RequestBody SkillGrantDTO request) {
        return ApiResult.success(skills.changeGrant(UserContext.current(servletRequest).userId(), versionId, request));
    }

    @PostMapping("/{versionId}/revoke")
    public ApiResult<SkillDetailVO> revoke(HttpServletRequest servletRequest,
                                         @PathVariable String versionId, @Valid @RequestBody SkillRevokeDTO request) {
        return ApiResult.success(skills.revoke(UserContext.current(servletRequest).userId(), versionId,
                request.expectedVersion()));
    }
}
