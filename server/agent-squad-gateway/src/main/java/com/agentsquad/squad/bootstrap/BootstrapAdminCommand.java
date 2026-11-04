package com.agentsquad.squad.bootstrap;

import com.agentsquad.squad.gateway.admin.UserAccountService;
import com.agentsquad.squad.common.exception.StateConflictException;
import com.agentsquad.squad.common.exception.ValidationFailedException;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 一次性初始管理员命令。
 *
 * <p>从当前环境配置读取账号与初始密码，且要求用户表为空；应用启动不会自动调用。
 * 命令不打印密码，失败只输出可理解原因；创建后须首次登录改密。</p>
 */
public final class BootstrapAdminCommand {

    private static final String USERNAME_PROPERTY = "agentsquad.auth.bootstrap.username";
    private static final String PASSWORD_PROPERTY = "agentsquad.auth.bootstrap.password";

    private BootstrapAdminCommand() {
    }

    public static void main(String[] args) {
        int result = initialize(args);
        if (result != 0) {
            System.exit(result);
        }
    }

    /** 加载独立配置后执行一次性初始化，配置缺失或创建失败均返回明确退出码。 */
    private static int initialize(String[] args) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(BootstrapAdminApplication.class)
                .web(WebApplicationType.NONE).run(args)) {
            String username = context.getEnvironment().getProperty(USERNAME_PROPERTY);
            String password = context.getEnvironment().getProperty(PASSWORD_PROPERTY);
            if (username == null || username.isBlank() || password == null || password.isBlank()) {
                System.err.println("请在配置文件中填写 " + USERNAME_PROPERTY + " 与 " + PASSWORD_PROPERTY
                        + " 后执行 bootstrap-admin；密码不会写入日志");
                return 2;
            }
            String userId = context.getBean(UserAccountService.class).bootstrapAdmin(username, password);
            System.out.println("初始管理员已创建，请使用临时密码登录并立即修改；userId=" + userId);
            return 0;
        } catch (Exception exception) {
            System.err.println("初始管理员创建失败：" + safeMessage(exception));
            return 1;
        }
    }

    private static String safeMessage(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof StateConflictException || current instanceof ValidationFailedException) {
                return current.getMessage();
            }
        }
        return "无法完成初始化，请检查数据库连接与账号状态";
    }
}
