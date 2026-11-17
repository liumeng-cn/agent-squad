package com.agentsquad.squad.testsupport;

/**
 * 本地Redis集成测试连接参数。
 * <p>
 * 与数据库夹具一致，只从测试环境变量读取凭据，不借用应用私有配置；
 * 端口缺省指向本地容器映射端口，便于直接运行跨实例测试。
 */
public final class TestRedis {

    private TestRedis() {
    }

    public static String host() {
        return System.getenv().getOrDefault("SQUAD_TEST_REDIS_HOST", "127.0.0.1");
    }

    public static int port() {
        return Integer.parseInt(System.getenv().getOrDefault("SQUAD_TEST_REDIS_PORT", "16379"));
    }

    public static String password() {
        String value = System.getenv("SQUAD_TEST_REDIS_PASSWORD");
        if (value == null) {
            throw new IllegalStateException("Redis集成测试需要SQUAD_TEST_REDIS_PASSWORD，不读取应用私有配置");
        }
        return value;
    }
}
