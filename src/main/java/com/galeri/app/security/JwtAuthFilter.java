package com.galeri.app.security;

import com.galeri.app.repository.PanelUserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtService jwtService;
    private final PanelUserRepository panelUserRepository;

    public JwtAuthFilter(JwtService jwtService, PanelUserRepository panelUserRepository) {
        this.jwtService = jwtService;
        this.panelUserRepository = panelUserRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = jwtService.parseToken(token);
                UUID userId = UUID.fromString(claims.getSubject());

                String tenantStr = claims.get("tenantId", String.class);
                UUID tenantId = (tenantStr != null) ? UUID.fromString(tenantStr) : null;   // SUPER_ADMIN icin null

                // Pasiflestirilen kullanici token'i bitmeden de disari cikar; rol degisikligi hemen gecerli olur
                String role = panelUserRepository.findActiveRole(userId).orElse(null);
                if (role == null) {
                    log.debug("Pasif ya da silinmis kullanici: {}", userId);
                    chain.doFilter(request, response);
                    return;
                }

                var auth = new UsernamePasswordAuthenticationToken(
                        new PanelPrincipal(userId, tenantId, role),
                        null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));

                SecurityContextHolder.getContext().setAuthentication(auth);

                MDC.put("userId", userId.toString());
                if (tenantId != null) {
                    MDC.put("tenantId", tenantId.toString());
                }
            } catch (Exception e) {
                // Token gecersiz -> auth koymayiz, istek 401 alir
                log.debug("Gecersiz JWT: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }
}
