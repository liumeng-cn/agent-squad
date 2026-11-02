package com.agentsquad.squad.bootstrap;

import com.agentsquad.squad.gateway.admin.UserAccountService;
import com.agentsquad.squad.gateway.exception.StateConflictException;
import com.agentsquad.squad.gateway.exception.ValidationFailedException;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 一次性初始管理员命令。
 *
 * <p>仅通过环境变量传入账号与初始密码，且要求用户表为空；应用启动不会自动调用。
 * 命令不打印密码，失败只输出可理解原因；创建后须首次登录改密。</p>
 */
public final class BootstrapAdminCommand {

    private static final String USERNAME_ENV = "SQUAD_BOOTSTRAP_ADMIN_USERNAME";
    private static final String PASSWORD_ENV = "SQUAD_BOOTSTRAP_ADMIN_PASSWORD";

    private BootstrapAdminCommand() {
    }

    public static void main(String[] args) {
        String username = System.getenv(USERNAME_ENV);
        String password = System.getenv(PASSWORD_ENV);
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            System.err.println("用法：设置 " + USERNAME_ENV + " 与 " + PASSWORD_ENV
                    + " 后执行 bootstrap-admin；密码不会写入日志");
            System.exit(2);
            return;
        }
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(BootstrapAdminApplication.class)
                .web(WebApplicationType.NONE).run()) {
            String userId = context.getBean(UserAccountService.class).bootstrapAdmin(username, password);
            System.out.println("初始管理员已创建，请使用临时密码登录并立即修改；userId=" + userId);
        } catch (Exception exception) {
            System.err.println("初始管理员创建失败：" + safeMessage(exception));
            System.exit(1);
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
