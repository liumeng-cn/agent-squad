package com.agentsquad.squad.controller;

import com.agentsquad.squad.common.model.ApiResult;
import com.agentsquad.squad.model.vo.ModelOptionVO;
import com.agentsquad.squad.service.ModelCatalogService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 已登录用户的模型选择目录
 * <p>
 * 身份由统一拦截器校验，接口只返回可选择模型，不返回凭据、供应商地址或调用原始响应。
 */
@RestController
@RequestMapping("/api/v1/models")
public class ModelCatalogController {
    @Resource
    private ModelCatalogService catalog;

    @GetMapping
    public ApiResult<List<ModelOptionVO>> list() {
        return ApiResult.success(catalog.list());
    }
}
