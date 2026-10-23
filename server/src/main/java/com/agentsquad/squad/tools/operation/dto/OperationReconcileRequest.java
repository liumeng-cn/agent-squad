package com.agentsquad.squad.tools.operation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 操作核对请求；结论只能是人工可举证的三类，note 承载核对证据。
 */
public record OperationReconcileRequest(
        @NotBlank @Size(max = 40) String outcome,
        @NotBlank @Size(max = 500) String note) {
}
