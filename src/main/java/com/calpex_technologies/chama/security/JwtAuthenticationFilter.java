package com.calpex_technologies.chama.security;

import com.calpex_technologies.chama.entity.Member;
import com.calpex_technologies.chama.repository.MemberRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final MemberRepository memberRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        final String authHeader = request.getHeader("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        
        try {
            if (jwtUtil.validateToken(jwt)) {
                String email = jwtUtil.extractEmail(jwt);
                Long memberId = jwtUtil.extractMemberId(jwt);
                Long tenantId = jwtUtil.extractTenantId(jwt);
                Boolean isSuperAdmin = jwtUtil.extractIsSuperAdmin(jwt);
                String roleName = jwtUtil.extractRole(jwt);
                List<String> permissions = jwtUtil.extractPermissions(jwt);
                
                log.debug("JWT validated for user: {}, memberId: {}, isSuperAdmin: {}, permissions: {}", 
                    email, memberId, isSuperAdmin, permissions);
                
                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    // Build authorities from permissions
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    
                    // Add base role authority and permissions from token
                    if (Boolean.TRUE.equals(isSuperAdmin)) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
                        // Super admin ALWAYS gets FULL_ACCESS
                        authorities.add(new SimpleGrantedAuthority("FULL_ACCESS"));
                        log.debug("Super admin detected - adding FULL_ACCESS authority");
                    } else {
                        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                    }
                    
                    // Add each permission from the token as an authority
                    if (permissions != null && !permissions.isEmpty()) {
                        for (String perm : permissions) {
                            authorities.add(new SimpleGrantedAuthority(perm));
                        }
                        log.debug("Added {} permission authorities from token", permissions.size());
                    }
                    
                    // Try to get member from DB, but don't fail if not found (super admin case)
                    Member member = memberRepository.findByIdWithRole(memberId).orElse(null);
                    String memberName = member != null ? member.getName() : email;
                    
                    UserPrincipal principal = new UserPrincipal(
                            memberId,
                            tenantId,
                            email,
                            memberName,
                            Boolean.TRUE.equals(isSuperAdmin),
                            roleName != null ? roleName : "Member"
                    );
                    
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            authorities
                    );
                    
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    
//                    log.debug("Authentication set with {} authorities: {}",
//                        authorities.size(),
//                        authorities.stream().map(a -> a.getAuthority()).collect(Collectors.joining(", ")));
                }
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage(), e);
        }
        
        filterChain.doFilter(request, response);
    }
}
