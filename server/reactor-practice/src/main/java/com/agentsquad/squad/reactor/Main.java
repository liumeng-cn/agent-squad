package com.agentsquad.squad.reactor;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactor 练习入口，按主题依次运行各个练习类
 * 所属模块：reactor-practice，纯本地练习，不参与业务部署
 */
public class Main {

    public static void main(String[] args) {
        Flux.just("A", "B", "C")
                .subscribe(
                        value -> System.out.println("收到：" + value),
                        error -> System.out.println("异常：" + error),
                        () -> System.out.println("处理完成")
                );
    }

    public static class MonoFluxDemo {

        public static void main(String[] args) {

            Mono<String> mono = Mono.just("Hello");
            Flux<Integer> flux = Flux.range(1, 3);
            System.out.println("Mono 类型：" + mono.getClass());
            System.out.println("Flux 类型：" + flux.getClass());
        }
    }

    public static class LazyDemo {

        public static void main(String[] args) {


            Mono<Mono<Integer>> source = Mono.just(Mono.just(10));

            Mono<Mono<String>> a = source.map(value -> Mono.just("Hello"));

            Mono<String> b = source.flatMap(value -> Mono.just("Hello"));

                    a.subscribe();
                    b.subscribe();
        }
    }
}