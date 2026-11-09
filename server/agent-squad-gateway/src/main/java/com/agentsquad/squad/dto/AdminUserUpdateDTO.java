package com.agentsquad.squad.dto;

import com.agentsquad.squad.enums.AccountRole;
import com.agentsquad.squad.enums.AccountStatus;

/**
 * 管理员更新账号请求。
 *
 * <p>按 {@code expectedVersion} 做 CAS；为空的字段保持原值。</p>
 */
@lombok.Getter
@lombok.EqualsAndHashCode
@lombok.experimental.Accessors(fluent = true)
public class AdminUserUpdateDTO {

    @com.fasterxml.jackson.annotation.JsonProperty("expectedVersion")
    private final Long expectedVersion;

    @com.fasterxml.jackson.annotation.JsonProperty("displayName")
    private final String displayName;

    @com.fasterxml.jackson.annotation.JsonProperty("role")
    private final AccountRole role;

    @com.fasterxml.jackson.annotation.JsonProperty("status")
    private final AccountStatus status;

    @com.fasterxml.jackson.annotation.JsonCreator
    public AdminUserUpdateDTO(
            @com.fasterxml.jackson.annotation.JsonProperty("expectedVersion") Long expectedVersion,
            @com.fasterxml.jackson.annotation.JsonProperty("displayName") String displayName,
            @com.fasterxml.jackson.annotation.JsonProperty("role") AccountRole role,
            @com.fasterxml.jackson.annotation.JsonProperty("status") AccountStatus status) {
        this.expectedVersion = expectedVersion;
        this.displayName = displayName;
        this.role = role;
        this.status = status;
    }
}
