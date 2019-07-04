package io.tryouts.java.primers.resiliency.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.decorators.Decorators;
import io.tryouts.java.primers.resiliency.circuitbreaker.service.OutOfProcessService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Duration;
import java.util.function.Supplier;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class Resiliency4JavaCircuitBreakerTests {

    @Mock
    OutOfProcessService outOfProcessService;

    private CircuitBreaker circuitBreaker;

    private Supplier<String> decoratedSupplier;

    @Before
    public void setUp() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(20)
                .ringBufferSizeInClosedState(5)
                .waitDurationInOpenState(Duration.ofSeconds(1))
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        circuitBreaker = registry.circuitBreaker("ResiliencyPrimer");
        decoratedSupplier = Decorators.ofSupplier(() -> outOfProcessService.invokeService("Resiliency4JDemo"))
                .withCircuitBreaker(circuitBreaker)
                .decorate();
    }

    @Test
    public void serviceDecoratedWithCircuitBreakerWillPreventCallsAfterFailureThresholdHasReached() {
        when(outOfProcessService.invokeService("Resiliency4JDemo"))
                .thenThrow(new RuntimeException("RTE"));
        for (int i = 0; i < 10; i++) {
            try {
                decoratedSupplier.get();
            } catch (Exception ignore) {
                System.out.printf("CircuitBreaker State :: %s | Attempt :: %d | Exception Message %s", circuitBreaker.getState(), i, ignore.getMessage() );
            }
        }
        verify(outOfProcessService, times(5)).invokeService("Resiliency4JDemo");
        Assert.assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    @Test
    public void serviceDecoratedWithCircuitBreakerWillAllowCallsAfterSuccessThresholdHasReached() {
        when(outOfProcessService.invokeService("Resiliency4JDemo"))
                .thenThrow(new RuntimeException("RTE"))
                .thenThrow(new RuntimeException("RTE"))
                .thenThrow(new RuntimeException("RTE"))
                .thenThrow(new RuntimeException("RTE"))
                .thenThrow(new RuntimeException("RTE"))
                .thenReturn("Hello World")
                .thenReturn("Hello World")
                .thenReturn("Hello World")
                .thenReturn("Hello World")
                .thenReturn("Hello World");
        for (int i = 0; i < 10; i++) {
            try {
                decoratedSupplier.get();
            } catch (Exception ignore) {
                System.out.printf("CircuitBreaker State :: %s | Attempt :: %d | Exception Message %s", circuitBreaker.getState(), i, ignore.getMessage() );
            }
        }
        verify(outOfProcessService, times(5)).invokeService("Resiliency4JDemo");
        Assert.assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        Assert.assertEquals(5, circuitBreaker.getMetrics().getNumberOfFailedCalls(), 0);
        try {
            Thread.sleep(2000);
        } catch(InterruptedException ex) {

        }
        //Assert.assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        Assert.assertEquals(100.0, circuitBreaker.getMetrics().getFailureRate(), 0);
        for (int i = 0; i < 10; i++) {
            decoratedSupplier.get();
        }
        Assert.assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        Assert.assertEquals(0, circuitBreaker.getMetrics().getNumberOfFailedCalls(), 0);
        Assert.assertEquals(5, circuitBreaker.getMetrics().getNumberOfSuccessfulCalls(), 0);
    }

}
