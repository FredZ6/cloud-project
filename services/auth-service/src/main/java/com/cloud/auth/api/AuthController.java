package com.cloud.auth.api;

import com.cloud.auth.service.TokenIssueResult;
import com.cloud.auth.service.TokenIntrospectionResult;
import com.cloud.auth.service.TokenService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final TokenService tokenService;

    public AuthController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping("/token")
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
