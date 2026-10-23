package com.nisuev.dips.ticketservice.controllers;

import com.nisuev.dips.ticketservice.dto.ErrorResponse;
import com.nisuev.dips.ticketservice.dto.TicketCreateRequest;
import com.nisuev.dips.ticketservice.dto.TicketResponse;
import com.nisuev.dips.ticketservice.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
@Tag(name = "Ticket API")
public class TicketController {

    private final TicketService ticketService;

    @GetMapping
    @Operation(summary = "Список билетов пользователя")
    public ResponseEntity<List<TicketResponse>> list(@AuthenticationPrincipal(expression = "claims['preferred_username']") String username) {
        return ResponseEntity.ok(ticketService.getAllByUsername(username));
    }

    @GetMapping("/{ticketUid}")
    @Operation(summary = "Билет по UUID")
    @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<TicketResponse> get(@PathVariable UUID ticketUid,
                                              @AuthenticationPrincipal(expression = "claims['preferred_username']") String username) {
        return ResponseEntity.ok(ticketService.getByUidAndUsername(ticketUid, username));
    }

    @PostMapping
    @Operation(summary = "Создать билет")
    public ResponseEntity<TicketResponse> create(@AuthenticationPrincipal(expression = "claims['preferred_username']") String username,
                                                 @RequestBody TicketCreateRequest request) {
        return ResponseEntity.ok(ticketService.create(username, request));
    }

    @DeleteMapping("/{ticketUid}")
    @Operation(summary = "Отменить билет")
    @ApiResponse(responseCode = "204", description = "Билет отменён")
    @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Void> cancel(@PathVariable UUID ticketUid,
                                       @AuthenticationPrincipal(expression = "claims['preferred_username']") String username) {
        ticketService.cancel(ticketUid, username);
        return ResponseEntity.noContent().build();
    }
}
