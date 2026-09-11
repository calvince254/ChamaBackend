package com.calpex_technologies.chama.config;

import com.calpex_technologies.chama.entity.*;
import com.calpex_technologies.chama.repository.*;
import com.calpex_technologies.chama.security.HttpConfigurer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final MemberRepository memberRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        // Create super admin if not exists
        initializeSuperAdmin();
        
        // Fix any incorrectly set systemDefault flags
        fixSystemDefaultFlags();
        
        // Migrate existing roles to new permission system
        migrateRolesToNewPermissions();
    }

    private void initializeSuperAdmin() {
        if (memberRepository.findByEmail("admin@chamamanager.app").isPresent()) {
            log.info("Super admin already exists, skipping initialization.");
            return;
        }

        log.info("Creating platform super admin...");

        Member superAdmin = Member.builder()
                .name("Platform Admin")
                .phone("+254700000000")
                .email("admin@chamamanager.app")
                .password(passwordEncoder.encode("admin123"))
                .memberNo("SA001")
                .joinDate(LocalDate.now())
                .status(Member.MemberStatus.ACTIVE)
                .superAdmin(true)
                .tenant(null)
                .build();
        memberRepository.save(superAdmin);

        log.info("==============================================");
        log.info("Platform initialized successfully!");
        log.info("Super Admin credentials:");
        log.info("  Email: admin@chamamanager.app");
        log.info("  Password: admin123");
        log.info("==============================================");
    }

    /**
     * Fix systemDefault flag - only Admin role should have systemDefault=true
     */
    private void fixSystemDefaultFlags() {
        List<Role> allRoles = roleRepository.findAll();
        
        for (Role role : allRoles) {
            // Only Admin role should be systemDefault
            boolean shouldBeSystemDefault = role.getName().equalsIgnoreCase("admin");
            
            if (role.isSystemDefault() != shouldBeSystemDefault) {
                log.info("Fixing systemDefault for role '{}': {} -> {}", 
                    role.getName(), role.isSystemDefault(), shouldBeSystemDefault);
                role.setSystemDefault(shouldBeSystemDefault);
                roleRepository.save(role);
            }
        }
    }

    /**
     * Migrate all existing roles to use new granular permission codes.
     * For Admin roles, grant ALL permission codes (including newly added ones).
     * For non-admin roles without permissions, grant basic VIEW permissions.
     */
    private void migrateRolesToNewPermissions() {
        List<Role> allRoles = roleRepository.findAll();
        
        // Get all permission codes from HttpConfigurer
        List<String> allPermissionCodes = getAllPermissionCodes();
        log.info("Total available permission codes: {}", allPermissionCodes.size());
        
        for (Role role : allRoles) {
            // Only Admin role gets all permissions (check by name, not by systemDefault)
            boolean isAdmin = role.getName().equalsIgnoreCase("admin");
            
            if (isAdmin) {
                // For Admin roles, ensure they have ALL permission codes (add any missing ones)
                Set<String> existingCodes = role.getRolePermissions() != null 
                    ? role.getRolePermissions().stream()
                        .map(RolePermission::getPermissionCode)
                        .collect(Collectors.toSet())
                    : Set.of();
                
                int addedCount = 0;
                for (String code : allPermissionCodes) {
                    if (!existingCodes.contains(code)) {
                        RolePermission rp = RolePermission.builder()
                                .role(role)
                                .permissionCode(code)
                                .build();
                        role.addRolePermission(rp);
                        addedCount++;
                    }
                }
                
                if (addedCount > 0) {
                    log.info("Added {} missing permission codes to Admin role '{}' (tenant {})", 
                        addedCount, role.getName(), 
                        role.getTenant() != null ? role.getTenant().getId() : "none");
                    roleRepository.save(role);
                } else {
                    log.info("Admin role '{}' (tenant {}) already has all {} permission codes", 
                        role.getName(), 
                        role.getTenant() != null ? role.getTenant().getId() : "none",
                        allPermissionCodes.size());
                }
            } else {
                // For non-admin roles, only seed if they don't have any permissions yet
                if (role.getRolePermissions() != null && !role.getRolePermissions().isEmpty()) {
                    log.info("Non-admin role '{}' (tenant {}) already has {} permission codes, skipping", 
                        role.getName(), role.getTenant() != null ? role.getTenant().getId() : "none", 
                        role.getRolePermissions().size());
                    continue;
                }
                
                // For non-admin roles, seed basic VIEW permissions
                log.info("Seeding basic VIEW permissions for role '{}' (tenant {})", 
                    role.getName(), role.getTenant() != null ? role.getTenant().getId() : "none");
                
                List<String> basicPermissions = List.of(
                    "VIEW_DASHBOARD",
                    "VIEW_MEMBERS",
                    "VIEW_MEETINGS",
                    "VIEW_CONTRIBUTIONS",
                    "VIEW_LOANS",
                    "VIEW_SHARES",
                    "VIEW_WELFARE_EVENTS",
                    "VIEW_INVESTMENTS",
                    "VIEW_MERRY_GO_ROUND",
                    "APPLY_FOR_LOAN"  // Members should be able to apply for loans
                );
                
                for (String code : basicPermissions) {
                    RolePermission rp = RolePermission.builder()
                            .role(role)
                            .permissionCode(code)
                            .build();
                    role.addRolePermission(rp);
                }
                roleRepository.save(role);
            }
        }
        
        log.info("Permission migration complete for {} roles", allRoles.size());
    }

    /**
     * Get all permission codes from HttpConfigurer
     */
    private List<String> getAllPermissionCodes() {
        Map<String, List<HttpConfigurer.PermissionDefinition>> allPermissions = HttpConfigurer.getAllPermissions();
        return allPermissions.values().stream()
                .flatMap(List::stream)
                .map(HttpConfigurer.PermissionDefinition::code)
                .collect(Collectors.toList());
    }
}
