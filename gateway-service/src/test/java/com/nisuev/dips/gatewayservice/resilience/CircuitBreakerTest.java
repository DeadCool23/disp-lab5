package com.nisuev.dips.gatewayservice.resilience;

import com.nisuev.dips.gatewayservice.dto.PaginationResponse;
import com.nisuev.dips.gatewayservice.exception.NotFoundException;
import com.nisuev.dips.gatewayservice.exception.ServiceUnavailableException;
import com.nisuev.dips.gatewayservice.repository.BonusRepository;
import com.nisuev.dips.gatewayservice.repository.FlightRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class CircuitBreakerTest {

    @MockitoBean
    private RestTemplate restTemplate;

    @Autowired
    private FlightRepository flightRepository;
    @Autowired
    private BonusRepository bonusRepository;
    @Autowired
    private CircuitBreakerRegistry registry;

    @BeforeEach
    void resetBreakers() {
        registry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @Test
    void opensAfterFailuresAndStopsCallingService() {
        when(restTemplate.getForObject(anyString(), any(Class.class), any(Object[].class)))
                .thenThrow(new ResourceAccessException("down"));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> flightRepository.findAll(1, 10))
                    .isInstanceOf(ServiceUnavailableException.class)
                    .hasMessage("Flight Service unavailable");
        }

        assertThat(registry.circuitBreaker("flight").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        verify(restTemplate, times(3)).getForObject(anyString(), any(Class.class), any(Object[].class));
    }

    @Test
    void notFoundDoesNotCountAsFailure() {
        when(restTemplate.getForObject(anyString(), any(Class.class), any(Object[].class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "nf", null, null, null));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> flightRepository.findByFlightNumber("XXX"))
                    .isInstanceOf(NotFoundException.class);
        }

        assertThat(registry.circuitBreaker("flight").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void breakersAreIndependentPerService() {
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(), any(Class.class)))
                .thenThrow(new ResourceAccessException("down"));
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> bonusRepository.getPrivilege())
                    .isInstanceOf(ServiceUnavailableException.class);
        }
        when(restTemplate.getForObject(anyString(), any(Class.class), any(Object[].class)))
                .thenReturn(new PaginationResponse());

        assertThat(registry.circuitBreaker("bonus").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(flightRepository.findAll(1, 10)).isNotNull();
        assertThat(registry.circuitBreaker("flight").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void writeIsTranslatedWithoutBreaker() {
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(), any(Class.class)))
                .thenThrow(new ResourceAccessException("down"));

        assertThatThrownBy(() -> bonusRepository.rollback(java.util.UUID.randomUUID()))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("Bonus Service unavailable");
        assertThat(registry.circuitBreaker("bonus").getMetrics().getNumberOfBufferedCalls()).isZero();
    }
}
