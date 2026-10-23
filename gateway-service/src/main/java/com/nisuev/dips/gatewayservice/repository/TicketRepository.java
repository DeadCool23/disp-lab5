package com.nisuev.dips.gatewayservice.repository;

import com.nisuev.dips.gatewayservice.config.ServiceUrlsConfig;
import com.nisuev.dips.gatewayservice.dto.TicketCreateRequest;
import com.nisuev.dips.gatewayservice.dto.TicketInternalResponse;
import com.nisuev.dips.gatewayservice.exception.NotFoundException;
import com.nisuev.dips.gatewayservice.resilience.Downstream;
import com.nisuev.dips.gatewayservice.resilience.DownstreamCalls;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TicketRepository {

    private final RestTemplate restTemplate;
    private final ServiceUrlsConfig urls;

    @CircuitBreaker(name = "ticket", fallbackMethod = "findAllFallback")
    public List<TicketInternalResponse> findAll() {
        ResponseEntity<List<TicketInternalResponse>> response = restTemplate.exchange(
                urls.getTicketUrl() + "/api/v1/tickets",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );
        return response.getBody();
    }

    @CircuitBreaker(name = "ticket", fallbackMethod = "findByUidFallback")
    public TicketInternalResponse findByUid(UUID ticketUid) {
        try {
            ResponseEntity<TicketInternalResponse> response = restTemplate.exchange(
                    urls.getTicketUrl() + "/api/v1/tickets/" + ticketUid,
                    HttpMethod.GET,
                    null,
                    TicketInternalResponse.class
            );
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new NotFoundException("Ticket not found");
        }
    }

    public TicketInternalResponse create(TicketCreateRequest request) {
        return DownstreamCalls.write(Downstream.TICKET, () -> {
            ResponseEntity<TicketInternalResponse> response = restTemplate.exchange(
                    urls.getTicketUrl() + "/api/v1/tickets",
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    TicketInternalResponse.class
            );
            return response.getBody();
        });
    }

    public void cancel(UUID ticketUid) {
        DownstreamCalls.write(Downstream.TICKET, () -> {
            try {
                restTemplate.exchange(
                        urls.getTicketUrl() + "/api/v1/tickets/" + ticketUid,
                        HttpMethod.DELETE,
                        null,
                        Void.class
                );
            } catch (HttpClientErrorException.NotFound e) {
                throw new NotFoundException("Ticket not found");
            }
            return null;
        });
    }

    private List<TicketInternalResponse> findAllFallback(Throwable cause) {
        throw DownstreamCalls.fallback(Downstream.TICKET, cause);
    }

    private TicketInternalResponse findByUidFallback(UUID ticketUid, Throwable cause) {
        throw DownstreamCalls.fallback(Downstream.TICKET, cause);
    }
}
