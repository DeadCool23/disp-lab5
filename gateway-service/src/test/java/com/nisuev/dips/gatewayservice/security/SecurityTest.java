package com.nisuev.dips.gatewayservice.security;

import com.nisuev.dips.gatewayservice.dto.PaginationResponse;
import com.nisuev.dips.gatewayservice.service.GatewayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class SecurityTest {

    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private GatewayService gatewayService;

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void apiWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/flights")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/privilege")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/tickets")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void apiWithInvalidTokenIsUnauthorized() throws Exception {
        when(jwtDecoder.decode("broken")).thenThrow(new BadJwtException("Invalid signature"));

        mockMvc.perform(get("/api/v1/flights").header("Authorization", "Bearer broken"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiWithExpiredTokenIsUnauthorized() throws Exception {
        when(jwtDecoder.decode("expired")).thenThrow(new BadJwtException("Jwt expired"));

        mockMvc.perform(get("/api/v1/flights").header("Authorization", "Bearer expired"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiWithValidTokenIsAllowed() throws Exception {
        Jwt jwt = Jwt.withTokenValue("valid")
                .header("alg", "RS256")
                .claim("preferred_username", "test-max")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        when(jwtDecoder.decode("valid")).thenReturn(jwt);
        when(gatewayService.getFlights(1, 10)).thenReturn(new PaginationResponse());

        mockMvc.perform(get("/api/v1/flights").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk());
    }

    @Test
    void healthDoesNotRequireToken() throws Exception {
        mockMvc.perform(get("/manage/health")).andExpect(status().isOk());
    }
}
