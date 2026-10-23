package com.nisuev.dips.gatewayservice;

import com.nisuev.dips.gatewayservice.dto.*;
import com.nisuev.dips.gatewayservice.exception.BadRequestException;
import com.nisuev.dips.gatewayservice.exception.NotFoundException;
import com.nisuev.dips.gatewayservice.exception.ServiceUnavailableException;
import com.nisuev.dips.gatewayservice.repository.BonusRepository;
import com.nisuev.dips.gatewayservice.repository.FlightRepository;
import com.nisuev.dips.gatewayservice.repository.TicketRepository;
import com.nisuev.dips.gatewayservice.resilience.BonusRollbackQueue;
import com.nisuev.dips.gatewayservice.service.GatewayService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewayServiceTest {

    @Mock
    private FlightRepository flightRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private BonusRepository bonusRepository;

    @Mock
    private BonusRollbackQueue rollbackQueue;

    @InjectMocks
    private GatewayService gatewayService;

    @Test
    void purchase_orchestratesFlightTicketAndBonus() {
        FlightResponse flight = new FlightResponse(
                "AFL031", "Санкт-Петербург Пулково", "Москва Шереметьево", "2021-10-08 20:00", 1500);
        when(flightRepository.findByFlightNumber("AFL031")).thenReturn(flight);

        TicketInternalResponse ticket = new TicketInternalResponse(
                UUID.randomUUID().toString(), "AFL031", 1500, "PAID");
        when(ticketRepository.create(eq("Test Max"), any(TicketCreateRequest.class))).thenReturn(ticket);

        PrivilegeApplyResponse bonus = new PrivilegeApplyResponse(
                0, 1500, new PrivilegeShortInfo(150, "BRONZE"));
        when(bonusRepository.apply(eq("Test Max"), any(PrivilegeApplyRequest.class))).thenReturn(bonus);

        TicketPurchaseResponse response = gatewayService.purchase(
                "Test Max",
                new TicketPurchaseRequest("AFL031", 1500, false)
        );

        assertThat(response.getFlightNumber()).isEqualTo("AFL031");
        assertThat(response.getFromAirport()).isEqualTo("Санкт-Петербург Пулково");
        assertThat(response.getPaidByMoney()).isEqualTo(1500);
        assertThat(response.getPaidByBonuses()).isEqualTo(0);
        assertThat(response.getPrivilege().getBalance()).isEqualTo(150);
    }

    @Test
    void purchase_throwsBadRequestWhenFlightMissing() {
        when(flightRepository.findByFlightNumber("XXX")).thenThrow(new NotFoundException("Flight not found"));

        assertThatThrownBy(() -> gatewayService.purchase(
                "User", new TicketPurchaseRequest("XXX", 100, false)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void cancelTicket_callsTicketAndBonus() {
        UUID uid = UUID.randomUUID();

        gatewayService.cancelTicket(uid, "Test Max");

        verify(ticketRepository).cancel(uid, "Test Max");
        verify(bonusRepository).rollback("Test Max", uid);
    }

    @Test
    void getTickets_enrichesWithFlightData() {
        TicketInternalResponse ticket = new TicketInternalResponse(
                "uid-1", "AFL031", 1500, "PAID");
        when(ticketRepository.findAllByUsername("User")).thenReturn(List.of(ticket));
        when(flightRepository.findByFlightNumber("AFL031")).thenReturn(
                new FlightResponse("AFL031", "Санкт-Петербург Пулково", "Москва Шереметьево",
                        "2021-10-08 20:00", 1500));

        List<TicketResponse> result = gatewayService.getTickets("User");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFromAirport()).isEqualTo("Санкт-Петербург Пулково");
        assertThat(result.get(0).getDate()).isEqualTo("2021-10-08 20:00");
    }

    @Test
    void purchase_rollsBackTicketWhenBonusUnavailable() {
        when(flightRepository.findByFlightNumber("AFL031")).thenReturn(
                new FlightResponse("AFL031", "A", "B", "2021-10-08 20:00", 1500));
        UUID uid = UUID.randomUUID();
        when(ticketRepository.create(eq("User"), any(TicketCreateRequest.class)))
                .thenReturn(new TicketInternalResponse(uid.toString(), "AFL031", 1500, "PAID"));
        when(bonusRepository.apply(eq("User"), any(PrivilegeApplyRequest.class)))
                .thenThrow(new ServiceUnavailableException("Bonus Service unavailable"));

        assertThatThrownBy(() -> gatewayService.purchase(
                "User", new TicketPurchaseRequest("AFL031", 1500, false)))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("Bonus Service unavailable");

        verify(ticketRepository).cancel(uid, "User");
    }

    @Test
    void cancelTicket_enqueuesBonusRollbackWhenBonusUnavailable() {
        UUID uid = UUID.randomUUID();
        doThrow(new ServiceUnavailableException("Bonus Service unavailable"))
                .when(bonusRepository).rollback("User", uid);

        gatewayService.cancelTicket(uid, "User");

        verify(rollbackQueue).enqueue("User", uid);
    }

    @Test
    void cancelTicket_doesNotEnqueueWhenBonusAvailable() {
        UUID uid = UUID.randomUUID();

        gatewayService.cancelTicket(uid, "User");

        verify(rollbackQueue, never()).enqueue(any(), any());
    }

    @Test
    void getTickets_returnsFallbackFlightFieldsWhenFlightUnavailable() {
        when(ticketRepository.findAllByUsername("User")).thenReturn(
                List.of(new TicketInternalResponse("uid-1", "AFL031", 1500, "PAID")));
        when(flightRepository.findByFlightNumber("AFL031"))
                .thenThrow(new ServiceUnavailableException("Flight Service unavailable"));

        List<TicketResponse> result = gatewayService.getTickets("User");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFlightNumber()).isEqualTo("AFL031");
        assertThat(result.get(0).getFromAirport()).isEmpty();
        assertThat(result.get(0).getToAirport()).isEmpty();
        assertThat(result.get(0).getDate()).isEmpty();
    }

    @Test
    void getTickets_failsWhenTicketServiceUnavailable() {
        when(ticketRepository.findAllByUsername("User"))
                .thenThrow(new ServiceUnavailableException("Ticket Service unavailable"));

        assertThatThrownBy(() -> gatewayService.getTickets("User"))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void getUserInfo_returnsEmptyPrivilegeWhenBonusUnavailable() {
        when(ticketRepository.findAllByUsername("User")).thenReturn(List.of());
        when(bonusRepository.getPrivilege("User"))
                .thenThrow(new ServiceUnavailableException("Bonus Service unavailable"));

        UserInfoResponse result = gatewayService.getUserInfo("User");

        assertThat(result.getTickets()).isEmpty();
        assertThat(result.getPrivilege().getBalance()).isNull();
        assertThat(result.getPrivilege().getStatus()).isNull();
    }

    @Test
    void getPrivilege_failsWhenBonusUnavailable() {
        when(bonusRepository.getPrivilege("User"))
                .thenThrow(new ServiceUnavailableException("Bonus Service unavailable"));

        assertThatThrownBy(() -> gatewayService.getPrivilege("User"))
                .isInstanceOf(ServiceUnavailableException.class);
    }
}
