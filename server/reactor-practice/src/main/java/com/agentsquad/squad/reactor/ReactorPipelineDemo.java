package com.agentsquad.squad.reactor;

import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

public class ReactorPipelineDemo {

    public static void main(String[] args) {

        UserRepository userRepository = new UserRepository();
        RoleRepository roleRepository = new RoleRepository();

        // 1. 用户 ID
        Mono<Long> userIdPublisher = Mono.just(100L);

        // 2. 查询用户
        Mono<User> userPublisher = userIdPublisher.flatMap(userRepository::findById);

        // 3. 查询角色，组装用户资料
        Mono<UserProfile> profilePublisher = userPublisher
                .flatMap(user ->
                        roleRepository.findRoles(user.getId())
                                .map(roles -> new UserProfile(user, roles))
                );

        // 4. 转换展示结果
        Mono<String> resultPublisher = profilePublisher
                .map(UserProfile::toDisplayText);

        // 5. 订阅
        resultPublisher.subscribe(
                result -> System.out.println("[结果] " + result),
                error -> System.err.println("[异常] " + error.getMessage()),
                () -> System.out.println("[完成] 数据流结束")
        );
    }

    static class UserRepository {

        public Mono<User> findById(Long userId) {
            System.out.println("[UserRepository] 创建查询 Publisher");

            return Mono.fromSupplier(() -> {
                System.out.println("[UserRepository] 执行用户查询");
                return new User(userId, "张三", 28);
            });
        }
    }

    static class RoleRepository {

        public Mono<List<String>> findRoles(Long userId) {
            System.out.println("[RoleRepository] 创建查询 Publisher");

            return Mono.fromSupplier(() -> {
                System.out.println("[RoleRepository] 执行角色查询");
                return Arrays.asList("ADMIN", "REVIEWER");
            });
        }
    }

    static class User {

        private final Long id;
        private final String name;
        private final Integer age;

        public User(Long id, String name, Integer age) {
            this.id = id;
            this.name = name;
            this.age = age;
        }

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public Integer getAge() {
            return age;
        }
    }

    static class UserProfile {

        private final User user;
        private final List<String> roles;

        public UserProfile(User user, List<String> roles) {
            this.user = user;
            this.roles = roles;
        }

        public String toDisplayText() {
            return "用户：" + user.getName()
                    + "，年龄：" + user.getAge()
                    + "，角色：" + roles;
        }
    }
}
