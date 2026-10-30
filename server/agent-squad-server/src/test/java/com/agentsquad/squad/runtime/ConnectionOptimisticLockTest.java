package com.agentsquad.squad.runtime;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.identity.AuthView;
import com.agentsquad.squad.tools.connection.ConnectionService;
import com.agentsquad.squad.tools.dto.ConnectionCreateRequest;
import com.agentsquad.squad.tools.dto.ConnectionUpdateRequest;
import com.agentsquad.squad.tools.repository.ConnectionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import com.agentsquad.squad.tools.connection.LocalSecretVault;

import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 连接配置的真实数据库乐观锁验证。
 * <p>
 * 覆盖服务事务与 XML 版本条件，确保过期写入不会覆盖新版本。
 */
class ConnectionOptimisticLockTest {
    @Test
    void staleRevisionCannotOverwriteCommittedConnection(@TempDir Path directory) {
        String owner = UUID.randomUUID().toString();
        try (var context = MessageAcceptanceHttpTest.start(com.agentsquad.squad.testsupport.MySqlTestDatabase.url(directory),
                com.agentsquad.squad.testsupport.MySqlTestDatabase.username(),
                com.agentsquad.squad.testsupport.MySqlTestDatabase.password(), owner)) {
            byte[] key = new byte[32];
            new java.security.SecureRandom().nextBytes(key);
            ReflectionTestUtils.setField(context.getBean(LocalSecretVault.class), "masterKeyBase64",
                    Base64.getEncoder().encodeToString(key));
            ConnectionService service = context.getBean(ConnectionService.class);
            ConnectionMapper mapper = context.getBean(ConnectionMapper.class);
            AuthView actor = new AuthView(owner, "tester", List.of("USER"), null);
            var created = service.create(actor, "create", new ConnectionCreateRequest(
                    "隔离连接", "mock", "test", Map.of("scope", "test"), "test-only-secret"));
            String id = created.connectionId();
            assertThat(mapper.findOwned(owner, id).revision()).isZero();

            service.update(actor, id, "first", new ConnectionUpdateRequest(0L, false, null, null));
            assertThat(mapper.findOwned(owner, id).revision()).isEqualTo(1L);
            assertThatThrownBy(() -> service.update(actor, id, "stale",
                    new ConnectionUpdateRequest(0L, true, null, null)))
                    .isInstanceOf(RuntimeBusinessException.class);
            assertThat(mapper.updateOwned(owner, id, 0L, null, true, "stale-writer")).isZero();
            assertThat(mapper.findOwned(owner, id).revision()).isEqualTo(1L);
            assertThat(mapper.findOwned(owner, id).enabled()).isFalse();
        }
    }
}
