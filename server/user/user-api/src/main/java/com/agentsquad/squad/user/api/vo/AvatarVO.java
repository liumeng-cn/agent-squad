package com.agentsquad.squad.user.api.vo;

import com.agentsquad.squad.common.enums.AvatarPalette;
import com.agentsquad.squad.common.enums.AvatarStyle;
import lombok.Data;

/**
 * 用户几何头像定义
 * <p>
 * 仅作为跨服务传输模型，稳定生成算法由user-server负责
 */
@Data
public class AvatarVO {
    /**
     * 固定版本生成风格
     */
    private AvatarStyle style;
    /**
     * 五行三列图案位，前端按列镜像展开
     */
    private String pattern;
    /**
     * 静态配色标识，不接收任意颜色和图片URL
     */
    private AvatarPalette paletteKey;
}
