package com.agentsquad.squad.reactor;

import reactor.core.publisher.Mono;

import java.util.function.Consumer;
import java.util.function.Function;

public class MapFlatMapDemo {

    // 用户实体
    static class User {
        private Long id;
        private String name;

        public User(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return "User{id=" + id + ", name='" + name + "'}";
        }
    }

    // 普通方法：返回 String
    public static String getName(Long userId) {
        System.out.println("执行 getName()");
        return "用户-" + userId;
    }

    // Reactor 方法：返回 Mono<User>
    public static Mono<User> queryUser(Long userId) {

        System.out.println("调用 queryUser()");

        return Mono.fromSupplier(() -> {
            System.out.println("真正执行用户查询");
            return new User(userId, "张三");
        });
    }

    public static void main(String[] args) {

        Mono<Long> source = Mono.just(100L);

        // ==================================
        // 1. map：转换成普通数据
        // ==================================

        System.out.println("===== map =====");

        Mono<String> nameResult = source.map(MapFlatMapDemo::getName);

        nameResult.subscribe(
                name -> System.out.println("最终结果：" + name)
        );

        // ==================================
        // 2. flatMap：转换成 Mono
        // ==================================

        System.out.println("\n===== flatMap =====");

        Mono<User> userResult = source.flatMap(
                new Function<Long, Mono<User>>() {
                    @Override
                    public Mono<User> apply(Long userId) {
                        return queryUser(userId);
                    }
                }
        );

        userResult.subscribe(
                new Consumer<User>() {
                    @Override
                    public void accept(User user) {
                        System.out.println("最终结果：" + user);
                    }
                }
        );

        // ==================================
        // 3. map：返回 Mono，观察嵌套
        // ==================================

        System.out.println("\n===== map 返回 Mono =====");

        Mono<Mono<User>> nestedResult = source.map(
                MapFlatMapDemo::queryUser
        );

        nestedResult.subscribe(
                inner -> {
                    System.out.println("外层收到一个 Mono 对象");
                    // 注意：这里没有订阅 inner
                }
        );
    }
}
