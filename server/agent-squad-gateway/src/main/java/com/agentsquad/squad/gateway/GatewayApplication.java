package com.agentsquad.squad.gateway;

import com.agentsquad.squad.mapper.AuthMapper;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Agent Squad 认证网关服务启动入口。
 *
 * <p>负责用户认证与会话管理，并将业务请求反向代理到业务服务；不持有业务数据。</p>
 */
@MapperScan(basePackageClasses = AuthMapper.class, annotationClass = Mapper.class)
@SpringBootApplication(scanBasePackages = "com.agentsquad.squad")
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
