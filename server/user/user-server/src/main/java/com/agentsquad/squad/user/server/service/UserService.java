package com.agentsquad.squad.user.server.service;

import com.agentsquad.squad.common.enums.ApiCode;
import com.agentsquad.squad.common.enums.UserStatus;
import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.user.api.vo.UserAccountVO;
import com.agentsquad.squad.user.server.config.properties.UserProperties;
import com.agentsquad.squad.user.server.converter.UserConverter;
import com.agentsquad.squad.user.server.dto.UserRegisterDTO;
import com.agentsquad.squad.user.server.dto.UserUpdateDTO;
import com.agentsquad.squad.user.server.mapper.UserMapper;
import com.agentsquad.squad.user.server.po.UserPO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户注册与资料业务
 * <p>
 * 所属user/user-server模块，按明确职责维护用户身份与数据边界。
 */
@Slf4j
@Service
public class UserService {
    @Resource
    private UserMapper userMapper;
    @Resource
    private UserConverter converter;
    @Resource
    private UserProperties properties;

    /**
     * 注册只提交用户事务；不创建登录态，重复手机号由唯一约束裁决。
     */
    @Transactional
    public UserAccountVO register(UserRegisterDTO request) {
        if (!properties.getVerificationCode().equals(request.getVerificationCode())) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "验证码错误");
        }
        UserPO user = new UserPO();
        user.setUserId(userMapper.nextUserId());
        user.setUsername("user_" + user.getUserId());
        user.setPhone(request.getPhone());
        user.setDisplayName("");
        user.setStatus(UserStatus.ACTIVE);
        user.setAuthVersion(0L);
        user.setVersion(0L);
        user.setDelFlag("0");
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ApiCode.CONFLICT, "该手机号已注册，请登录");
        }
        log.info("用户注册完成 userId={}", user.getUserId());
        return converter.toAccount(user);
    }

    public UserAccountVO byPhone(String phone) {
        return account(userMapper.selectOne(Wrappers.<UserPO>lambdaQuery()
                .eq(UserPO::getPhone, phone).eq(UserPO::getDelFlag, "0")));
    }

    public UserAccountVO byId(String userId) {
        if (userId == null || !userId.matches("[1-9][0-9]{0,18}")) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "用户标识无效");
        }
        return account(userMapper.selectOne(Wrappers.<UserPO>lambdaQuery()
                .eq(UserPO::getUserId, Long.valueOf(userId)).eq(UserPO::getDelFlag, "0")));
    }

    /**
     * 昵称条件更新，旧版本拒绝覆盖，事务内返回最新资料。
     */
    @Transactional
    public UserAccountVO update(String userId, UserUpdateDTO request) {
        if (userMapper.updateDisplayName(Long.valueOf(userId), request.getDisplayName().strip(), request.getVersion()) != 1) {
            throw new BusinessException(ApiCode.CONFLICT, "资料已变更，请刷新后重试");
        }
        log.info("用户昵称更新完成 userId={}", userId);
        return byId(userId);
    }

    private UserAccountVO account(UserPO user) {
        if (user == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "账号不存在，请先注册");
        }
        return converter.toAccount(user);
    }
}
