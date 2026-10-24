package com.agentsquad.squad.tools.operation;

import com.agentsquad.squad.tools.operation.domain.OperationRow;
import com.agentsquad.squad.tools.operation.repository.OperationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 外部操作执行器事务边界测试。
 * <p>
 * 验证审批事务调用执行器时事务已暂停，外部调用前的账本写入能够独立提交。
 */
class OperationExecutorTransactionTest {

    @Test
    void shouldSuspendCallerTransactionBeforeExternalCall() {
        OperationMapper operationMapper = mock(OperationMapper.class);
        when(operationMapper.markInFlight(anyString(), anyString(), anyLong(), anyString(),
                anyLong(), any(LocalDateTime.class), anyString())).thenReturn(1);
        when(operationMapper.completeInFlight(anyString(), anyString(), anyLong(), anyString(),
                anyLong(), anyString(), anyString(), anyString())).thenReturn(1);

        OperationExecutor target = new OperationExecutor();
        ReflectionTestUtils.setField(target, "operationMapper", operationMapper);
        ReflectionTestUtils.setField(target, "objectMapper", new ObjectMapper());
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(
                new DriverManagerDataSource("jdbc:h2:mem:operation-boundary", "sa", ""));
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TransactionInterceptor(transactionManager,
                new AnnotationTransactionAttributeSource()));
        OperationExecutor executor = (OperationExecutor) proxyFactory.getProxy();

        OperationRow operation = mock(OperationRow.class);
        when(operation.userId()).thenReturn("user-1");
        when(operation.operationId()).thenReturn("operation-1");
        when(operation.version()).thenReturn(0L);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            String result = executor.execute(operation, externalKey -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                return "{}";
            });
            assertEquals("{}", result);
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        });
    }
}
