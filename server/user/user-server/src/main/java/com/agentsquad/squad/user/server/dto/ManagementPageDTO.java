package com.agentsquad.squad.user.server.dto;

import lombok.Data;
import jakarta.validation.constraints.Size;
import com.agentsquad.squad.common.enums.ManagementUserStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/**
 * 管理分页条件
 * <p>
 * 所属用户平台管理模块，限定于管理身份与授权，不改变普通聊天账号归属。
 */
@Data
public class ManagementPageDTO {
    /**
     * 名称编码或手机号关键字
     */
    @Size(max = 100)
    private String keyword;

    /**
     * 筛选状态
     */
    private ManagementUserStatus status;

    /**
     * 页码
     */
    @Min(1)
    private int current = 1;

    /**
     * 每页条数
     */
    @Min(1)
    @Max(100)
    private int size = 10;
}
