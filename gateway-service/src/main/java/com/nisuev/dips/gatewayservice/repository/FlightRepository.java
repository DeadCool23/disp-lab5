package com.nisuev.dips.gatewayservice.repository;

import com.nisuev.dips.gatewayservice.config.ServiceUrlsConfig;
import com.nisuev.dips.gatewayservice.dto.FlightResponse;
import com.nisuev.dips.gatewayservice.dto.PaginationResponse;
import com.nisuev.dips.gatewayservice.exception.NotFoundException;
import com.nisuev.dips.gatewayservice.resilience.Downstream;
import com.nisuev.dips.gatewayservice.resilience.DownstreamCalls;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Repository
@RequiredArgsConstructor
public class FlightRepository {

    private final RestTemplate restTemplate;
    private final ServiceUrlsConfig urls;

    @CircuitBreaker(name = "flight", fallbackMethod = "findAllFallback")
    public PaginationResponse findAll(int page, int size) {
        return restTemplate.getForObject(
                urls.getFlightUrl() + "/api/v1/flights?page={page}&size={size}",
                PaginationResponse.class,
                page, size
        );
    }

    @CircuitBreaker(name = "flight", fallbackMethod = "findByFlightNumberFallback")
    public FlightResponse findByFlightNumber(String flightNumber) {
        try {
            return restTemplate.getForObject(
                    urls.getFlightUrl() + "/api/v1/flights/{flightNumber}",
                    FlightResponse.class,
                    flightNumber
            );
        } catch (HttpClientErrorException.NotFound e) {
            throw new NotFoundException("Flight not found");
        }
    }

    private PaginationResponse findAllFallback(int page, int size, Throwable cause) {
        throw DownstreamCalls.fallback(Downstream.FLIGHT, cause);
    }

    private FlightResponse findByFlightNumberFallback(String flightNumber, Throwable cause) {
        throw DownstreamCalls.fallback(Downstream.FLIGHT, cause);
    }
}
