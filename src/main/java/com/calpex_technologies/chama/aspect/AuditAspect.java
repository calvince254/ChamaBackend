package com.calpex_technologies.chama.aspect;

import com.calpex_technologies.chama.security.UserPrincipal;
import com.calpex_technologies.chama.service.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    // Endpoints to skip auditing (high frequency, low value)
    private static final String[] SKIP_ENDPOINTS = {
        "/api/auth/validate",
        "/api/auth/refresh",
        "/api/dashboard",
        "/health",
        "/actuator"
    };

    @Around("execution(* com.calpex_technologies.chama.controller..*.*(..)) && " +
            "!execution(* com.calpex_technologies.chama.controller.AuditLogController.*(..))")
    public Object auditControllerMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        HttpServletRequest request = getRequest();
        if (request == null) {
            return joinPoint.proceed();
        }

        String endpoint = request.getRequestURI();
        String httpMethod = request.getMethod();

        // Skip certain endpoints
        if (shouldSkip(endpoint, httpMethod)) {
            return joinPoint.proceed();
        }

        // Get user info (will be populated after login response for auth endpoints)
        Long tenantId = null;
        Long memberId = null;
        String memberName = null;
        boolean isLoginEndpoint = endpoint.contains("/api/auth/login");
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            tenantId = principal.getTenantId();
            memberId = principal.getMemberId();
        }

        // Extract method info
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String controllerName = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = method.getName();

        // Determine module and action
        String module = extractModule(controllerName);
        String action = extractAction(httpMethod, methodName);

        // Extract entity info from path variables
        String entityType = extractEntityType(controllerName);
        Long entityId = extractEntityId(joinPoint, signature);

        // Build description
        String description = buildDescription(action, module, entityId);

        // Get IP address and user agent
        String ipAddress = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        // Capture old value for updates/deletes
        String oldValue = null;
        if (httpMethod.equals("PUT") || httpMethod.equals("DELETE")) {
            oldValue = captureOldValue(joinPoint, entityId, module);
        }

        Object result = null;
        boolean success = true;
        String errorMessage = null;
        Integer responseStatus = 200;

        try {
            result = joinPoint.proceed();
            
            // Extract response status
            if (result instanceof ResponseEntity<?> responseEntity) {
                responseStatus = responseEntity.getStatusCode().value();
                
                // For login endpoints, extract user info from the AuthResponse
                if (isLoginEndpoint && responseEntity.getBody() != null) {
                    try {
                        Object body = responseEntity.getBody();
                        // Use reflection to get id (memberId) and tenantId from AuthResponse
                        java.lang.reflect.Method getId = body.getClass().getMethod("getId");
                        java.lang.reflect.Method getTenantId = body.getClass().getMethod("getTenantId");
                        Object memberIdObj = getId.invoke(body);
                        Object tenantIdObj = getTenantId.invoke(body);
                        if (memberIdObj instanceof Long) memberId = (Long) memberIdObj;
                        if (tenantIdObj instanceof Long) tenantId = (Long) tenantIdObj;
                    } catch (Exception e) {
                        log.debug("Could not extract user info from login response", e);
                    }
                }
            }
            
        } catch (Throwable ex) {
            success = false;
            errorMessage = ex.getMessage();
            responseStatus = 500;
            throw ex;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;

            // Capture new value for creates/updates
            String newValue = null;
            if ((httpMethod.equals("POST") || httpMethod.equals("PUT")) && result instanceof ResponseEntity<?> responseEntity) {
                newValue = captureNewValue(responseEntity.getBody());
            }

            // Log asynchronously to not impact response time
            try {
                auditLogService.logAsync(
                    tenantId,
                    memberId,
                    module,
                    action,
                    httpMethod,
                    endpoint,
                    entityType,
                    entityId,
                    description,
                    oldValue,
                    newValue,
                    ipAddress,
                    userAgent,
                    responseStatus,
                    executionTime,
                    success,
                    errorMessage
                );
            } catch (Exception e) {
                log.error("Failed to log audit event", e);
            }
        }

        return result;
    }

    private boolean shouldSkip(String endpoint, String httpMethod) {
        // Skip ALL GET requests (view-only operations have low audit value)
        // Exception: downloads are audited
        if (httpMethod.equals("GET")) {
            if (endpoint.contains("/download")) {
                return false;  // Audit downloads
            }
            return true;  // Skip all other GETs
        }

        for (String skip : SKIP_ENDPOINTS) {
            if (endpoint.startsWith(skip)) {
                return true;
            }
        }
        return false;
    }

    private HttpServletRequest getRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    private String extractModule(String controllerName) {
        // AuthController -> Auth, MemberController -> Members, etc.
        String module = controllerName.replace("Controller", "");
        
        // Map to module names
        return switch (module) {
            case "Auth" -> "Authentication";
            case "Member" -> "Members";
            case "Loan" -> "Loans";
            case "Contribution" -> "Contributions";
            case "ContributionType" -> "Contribution Types";
            case "Meeting" -> "Meetings";
            case "Fine" -> "Fines";
            case "FineType" -> "Fine Types";
            case "Role" -> "Roles";
            case "Tenant" -> "Tenants";
            case "Dashboard" -> "Dashboard";
            case "Payout" -> "Payouts";
            case "Share" -> "Shares";
            case "Investment" -> "Investments";
            case "Welfare" -> "Welfare";
            case "MerryGoRound" -> "Merry-Go-Round";
            case "Finance" -> "Finance";
            case "Penalty" -> "Penalties";
            case "LoanProduct" -> "Loan Products";
            case "Resource" -> "Resources";
            case "RegistrationFee" -> "Registration Fee";
            case "SystemSettings" -> "System Settings";
            case "Permissions" -> "Permissions";
            default -> module;
        };
    }

    private String extractAction(String httpMethod, String methodName) {
        // First try to infer from HTTP method
        String baseAction = switch (httpMethod) {
            case "GET" -> "VIEW";
            case "POST" -> "CREATE";
            case "PUT", "PATCH" -> "UPDATE";
            case "DELETE" -> "DELETE";
            default -> httpMethod;
        };

        // Refine based on method name
        String lowerMethod = methodName.toLowerCase();
        if (lowerMethod.contains("login")) return "LOGIN";
        if (lowerMethod.contains("logout")) return "LOGOUT";
        if (lowerMethod.contains("password")) return "CHANGE_PASSWORD";
        if (lowerMethod.contains("approve")) return "APPROVE";
        if (lowerMethod.contains("reject")) return "REJECT";
        if (lowerMethod.contains("disburse")) return "DISBURSE";
        if (lowerMethod.contains("verify")) return "VERIFY";
        if (lowerMethod.contains("upload")) return "UPLOAD";
        if (lowerMethod.contains("download")) return "DOWNLOAD";
        if (lowerMethod.contains("export")) return "EXPORT";
        if (lowerMethod.contains("import")) return "IMPORT";
        if (lowerMethod.contains("activate")) return "ACTIVATE";
        if (lowerMethod.contains("deactivate") || lowerMethod.contains("suspend")) return "DEACTIVATE";
        if (lowerMethod.contains("hard")) return "HARD_DELETE";

        return baseAction;
    }

    private String extractEntityType(String controllerName) {
        return controllerName.replace("Controller", "");
    }

    private Long extractEntityId(ProceedingJoinPoint joinPoint, MethodSignature signature) {
        Object[] args = joinPoint.getArgs();
        Parameter[] parameters = signature.getMethod().getParameters();

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            if (param.isAnnotationPresent(PathVariable.class)) {
                PathVariable pv = param.getAnnotation(PathVariable.class);
                String name = pv.value().isEmpty() ? param.getName() : pv.value();
                if (name.equals("id") || name.endsWith("Id")) {
                    Object value = args[i];
                    if (value instanceof Long) {
                        return (Long) value;
                    } else if (value instanceof Integer) {
                        return ((Integer) value).longValue();
                    } else if (value instanceof String) {
                        try {
                            return Long.parseLong((String) value);
                        } catch (NumberFormatException e) {
                            // Not a numeric ID
                        }
                    }
                }
            }
        }
        return null;
    }

    private String buildDescription(String action, String module, Long entityId) {
        StringBuilder desc = new StringBuilder();
        desc.append(action).append(" ").append(module.toLowerCase());
        if (entityId != null) {
            desc.append(" #").append(entityId);
        }
        
        // Special handling for auth actions
        if (action.equals("LOGIN")) {
            return "LOGIN authentication";
        }
        if (action.equals("LOGOUT")) {
            return "LOGOUT authentication";
        }
        
        return desc.toString();
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // Handle multiple IPs (X-Forwarded-For can contain comma-separated list)
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String captureOldValue(ProceedingJoinPoint joinPoint, Long entityId, String module) {
        // For simplicity, we'll just note that this was an update/delete
        // Full value capture would require service lookup
        if (entityId != null) {
            return "Entity ID: " + entityId;
        }
        return null;
    }

    private String captureNewValue(Object body) {
        if (body == null) return null;
        
        // Skip capturing large objects or files
        if (body instanceof byte[] || body instanceof MultipartFile) {
            return "[Binary data]";
        }
        
        try {
            String json = objectMapper.writeValueAsString(body);
            // Truncate very long values
            if (json.length() > 10000) {
                return json.substring(0, 10000) + "... [truncated]";
            }
            return json;
        } catch (Exception e) {
            return body.toString();
        }
    }
}
