package com.cloud.auth.api;

import com.cloud.auth.service.TokenIssueResult;
import com.cloud.auth.service.TokenIntrospectionResult;
import com.cloud.auth.service.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Token issuing and introspection APIs")
public class AuthController {

    private final TokenService tokenService;

    public AuthController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping("/token")
    @Operation(summary = "Issue mock JWT token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token issued"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public TokenResponse issueToken(@Valid @RequestBody IssueTokenRequest request) {
        TokenIssueResult result = tokenService.issueToken(request);
        return new TokenResponse(
                result.token(),
                "Bearer",
                result.claims().expiresAt(),
                result.claims().userId(),
                result.claims().roles()
        );
    }

    @PostMapping("/introspect")
    @Operation(summary = "Introspect JWT token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Introspection result returned"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public TokenIntrospectionResponse introspect(@Valid @RequestBody IntrospectTokenRequest request) {
        TokenIntrospectionResult result = tokenService.introspect(request.token());
        if (!result.active()) {
            return new TokenIntrospectionResponse(false, null, List.of(), null, result.reason());
        }
        return new TokenIntrospectionResponse(
                true,
                result.claims().userId(),
                result.claims().roles(),
                result.claims().expiresAt(),
                null
        );
    }
}
