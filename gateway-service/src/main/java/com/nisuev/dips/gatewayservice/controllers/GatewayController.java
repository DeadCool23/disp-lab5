package com.nisuev.dips.gatewayservice.controllers;

import com.nisuev.dips.gatewayservice.dto.*;
import com.nisuev.dips.gatewayservice.service.GatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
@Tag(name = "Gateway API")
public class GatewayController {

    private final GatewayService gatewayService;

    @GetMapping("/flights")
    @Operation(summary = "Получить список рейсов")
    public ResponseEntity<PaginationResponse> getFlights(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(gatewayService.getFlights(page, size));
    }

    @GetMapping("/privilege")
    @Operation(summary = "Получить информацию о состоянии бонусного счета")
    public ResponseEntity<PrivilegeInfoResponse> getPrivilege() {
        return ResponseEntity.ok(gatewayService.getPrivilege());
    }

    @GetMapping("/tickets")
    @Operation(summary = "Информация по всем билетам пользователя")
    public ResponseEntity<List<TicketResponse>> getTickets() {
        return ResponseEntity.ok(gatewayService.getTickets());
    }

    @GetMapping("/tickets/{ticketUid}")
    @Operation(summary = "Информация по конкретному билету")
    public ResponseEntity<TicketResponse> getTicket(@PathVariable UUID ticketUid) {
        return ResponseEntity.ok(gatewayService.getTicket(ticketUid));
    }

    @PostMapping("/tickets")
    @Operation(summary = "Покупка билета")
    public ResponseEntity<TicketPurchaseResponse> purchase(
            @Valid @RequestBody TicketPurchaseRequest request) {
        return ResponseEntity.ok(gatewayService.purchase(request));
    }

    @DeleteMapping("/tickets/{ticketUid}")
    @Operation(summary = "Возврат билета")
    public ResponseEntity<Void> cancel(@PathVariable UUID ticketUid) {
        gatewayService.cancelTicket(ticketUid);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Информация о пользователе")
    public ResponseEntity<UserInfoResponse> me() {
        return ResponseEntity.ok(gatewayService.getUserInfo());
    }
}
