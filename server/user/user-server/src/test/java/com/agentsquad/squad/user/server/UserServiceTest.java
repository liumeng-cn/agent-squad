package com.agentsquad.squad.user.server;

import com.agentsquad.squad.common.exceptions.BusinessException;
import com.agentsquad.squad.user.server.config.properties.UserProperties;
import com.agentsquad.squad.user.server.converter.UserConverter;
import com.agentsquad.squad.user.server.dto.UserRegisterDTO;
import com.agentsquad.squad.user.server.dto.UserUpdateDTO;
import com.agentsquad.squad.user.server.mapper.UserMapper;
import com.agentsquad.squad.user.server.po.UserPO;
import com.agentsquad.squad.user.server.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 用户注册与并发编辑测试
 * <p>
 * 验证无效验证码不写库及数据库冲突转换为明确业务拒绝。
 */
class UserServiceTest {
    @Test
    void rejectsInvalidCodeAndDuplicatePhoneAndStaleEdit() {
        UserMapper mapper = mock(UserMapper.class);
        UserService service = new UserService();
        ReflectionTestUtils.setField(service, "userMapper", mapper);
        ReflectionTestUtils.setField(service, "converter", mock(UserConverter.class));
        ReflectionTestUtils.setField(service, "properties", new UserProperties());
        UserRegisterDTO request = new UserRegisterDTO();
        request.setPhone("13800138000");
        request.setVerificationCode("bad");
        assertThrows(BusinessException.class, () -> service.register(request));
        verifyNoInteractions(mapper);
        request.setVerificationCode("8888");
        when(mapper.nextUserId()).thenReturn(100000L);
        when(mapper.insert(any(UserPO.class))).thenThrow(new DuplicateKeyException("duplicate"));
        assertThrows(BusinessException.class, () -> service.register(request));
        UserUpdateDTO update = new UserUpdateDTO();
        update.setDisplayName("昵称");
        update.setVersion(0L);
        assertThrows(BusinessException.class, () -> service.update("100000", update));
    }
}
