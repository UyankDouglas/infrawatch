package com.infrawatch.auth;

import com.infrawatch.auth.AuthDtos.CsrfResponse;
import com.infrawatch.auth.AuthDtos.LoginRequest;
import com.infrawatch.auth.AuthDtos.MeResponse;
import com.infrawatch.auth.AuthDtos.RegisterRequest;
import com.infrawatch.common.ApiException;
import com.infrawatch.security.AppUserDetails;
import com.infrawatch.security.CurrentContextService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CurrentContextService context;

    public AuthController(AuthService authService, AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository, CurrentContextService context) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.context = context;
    }

    /**
     * Garante a emissão do cookie XSRF-TOKEN. O valor deve ser lido do cookie pelo cliente e enviado no
     * cabeçalho X-XSRF-TOKEN (padrão "double submit cookie" para SPAs).
     */
    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), "XSRF-TOKEN");
    }

    @PostMapping("/register")
    public ResponseEntity<MeResponse> register(@Valid @RequestBody RegisterRequest body,
                                               HttpServletRequest request, HttpServletResponse response) {
        authService.register(body);
        MeResponse me = authenticate(body.email(), body.password(), request, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(me);
    }

    @PostMapping("/login")
    public MeResponse login(@Valid @RequestBody LoginRequest body,
                            HttpServletRequest request, HttpServletResponse response) {
        return authenticate(body.email(), body.password(), request, response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public MeResponse me() {
        AppUserDetails principal = context.currentUser()
                .orElseThrow(() -> ApiException.unauthorized("Autenticação necessária."));
        return authService.me(principal);
    }

    private MeResponse authenticate(String email, String password, HttpServletRequest request,
                                    HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email.trim().toLowerCase(Locale.ROOT), password));

        // Proteção contra fixação de sessão: renova o identificador antes de associar a identidade.
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            request.changeSessionId();
        }
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
        securityContextRepository.saveContext(securityContext, request, response);

        AppUserDetails principal = (AppUserDetails) authentication.getPrincipal();
        authService.recordLogin(principal.getId());
        return authService.me(principal);
    }
}
