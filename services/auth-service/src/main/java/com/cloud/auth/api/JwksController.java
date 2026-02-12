package com.cloud.auth.api;

import com.cloud.auth.service.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "Auth", description = "JWKS publishing API")
public class JwksController {

    private final TokenService tokenService;

    public JwksController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @GetMapping("/.well-known/jwks.json")
    @Operation(summary = "Get JWKS public keys")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "JWKS returned")
    })
    public Map<String, Object> jwks() {
        return Map.of("keys", List.of(tokenService.currentJwk()));
    }
}
