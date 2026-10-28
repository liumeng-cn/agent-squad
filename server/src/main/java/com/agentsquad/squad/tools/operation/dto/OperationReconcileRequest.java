package com.agentsquad.squad.tools.operation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 有版本和证据约束的操作核对请求。
 *
 * 只能确认已成功、未执行或继续未知；证据必须引用当前用户可读取的产物。
 */
public record OperationReconcileRequest(@NotNull Long expectedVersion,
        @NotBlank @Size(max = 40) String disposition,
        @NotNull @Size(min = 1, max = 20) List<String> evidenceRefs,
        @NotBlank @Size(max = 2000) String note) {
}
