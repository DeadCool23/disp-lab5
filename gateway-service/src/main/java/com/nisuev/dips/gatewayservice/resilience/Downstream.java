package com.nisuev.dips.gatewayservice.resilience;

import lombok.Getter;

@Getter
public enum Downstream {
    FLIGHT("flight", "Flight Service"),
    TICKET("ticket", "Ticket Service"),
    BONUS("bonus", "Bonus Service");

    private final String breakerName;
    private final String displayName;

    Downstream(String breakerName, String displayName) {
        this.breakerName = breakerName;
        this.displayName = displayName;
    }
}
