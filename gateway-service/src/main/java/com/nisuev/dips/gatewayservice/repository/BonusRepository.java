package com.nisuev.dips.gatewayservice.repository;

import com.nisuev.dips.gatewayservice.config.ServiceUrlsConfig;
import com.nisuev.dips.gatewayservice.dto.PrivilegeApplyRequest;
import com.nisuev.dips.gatewayservice.dto.PrivilegeApplyResponse;
import com.nisuev.dips.gatewayservice.dto.PrivilegeInfoResponse;
import com.nisuev.dips.gatewayservice.resilience.Downstream;
import com.nisuev.dips.gatewayservice.resilience.DownstreamCalls;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class BonusRepository {

    private final RestTemplate restTemplate;
    private final ServiceUrlsConfig urls;

    @CircuitBreaker(name = "bonus", fallbackMethod = "getPrivilegeFallback")
    public PrivilegeInfoResponse getPrivilege() {
        ResponseEntity<PrivilegeInfoResponse> response = restTemplate.exchange(
                urls.getBonusUrl() + "/api/v1/privilege",
                HttpMethod.GET,
                null,
                PrivilegeInfoResponse.class
        );
        return response.getBody();
    }

    public PrivilegeApplyResponse apply(PrivilegeApplyRequest request) {
        return DownstreamCalls.write(Downstream.BONUS, () -> {
            ResponseEntity<PrivilegeApplyResponse> response = restTemplate.exchange(
                    urls.getBonusUrl() + "/api/v1/privilege",
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    PrivilegeApplyResponse.class
            );
            return response.getBody();
        });
    }

    public void rollback(UUID ticketUid) {
        DownstreamCalls.write(Downstream.BONUS, () -> {
            try {
                restTemplate.exchange(
                        urls.getBonusUrl() + "/api/v1/privilege?ticketUid=" + ticketUid,
                        HttpMethod.DELETE,
                        null,
                        Void.class
                );
            } catch (HttpClientErrorException.NotFound ignored) {
            }
            return null;
        });
    }

    private PrivilegeInfoResponse getPrivilegeFallback(Throwable cause) {
        throw DownstreamCalls.fallback(Downstream.BONUS, cause);
    }
}
