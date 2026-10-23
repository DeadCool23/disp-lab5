package com.nisuev.dips.gatewayservice.resilience;

import com.nisuev.dips.gatewayservice.exception.NotFoundException;
import com.nisuev.dips.gatewayservice.exception.ServiceUnavailableException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.function.Supplier;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class DownstreamCalls {
    public static RuntimeException fallback(Downstream service, Throwable cause) {
        if (cause instanceof NotFoundException notFound) {
            return notFound;
        }
        return unavailable(service);
    }

    public static <T> T write(Downstream service, Supplier<T> call) {
        try {
            return call.get();
        } catch (ResourceAccessException | HttpServerErrorException e) {
            log.warn("{} call failed: {}", service.getDisplayName(), e.toString());
            throw unavailable(service);
        }
    }

    private static ServiceUnavailableException unavailable(Downstream service) {
        return new ServiceUnavailableException(service.getDisplayName() + " unavailable");
    }
}
