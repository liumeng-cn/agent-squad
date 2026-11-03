package com.agentsquad.squad.gateway.auth;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

/**
 * 一次性受控初始化管理员。默认绝不运行；只有显式启用 bootstrap 和提供强密码时执行。
 * 必须在可信本地/运维环境运行，成功后撤销密码环境变量。
 */
@Component
@ConditionalOnProperty(prefix="agentsquad.auth.bootstrap",name="enabled",havingValue="true")
public class BootstrapAdminRunner implements CommandLineRunner {
    @Resource private JdbcTemplate jdbc;
    @Value("${agentsquad.auth.bootstrap.username:}") private String username;
    @Value("${agentsquad.auth.bootstrap.password:}") private String password;
    @Value("${agentsquad.auth.bootstrap.user-id:}") private String userId;

    @Override
    public void run(String... args) {
        String name=username.trim().toLowerCase(Locale.ROOT);
        if(!name.matches("[a-z0-9._-]{3,64}") || password.length()<20)
            throw new IllegalStateException("管理员初始化账号或密码无效");
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM sq_user",Long.class);
        if(total==null || total!=0L)
            throw new IllegalStateException("账户表非空，拒绝自动初始化或覆盖已有账号");
        String uid=userId.isBlank()?UUID.randomUUID().toString():UUID.fromString(userId).toString();
        jdbc.update("INSERT INTO sq_user(user_id,username,display_name,password_hash,role,status,"
                + "must_change_password,creator_id) VALUES(?,?,?,?,'ADMIN','ACTIVE',1,?)",
                uid,name,name,new BCryptPasswordEncoder(12).encode(password),uid);
        // 不输出明文密码或摘要，用户须在首次登录后修改密码。
    }
}
