package com.agentsquad.squad.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 有版本和证据约束的操作核对请求。
 * <p>
 * 只能确认已成功、未执行或继续未知；证据必须引用当前用户可读取的产物。
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class OperationReconcileDTO {

    @com.fasterxml.jackson.annotation.JsonProperty("expectedVersion")
    @NotNull
    private final Long expectedVersion;

    @com.fasterxml.jackson.annotation.JsonProperty("disposition")
    @NotBlank
    @Size(max = 40)
    private final String disposition;

    @com.fasterxml.jackson.annotation.JsonProperty("evidenceRefs")
    @NotNull
    @Size(min = 1, max = 20)
    private final List<String> evidenceRefs;

    @com.fasterxml.jackson.annotation.JsonProperty("note")
    @NotBlank
    @Size(max = 2000)
    private final String note;

    @com.fasterxml.jackson.annotation.JsonCreator
    public OperationReconcileDTO(
            @com.fasterxml.jackson.annotation.JsonProperty("expectedVersion") Long expectedVersion,
            @com.fasterxml.jackson.annotation.JsonProperty("disposition") String disposition,
            @com.fasterxml.jackson.annotation.JsonProperty("evidenceRefs") List<String> evidenceRefs,
            @com.fasterxml.jackson.annotation.JsonProperty("note") String note) {
        this.expectedVersion = expectedVersion;
        this.disposition = disposition;
        this.evidenceRefs = evidenceRefs;
        this.note = note;
    }
}
