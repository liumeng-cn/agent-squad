package com.agentsquad.squad.tools.operation;

import com.agentsquad.squad.service.UserStatusGuardService;
import com.agentsquad.squad.testsupport.PostgresTestDatabase;
import com.agentsquad.squad.mapper.OperationMapper;
import com.agentsquad.squad.model.po.OperationPO;
import com.agentsquad.squad.service.OperationDispatchService;
import com.agentsquad.squad.service.OperationExecutorService;
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
        when(operationMapper.markInFlight(anyLong(), anyLong(), anyLong(), anyString(),
                anyLong(), any(LocalDateTime.class), anyString())).thenReturn(1);
        when(operationMapper.completeInFlight(anyLong(), anyLong(), anyLong(), anyString(),
                anyLong(), anyString(), anyString(), anyLong())).thenReturn(1);

        OperationExecutorService target = new OperationExecutorService();
        ReflectionTestUtils.setField(target, "userStatus", mock(UserStatusGuardService.class));
        ReflectionTestUtils.setField(target, "operationMapper", operationMapper);
        OperationDispatchService dispatch = mock(OperationDispatchService.class);
        when(dispatch.begin(any(), anyString(), anyLong(), any(), anyString())).thenReturn(1);
        ReflectionTestUtils.setField(target, "dispatchService", dispatch);
        ReflectionTestUtils.setField(target, "objectMapper", new ObjectMapper());
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(
                new DriverManagerDataSource(PostgresTestDatabase.url(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "operation-boundary-" + java.util.UUID.randomUUID())), PostgresTestDatabase.username(), PostgresTestDatabase.password()));
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TransactionInterceptor(transactionManager,
                new AnnotationTransactionAttributeSource()));
        OperationExecutorService executor = (OperationExecutorService) proxyFactory.getProxy();

        OperationPO operation = mock(OperationPO.class);
        when(operation.getUserId()).thenReturn(1L);
        when(operation.getOperationId()).thenReturn(2L);
        when(operation.getVersion()).thenReturn(0L);

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
