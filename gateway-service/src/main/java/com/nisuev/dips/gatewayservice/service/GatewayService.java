package com.nisuev.dips.gatewayservice.service;

import com.nisuev.dips.gatewayservice.dto.*;
import com.nisuev.dips.gatewayservice.exception.BadRequestException;
import com.nisuev.dips.gatewayservice.exception.NotFoundException;
import com.nisuev.dips.gatewayservice.exception.ServiceUnavailableException;
import com.nisuev.dips.gatewayservice.repository.BonusRepository;
import com.nisuev.dips.gatewayservice.repository.FlightRepository;
import com.nisuev.dips.gatewayservice.repository.TicketRepository;
import com.nisuev.dips.gatewayservice.resilience.BonusRollbackQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayService {

    private final FlightRepository flightRepository;
    private final TicketRepository ticketRepository;
    private final BonusRepository bonusRepository;
    private final BonusRollbackQueue rollbackQueue;

    public PaginationResponse getFlights(int page, int size) {
        return flightRepository.findAll(page, size);
    }

    public PrivilegeInfoResponse getPrivilege(String username) {
        return bonusRepository.getPrivilege(username);
    }

    public List<TicketResponse> getTickets(String username) {
        return ticketRepository.findAllByUsername(username).stream()
                .map(this::enrich)
                .toList();
    }

    public TicketResponse getTicket(UUID ticketUid, String username) {
        return enrich(ticketRepository.findByUidAndUsername(ticketUid, username));
    }

    public TicketPurchaseResponse purchase(String username, TicketPurchaseRequest request) {
        FlightResponse flight;
        try {
            flight = flightRepository.findByFlightNumber(request.getFlightNumber());
        } catch (NotFoundException e) {
            throw new BadRequestException("Flight not found");
        }

        TicketInternalResponse ticket = ticketRepository.create(
                username,
                new TicketCreateRequest(request.getFlightNumber(), request.getPrice())
        );

        PrivilegeApplyResponse bonus;
        try {
            bonus = bonusRepository.apply(
                    username,
                    new PrivilegeApplyRequest(ticket.getTicketUid(), request.getPrice(), request.getPaidFromBalance())
            );
        } catch (RuntimeException e) {
            rollbackTicket(ticket, username);
            throw e;
        }

        return new TicketPurchaseResponse(
                ticket.getTicketUid(),
                request.getFlightNumber(),
                flight.getFromAirport(),
                flight.getToAirport(),
                flight.getDate(),
                request.getPrice(),
                bonus.getPaidByMoney(),
                bonus.getPaidByBonuses(),
                ticket.getStatus(),
                bonus.getPrivilege()
        );
    }

    public void cancelTicket(UUID ticketUid, String username) {
        ticketRepository.cancel(ticketUid, username);
        try {
            bonusRepository.rollback(username, ticketUid);
        } catch (ServiceUnavailableException e) {
            rollbackQueue.enqueue(username, ticketUid);
        }
    }

    public UserInfoResponse getUserInfo(String username) {
        List<TicketResponse> tickets = getTickets(username);
        PrivilegeShortInfo privilege;
        try {
            PrivilegeInfoResponse privilegeInfo = bonusRepository.getPrivilege(username);
            privilege = new PrivilegeShortInfo(privilegeInfo.getBalance(), privilegeInfo.getStatus());
        } catch (ServiceUnavailableException e) {
            privilege = new PrivilegeShortInfo();
        }
        return new UserInfoResponse(tickets, privilege);
    }

    private TicketResponse enrich(TicketInternalResponse ticket) {
        FlightResponse flight;
        try {
            flight = flightRepository.findByFlightNumber(ticket.getFlightNumber());
        } catch (ServiceUnavailableException e) {
            flight = new FlightResponse(ticket.getFlightNumber(), "", "", "", ticket.getPrice());
        }
        return new TicketResponse(
                ticket.getTicketUid(),
                ticket.getFlightNumber(),
                flight.getFromAirport(),
                flight.getToAirport(),
                flight.getDate(),
                ticket.getPrice(),
                ticket.getStatus()
        );
    }

    private void rollbackTicket(TicketInternalResponse ticket, String username) {
        try {
            ticketRepository.cancel(UUID.fromString(ticket.getTicketUid()), username);
        } catch (RuntimeException e) {
            log.error("Failed to rollback ticket {}: {}", ticket.getTicketUid(), e.getMessage());
        }
    }
}
