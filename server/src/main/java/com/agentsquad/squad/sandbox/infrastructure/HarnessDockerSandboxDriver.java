package com.agentsquad.squad.sandbox.infrastructure;

import com.agentsquad.squad.sandbox.domain.SandboxPolicy;
import com.agentsquad.squad.sandbox.dto.SandboxDriverResult;
import com.agentsquad.squad.sandbox.dto.SandboxExecuteRequest;
import com.agentsquad.squad.sandbox.dto.SandboxOutput;
import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import io.agentscope.harness.agent.sandbox.ExecResult;
import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxClient;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxClientOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 固定 AgentScope Docker 驱动适配。
 * <p>
 * 默认禁用，仅使用已有的不可变镜像；无宿主挂载、网络或 socket，执行结束销毁容器。
 */
@Component
public class HarnessDockerSandboxDriver {
    @Value("${agentsquad.sandbox.enabled:false}")
    private boolean enabled;

    @Value("${agentsquad.sandbox.image:}")
    private String image;

    public void requireAvailable() {
        if (!enabled || image == null || !image.matches("sha256:[0-9a-f]{64}|[^\s]+@sha256:[0-9a-f]{64}")) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "CAPABILITY_UNAVAILABLE：隔离沙箱尚未启用或未固定镜像摘要");
        }
    }

    /**
     * SDK 只在容器内解释命令；输入通过内容投影复制，无宿主路径可供脚本访问。
     * 超时或失败仍销毁整个容器，防止 docker exec 超时后遗留脚本进程。
     */
    public SandboxDriverResult execute(SandboxExecuteRequest request) throws Exception {
        return execute(request, request.files().stream().map(file -> new com.agentsquad.squad.sandbox.domain.SandboxInputFile(
                file.path(), file.content().getBytes(StandardCharsets.UTF_8))).toList());
    }

    /**
     * 仅将服务层已校验的字节投影到容器；SDK工作空间保持空，避免其文件物化逻辑写到宿主。
     */
    public SandboxDriverResult execute(SandboxExecuteRequest request,
                                       java.util.List<com.agentsquad.squad.sandbox.domain.SandboxInputFile> inputFiles) throws Exception {
        requireAvailable();
        SandboxPolicy.validate(request);
        WorkspaceSpec workspace = new WorkspaceSpec();
        workspace.setRoot("/workspace");
        DockerSandboxClientOptions options = new DockerSandboxClientOptions()
                .image(image).workspaceRoot("/workspace").memorySizeBytes(128L * 1024 * 1024)
                .cpuCount(1L).network("none").additionalRunArgs(
                        "--pull=never", "--read-only", "--cap-drop=ALL", "--security-opt=no-new-privileges",
                        "--pids-limit=64", "--memory-swap=134217728", "--user=65534:65534",
                        "--ulimit=nofile=64:64", "--ulimit=fsize=10485760:10485760",
                        "--tmpfs=/workspace:rw,noexec,nosuid,nodev,size=67108864,mode=1777",
                        "--tmpfs=/tmp:rw,noexec,nosuid,nodev,size=16777216,mode=1777");
        Sandbox sandbox = new DockerSandboxClient().create(workspace, null, options);
        try {
            sandbox.start();
            for (var file : inputFiles) {
                writeInput(sandbox, file.path(), file.content());
            }
            writeInput(sandbox, "__agentsquad_script__.py", request.script().getBytes(StandardCharsets.UTF_8));
            ExecResult result = sandbox.exec(null, "python -I -c " + quote(supervisor(request.timeoutSeconds())),
                    request.timeoutSeconds() + 5);
            int exitCode = Integer.parseInt(result.stdout().trim());
            String stdout = boundedLog(sandbox, "__agentsquad_stdout__");
            String stderr = boundedLog(sandbox, "__agentsquad_stderr__");
            List<SandboxOutput> outputs = new ArrayList<>();
            if (exitCode == 0) {
                for (String path : request.outputPaths()) {
                    outputs.add(new SandboxOutput(path, readOutput(sandbox, path)));
                }
            }
            return new SandboxDriverResult(exitCode, stdout, stderr,
                    result.truncated() || stdout.getBytes(StandardCharsets.UTF_8).length >= SandboxPolicy.MAX_LOG_BYTES
                            || stderr.getBytes(StandardCharsets.UTF_8).length >= SandboxPolicy.MAX_LOG_BYTES, List.copyOf(outputs));
        } finally {
            sandbox.shutdown();
        }
    }

    private void writeInput(Sandbox sandbox, String path, byte[] bytes) throws Exception {
        String encodedPath = Base64.getEncoder().encodeToString(path.getBytes(StandardCharsets.UTF_8));
        for (int offset = 0; offset < Math.max(1, bytes.length); offset += 32768) {
            byte[] chunk = java.util.Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + 32768));
            String encoded = Base64.getEncoder().encodeToString(chunk);
            String script = "import base64,pathlib; p=pathlib.Path('/workspace')/base64.b64decode('" + encodedPath
                    + "').decode(); p.parent.mkdir(parents=True,exist_ok=True); "
                    + "f=p.open('" + (offset == 0 ? "wb" : "ab") + "'); f.write(base64.b64decode('" + encoded + "')); f.close()";
            sandbox.exec(null, "python -I -c " + quote(script), 5);
        }
    }

    /**
     * 监督进程由调用参数直接传入，用户无法改写其文件；清除所有新增命名空间进程后再读取文件。
     * 不只杀进程组，双重fork或setsid后的后代同样被发现；无法静默工作区即拒绝提取。
     */
    private String supervisor(int timeoutSeconds) {
        return """
                import os, signal, subprocess, sys, time, stat
                def live_pids():
                    result=set()
                    for name in os.listdir('/proc'):
                        if not name.isdigit():
                            continue
                        try:
                            value=open('/proc/'+name+'/stat').read().rsplit(')',1)[1].split()
                            if value[0] not in ('Z','X'):
                                result.add(int(name))
                        except (OSError,IndexError):
                            pass
                    return result
                baseline=live_pids()
                outfd=os.open('/workspace/__agentsquad_stdout__', os.O_WRONLY|os.O_CREAT|os.O_TRUNC|os.O_NOFOLLOW, 0o600)
                errfd=os.open('/workspace/__agentsquad_stderr__', os.O_WRONLY|os.O_CREAT|os.O_TRUNC|os.O_NOFOLLOW, 0o600)
                try:
                    process=subprocess.Popen([sys.executable,'-I','/workspace/__agentsquad_script__.py'],
                        stdout=outfd,stderr=errfd,start_new_session=True)
                    timed_out=False
                    try:
                        code=process.wait(timeout=TIMEOUT_SECONDS)
                    except subprocess.TimeoutExpired:
                        timed_out=True
                        code=124
                    deadline=time.monotonic()+2
                    while True:
                        others=live_pids()-baseline
                        if not others:
                            break
                        for pid in others:
                            try:
                                os.kill(pid,signal.SIGSTOP)
                            except ProcessLookupError:
                                pass
                        for pid in others:
                            try:
                                os.kill(pid,signal.SIGKILL)
                            except ProcessLookupError:
                                pass
                        try:
                            process.wait(timeout=0.05)
                        except subprocess.TimeoutExpired:
                            pass
                        if time.monotonic()>=deadline:
                            raise RuntimeError('sandbox workspace did not quiesce')
                        time.sleep(0.01)
                    if timed_out:
                        raise RuntimeError('sandbox script execution timed out')
                    print(code)
                finally:
                    os.close(outfd)
                    os.close(errfd)
                """.replace("TIMEOUT_SECONDS", Integer.toString(timeoutSeconds));
    }

    private String boundedLog(Sandbox sandbox, String path) throws Exception {
        String command = "python -I -c " + quote("import os,stat,sys; "
                + "fd=os.open('/workspace/" + path + "',os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK); "
                + "assert stat.S_ISREG(os.fstat(fd).st_mode); "
                + "sys.stdout.buffer.write(os.read(fd,65536)); os.close(fd)");
        return sandbox.exec(null, command, 5).stdout();
    }

    private byte[] readOutput(Sandbox sandbox, String path) throws Exception {
        String encoded = Base64.getEncoder().encodeToString(path.getBytes(StandardCharsets.UTF_8));
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        for (int offset = 0; offset <= SandboxPolicy.MAX_OUTPUT_BYTES; offset += 131072) {
            String script = "import os,base64,stat; p=base64.b64decode('" + encoded + "').decode(); "
                    + "parts=p.split('/'); fd=os.open('/workspace', os.O_RDONLY|os.O_DIRECTORY); "
                    + "\nfor part in parts[:-1]: old=fd; fd=os.open(part,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW,dir_fd=old); os.close(old)"
                    + "\nold=fd; fd=os.open(parts[-1],os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK,dir_fd=old); os.close(old); s=os.fstat(fd); "
                    + "assert stat.S_ISREG(s.st_mode) and s.st_size<=1048576; "
                    + "os.lseek(fd," + offset + ",0); data=os.read(fd,131072); "
                    + "print(base64.b64encode(data).decode())";
            ExecResult result = sandbox.exec(null, "python -I -c " + quote(script), 5);
            if (result.truncated()) {
                throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "沙箱输出超过读取上限");
            }
            byte[] part = Base64.getDecoder().decode(result.stdout().trim());
            bytes.write(part);
            if (part.length < 131072) {
                return bytes.toByteArray();
            }
        }
        throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "沙箱输出超过读取上限");
    }

    private String quote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
