package com.agentsquad.squad.v2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 会话建档请求
 * <p>
 * v2接口只接收会话标识，用户与Agent由服务端确定
 */
@Data
public class ConvCreateDTO {
    /**
     * 新建会话标识；创建方生成UUID，重试保持相同
     */
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9-]{1,64}")
    private String convId;
}
