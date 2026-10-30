package com.agentsquad.squad.sandbox;

import com.agentsquad.squad.sandbox.domain.SandboxPolicy;
import com.agentsquad.squad.sandbox.dto.SandboxExecuteRequest;
import com.agentsquad.squad.sandbox.dto.SandboxFile;
import com.agentsquad.squad.artifact.domain.ArtifactContentPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 受控沙箱输入与交付文件的纯规则验证。
 * <p>
 * 覆盖路径、资源总量、重复输入和二进制伪装，不依赖框架或数据库。
 */
class SandboxPolicyTest {
    @Test
    void traversalAbsoluteWindowsAndControlPathsAreRejected() {
        for (String path : List.of("../x", "/etc/passwd", "a/../../x", "a\\x", "C:/x", "a//x", "a\n.txt")) {
            assertThatThrownBy(() -> SandboxPolicy.requirePath(path)).hasMessageContaining("相对路径");
        }
        SandboxPolicy.requirePath("reports/result.txt");
    }

    @Test
    void duplicateFilesAndUnboundedTimeoutAreRejected() {
        var files = List.of(new SandboxFile("x.txt", "one"), new SandboxFile("x.txt", "two"));
        assertThatThrownBy(() -> SandboxPolicy.validate(new SandboxExecuteRequest("key", "print(1)", files,
                List.of(), 5))).hasMessageContaining("重复");
        assertThatThrownBy(() -> SandboxPolicy.validate(new SandboxExecuteRequest("key", "print(1)", List.of(),
                List.of(), 61))).hasMessageContaining("时限");
    }

    @Test
    void forgedBinaryAndNonUtf8CannotPublish() {
        assertThatThrownBy(() -> ArtifactContentPolicy.mediaType("x.png", "fake".getBytes())).hasMessageContaining("PNG");
        assertThatThrownBy(() -> ArtifactContentPolicy.mediaType("x.txt", new byte[]{(byte) 0xff}))
                .hasMessageContaining("UTF-8");
        assertThat(ArtifactContentPolicy.mediaType("result.pdf", "%PDF-1.7\n".getBytes())).isEqualTo("application/pdf");
    }
}
