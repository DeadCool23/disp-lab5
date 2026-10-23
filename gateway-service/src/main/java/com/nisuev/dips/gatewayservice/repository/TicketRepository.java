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
import org.springframework.http.*;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TicketRepository {

    private static final String USER_HEADER = "X-User-Name";

    private final RestTemplate restTemplate;
    private final ServiceUrlsConfig urls;

    @CircuitBreaker(name = "ticket", fallbackMethod = "findAllByUsernameFallback")
    public List<TicketInternalResponse> findAllByUsername(String username) {
        ResponseEntity<List<TicketInternalResponse>> response = restTemplate.exchange(
                urls.getTicketUrl() + "/api/v1/tickets",
                HttpMethod.GET,
                new HttpEntity<>(headers(username)),
                new ParameterizedTypeReference<>() {}
        );
        return response.getBody();
    }

    @CircuitBreaker(name = "ticket", fallbackMethod = "findByUidAndUsernameFallback")
    public TicketInternalResponse findByUidAndUsername(UUID ticketUid, String username) {
        try {
            ResponseEntity<TicketInternalResponse> response = restTemplate.exchange(
                    urls.getTicketUrl() + "/api/v1/tickets/" + ticketUid,
                    HttpMethod.GET,
                    new HttpEntity<>(headers(username)),
                    TicketInternalResponse.class
            );
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new NotFoundException("Ticket not found");
        }
    }

    public TicketInternalResponse create(String username, TicketCreateRequest request) {
        return DownstreamCalls.write(Downstream.TICKET, () -> {
            ResponseEntity<TicketInternalResponse> response = restTemplate.exchange(
                    urls.getTicketUrl() + "/api/v1/tickets",
                    HttpMethod.POST,
                    new HttpEntity<>(request, jsonHeaders(username)),
                    TicketInternalResponse.class
            );
            return response.getBody();
        });
    }

    public void cancel(UUID ticketUid, String username) {
        DownstreamCalls.write(Downstream.TICKET, () -> {
            try {
                restTemplate.exchange(
                        urls.getTicketUrl() + "/api/v1/tickets/" + ticketUid,
                        HttpMethod.DELETE,
                        new HttpEntity<>(headers(username)),
                        Void.class
                );
            } catch (HttpClientErrorException.NotFound e) {
                throw new NotFoundException("Ticket not found");
            }
            return null;
        });
    }

    private List<TicketInternalResponse> findAllByUsernameFallback(String username, Throwable cause) {
        throw DownstreamCalls.fallback(Downstream.TICKET, cause);
    }

    private TicketInternalResponse findByUidAndUsernameFallback(UUID ticketUid, String username, Throwable cause) {
        throw DownstreamCalls.fallback(Downstream.TICKET, cause);
    }

    private HttpHeaders headers(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(USER_HEADER, username);
        return headers;
    }

    private HttpHeaders jsonHeaders(String username) {
        HttpHeaders headers = headers(username);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
