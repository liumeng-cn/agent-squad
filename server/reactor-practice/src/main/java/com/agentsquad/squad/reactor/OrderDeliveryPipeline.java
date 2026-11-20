package com.agentsquad.squad.reactor;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public class OrderDeliveryPipeline {

    public static void main(String[] args) {

        var orderRepository = new OrderRepository();
        var customerRepository = new CustomerRepository();
        var inventoryRepository = new InventoryRepository();

        var pricingService = new PricingService();
        var riskService = new RiskService();

        System.out.println("===== 组装执行链 =====");

        Mono<DeliveryCheckResult> result = Mono.just(10001L)

                // 1. 根据订单ID查询订单
                .flatMap(orderRepository::findById)

                // 2. 查询客户，再查询库存，组装上下文
                .flatMap(order ->
                        customerRepository.findById(order.customerId())
                                .flatMap(customer ->
                                        inventoryRepository.findBySku(order.sku())
                                                .map(stock ->
                                                        new OrderContext(
                                                                order,
                                                                customer,
                                                                stock,
                                                                BigDecimal.ZERO
                                                        )
                                                )
                                )
                )

                // 3. 计算优惠金额，更新业务上下文
                .map(context ->
                        context.withPayableAmount(
                                pricingService.calculate(context)
                        )
                )

                // 4. 调用风控服务
                .flatMap(context ->
                        riskService.evaluate(context)
                                .map(risk ->
                                        DeliveryCheckResult.from(context, risk)
                                )
                );

        System.out.println("执行链组装完成");

        // 5. 订阅
        System.out.println("\n===== 开始订阅 =====");

        result.subscribe(
                response -> System.out.println("[最终结果] " + response),
                error -> System.err.println("[异常] " + error.getMessage()),
                () -> System.out.println("[完成] 交付预检结束")
        );
    }

    // =========================
    // Repository
    // =========================

    static class OrderRepository {

        public Mono<Order> findById(Long orderId) {

            System.out.println("[OrderRepository] 创建查询 Publisher");

            return Mono.fromSupplier(() -> {
                System.out.println("[OrderRepository] 执行订单查询");

                return new Order(
                        orderId,
                        "ORD-10001",
                        2001L,
                        "SUV-001",
                        2,
                        new BigDecimal("1200")
                );
            });
        }
    }

    static class CustomerRepository {

        public Mono<Customer> findById(Long customerId) {

            System.out.println("[CustomerRepository] 创建查询 Publisher");

            return Mono.fromSupplier(() -> {
                System.out.println("[CustomerRepository] 执行客户查询");

                return new Customer(customerId, "张三", 2);
            });
        }
    }

    static class InventoryRepository {

        public Mono<Stock> findBySku(String sku) {

            System.out.println("[InventoryRepository] 创建查询 Publisher");

            return Mono.fromSupplier(() -> {
                System.out.println("[InventoryRepository] 执行库存查询");

                return new Stock(sku, 8);
            });
        }
    }

    // =========================
    // Service
    // =========================

    static class PricingService {

        public BigDecimal calculate(OrderContext context) {

            System.out.println("[PricingService] 计算订单金额");

            BigDecimal total = context.order().unitPrice()
                    .multiply(BigDecimal.valueOf(
                            context.order().quantity()
                    ));

            // VIP客户享受九折
            BigDecimal discount = context.customer().level() >= 2
                    ? new BigDecimal("0.90")
                    : BigDecimal.ONE;

            return total.multiply(discount);
        }
    }

    static class RiskService {

        public Mono<RiskResult> evaluate(OrderContext context) {

            System.out.println("[RiskService] 创建风控 Publisher");

            return Mono.fromSupplier(() -> {

                System.out.println("[RiskService] 执行风控评估");

                if (context.stock().available()
                        < context.order().quantity()) {

                    return new RiskResult(
                            "HIGH",
                            "库存不足"
                    );
                }

                if (context.payableAmount()
                        .compareTo(new BigDecimal("10000")) > 0) {

                    return new RiskResult(
                            "MEDIUM",
                            "订单金额较高"
                    );
                }

                return new RiskResult(
                        "LOW",
                        "风险正常"
                );
            });
        }
    }

    // =========================
    // 业务对象
    // =========================

    record Order(
            Long id,
            String orderNo,
            Long customerId,
            String sku,
            int quantity,
            BigDecimal unitPrice
    ) {
    }

    record Customer(
            Long id,
            String name,
            int level
    ) {
    }

    record Stock(
            String sku,
            int available
    ) {
    }

    record OrderContext(
            Order order,
            Customer customer,
            Stock stock,
            BigDecimal payableAmount
    ) {

        public OrderContext withPayableAmount(BigDecimal amount) {
            return new OrderContext(
                    order,
                    customer,
                    stock,
                    amount
            );
        }
    }

    record RiskResult(
            String level,
            String reason
    ) {
    }

    record DeliveryCheckResult(
            String orderNo,
            String customerName,
            String sku,
            int quantity,
            BigDecimal payableAmount,
            int availableStock,
            String riskLevel,
            String riskReason
    ) {

        static DeliveryCheckResult from(
                OrderContext context,
                RiskResult risk
        ) {
            return new DeliveryCheckResult(
                    context.order().orderNo(),
                    context.customer().name(),
                    context.order().sku(),
                    context.order().quantity(),
                    context.payableAmount(),
                    context.stock().available(),
                    risk.level(),
                    risk.reason()
            );
        }
    }
}
