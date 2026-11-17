package com.agentsquad.squad.user.server.utils;

import com.agentsquad.squad.common.enums.AvatarPalette;
import com.agentsquad.squad.common.enums.AvatarStyle;
import com.agentsquad.squad.user.api.vo.AvatarVO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 稳定用户头像生成
 * <p>
 * user-server纯计算工具，不请求外部站点或存储图片
 */
public final class AvatarGenerator {
    private AvatarGenerator() {
    }

    /**
     * 只使用稳定业务ID发号，昵称和手机号变化不影响头像。
     */
    public static AvatarVO generate(String userId) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                    ("squad-avatar:v1:" + userId).getBytes(StandardCharsets.UTF_8));
            StringBuilder pattern = new StringBuilder(15);
            for (int bit = 0; bit < 15; bit++) {
                pattern.append((hash[bit / 8] >> (7 - bit % 8)) & 1);
            }
            if (pattern.indexOf("1") < 0) {
                pattern.setCharAt(7, '1');
            }
            AvatarVO avatar = new AvatarVO();
            avatar.setStyle(AvatarStyle.GEOMETRIC_V1);
            avatar.setPattern(pattern.toString());
            avatar.setPaletteKey(AvatarPalette.values()[hash[2] & 7]);
            return avatar;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("系统不支持头像摘要算法", exception);
        }
    }
}
