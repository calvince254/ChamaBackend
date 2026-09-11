package com.calpex_technologies.chama.service;

import com.calpex_technologies.chama.dto.CreateTenantRequest;
import com.calpex_technologies.chama.dto.MemberDto;
import com.calpex_technologies.chama.dto.PermissionDto;
import com.calpex_technologies.chama.dto.RoleDto;
import com.calpex_technologies.chama.dto.TenantDto;
import com.calpex_technologies.chama.dto.TenantSettingsDto;
import com.calpex_technologies.chama.entity.*;
import com.calpex_technologies.chama.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantService {

    private final TenantRepository tenantRepository;
    private final MemberRepository memberRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final ContributionRepository contributionRepository;
    private final LoanRepository loanRepository;
    private final MeetingRepository meetingRepository;
    private final ShareRepository shareRepository;
    private final WelfareEventRepository welfareEventRepository;
    private final InvestmentRepository investmentRepository;
    private final TransactionRepository transactionRepository;
    private final MerryGoRoundCycleRepository cycleRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String[] MODULES = {
            "dashboard", "members", "meetings", "contributions", "loans",
            "merry-go-round", "shares", "welfare", "investments", "finance", "roles", "settings"
    };

    public List<TenantDto> getAllTenants() {
        return tenantRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public TenantDto getTenant(Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));
        return toDto(tenant);
    }

    /**
     * Get all members of a specific tenant (for super admin)
     */
    @Transactional(readOnly = true)
    public List<MemberDto> getTenantMembers(Long tenantId) {
        // Verify tenant exists
        if (!tenantRepository.existsById(tenantId)) {
            throw new RuntimeException("Tenant not found");
        }
        
        List<Member> members = memberRepository.findByTenantId(tenantId);
        
        return members.stream()
                .map(this::toMemberDto)
                .collect(Collectors.toList());
    }

    /**
     * Get all roles of a specific tenant
     */
    @Transactional(readOnly = true)
    public List<Role> getTenantRoles(Long tenantId) {
        return roleRepository.findByTenantId(tenantId);
    }

    /**
     * Get all roles of a specific tenant as DTOs
     */
    @Transactional(readOnly = true)
    public List<RoleDto> getTenantRolesDto(Long tenantId) {
        // Verify tenant exists
        if (!tenantRepository.existsById(tenantId)) {
            throw new RuntimeException("Tenant not found");
        }
        
        List<Role> roles = roleRepository.findByTenantId(tenantId);
        List<Member> tenantMembers = memberRepository.findByTenantId(tenantId);
        
        return roles.stream().map(role -> {
            long memberCount = tenantMembers.stream()
                    .filter(m -> m.getRole() != null && m.getRole().getId().equals(role.getId()))
                    .count();
            
            List<PermissionDto> permissions = role.getPermissions().stream()
                    .map(p -> PermissionDto.builder()
                            .module(p.getModule())
                            .view(p.isCanView())
                            .create(p.isCanCreate())
                            .edit(p.isCanEdit())
                            .approve(p.isCanApprove())
                            .delete(p.isCanDelete())
                            .build())
                    .collect(Collectors.toList());
            
            return RoleDto.builder()
                    .id(role.getId())
                    .name(role.getName())
                    .isSystemDefault(role.isSystemDefault())
                    .permissions(permissions)
                    .memberCount((int) memberCount)
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * Update member's role within a tenant
     */
    @Transactional
    public MemberDto updateMemberRole(Long tenantId, Long memberId, Long roleId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        
        if (member.getTenant() == null || !member.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Member does not belong to this tenant");
        }
        
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found"));
        
        if (!role.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Role does not belong to this tenant");
        }
        
        member.setRole(role);
        member = memberRepository.save(member);
        return toMemberDto(member);
    }

    /**
     * Remove a member from a tenant
     */
    @Transactional
    public void removeMemberFromTenant(Long tenantId, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        
        if (member.getTenant() == null || !member.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("Member does not belong to this tenant");
        }
        
        // Delete member's related data
        contributionRepository.deleteByMember(member);
        loanRepository.deleteByMember(member);
        shareRepository.deleteByMember(member);
        welfareEventRepository.deleteByMember(member);
        
        memberRepository.delete(member);
    }

    @Transactional
    public TenantDto createTenant(TenantDto dto) {
        String code = dto.getCode() != null && !dto.getCode().isEmpty() 
                ? dto.getCode().toLowerCase().replaceAll("\\s+", "-")
                : dto.getName().toLowerCase().replaceAll("\\s+", "-");
                
        if (tenantRepository.existsByCode(code)) {
            throw new RuntimeException("Tenant code already exists");
        }

        Tenant tenant = Tenant.builder()
                .name(dto.getName())
                .code(code)
                .contactEmail(dto.getContactEmail())
                .contactPhone(dto.getContactPhone())
                .status(Tenant.TenantStatus.ACTIVE)
                .plan(dto.getPlan() != null ? Tenant.TenantPlan.valueOf(dto.getPlan()) : Tenant.TenantPlan.FREE)
                .build();

        tenant = tenantRepository.save(tenant);
        
        // Create default roles for the tenant
        createDefaultRoles(tenant);
        
        return toDto(tenant);
    }

    /**
     * Create tenant with admin member in one step
     */
    @Transactional
    public TenantDto createTenantWithAdmin(CreateTenantRequest request) {
        String code = request.getCode() != null && !request.getCode().isEmpty() 
                ? request.getCode().toLowerCase().replaceAll("\\s+", "-")
                : request.getName().toLowerCase().replaceAll("\\s+", "-");
                
        if (tenantRepository.existsByCode(code)) {
            throw new RuntimeException("Chama code already exists");
        }
        
        if (memberRepository.findByEmail(request.getAdminEmail()).isPresent()) {
            throw new RuntimeException("Admin email already exists");
        }

        // Create tenant
        Tenant tenant = Tenant.builder()
                .name(request.getName())
                .code(code)
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .status(Tenant.TenantStatus.ACTIVE)
                .plan(Tenant.TenantPlan.FREE)
                .build();
        tenant = tenantRepository.save(tenant);

        // Create default roles (Admin, Treasurer, Secretary, Member)
        java.util.Map<String, Role> rolesMap = createDefaultRolesMap(tenant);
        
        // Determine which role to assign to the first member
        String roleName = request.getAdminRoleName();
        if (roleName == null || roleName.isEmpty()) {
            roleName = "Admin";  // Default to Admin
        }
        
        Role selectedRole = rolesMap.get(roleName);
        if (selectedRole == null) {
            selectedRole = rolesMap.get("Admin");  // Fallback to Admin
        }

        // Create first member with selected role
        Member admin = Member.builder()
                .name(request.getAdminName())
                .email(request.getAdminEmail())
                .phone(request.getAdminPhone() != null ? request.getAdminPhone() : "")
                .password(passwordEncoder.encode(request.getAdminPassword()))
                .memberNo("M001")
                .joinDate(LocalDate.now())
                .status(Member.MemberStatus.ACTIVE)
                .role(selectedRole)
                .tenant(tenant)
                .superAdmin(false)  // Chama admin is NOT a super admin
                .totalSavings(BigDecimal.ZERO)
                .totalShares(BigDecimal.ZERO)
                .build();
        memberRepository.save(admin);

        return toDto(tenant);
    }

    /**
     * Delete tenant and ALL related data (cascade delete)
     */
    @Transactional
    public void deleteTenant(Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        // Delete all related data in order (respecting foreign keys)
        transactionRepository.deleteByTenant(tenant);
        investmentRepository.deleteByTenant(tenant);
        welfareEventRepository.deleteByTenant(tenant);
        shareRepository.deleteByTenant(tenant);
        
        // Delete merry-go-round cycles (rounds are cascade deleted)
        cycleRepository.deleteByTenant(tenant);
        
        meetingRepository.deleteByTenant(tenant);
        loanRepository.deleteByTenant(tenant);
        contributionRepository.deleteByTenant(tenant);
        
        // Delete members
        memberRepository.deleteByTenant(tenant);
        
        // Delete roles and permissions
        List<Role> roles = roleRepository.findByTenant(tenant);
        for (Role role : roles) {
            permissionRepository.deleteByRole(role);
        }
        roleRepository.deleteByTenant(tenant);
        
        // Finally delete the tenant
        tenantRepository.delete(tenant);
    }

    /**
     * Creates default roles (Admin, Treasurer, Member) with both legacy and new permissions, 
     * and returns the Admin role
     */
    private Role createDefaultRoles(Tenant tenant) {
        java.util.Map<String, Role> rolesMap = createDefaultRolesMap(tenant);
        return rolesMap.get("Admin");
    }
    
    /**
     * Creates default roles (Admin, Treasurer, Secretary, Member) with appropriate permissions.
     * Each chama gets these 4 roles by default.
     */
    private java.util.Map<String, Role> createDefaultRolesMap(Tenant tenant) {
        java.util.Map<String, Role> rolesMap = new java.util.HashMap<>();
        
        // Get all permission codes for Admin
        List<String> allPermissionCodes = getAllPermissionCodes();
        
        // ==================== ADMIN ROLE - Full permissions ====================
        Role adminRole = Role.builder()
                .name("Admin")
                .systemDefault(true)
                .tenant(tenant)
                .build();
        adminRole = roleRepository.save(adminRole);
        
        // Add all granular permissions for Admin
        for (String code : allPermissionCodes) {
            RolePermission rp = RolePermission.builder()
                    .role(adminRole)
                    .permissionCode(code)
                    .build();
            adminRole.addRolePermission(rp);
        }
        adminRole = roleRepository.save(adminRole);
        
        // Legacy permissions for Admin
        for (String module : MODULES) {
            Permission p = Permission.builder()
                    .role(adminRole)
                    .module(module)
                    .canView(true)
                    .canCreate(true)
                    .canEdit(true)
                    .canApprove(true)
                    .canDelete(true)
                    .build();
            permissionRepository.save(p);
        }
        rolesMap.put("Admin", adminRole);

        // ==================== TREASURER ROLE - Finance focused ====================
        Role treasurerRole = Role.builder()
                .name("Treasurer")
                .systemDefault(false)
                .tenant(tenant)
                .build();
        treasurerRole = roleRepository.save(treasurerRole);
        
        List<String> treasurerPermissions = List.of(
            "VIEW_DASHBOARD",
            "VIEW_MEMBERS",
            "VIEW_MEETINGS",
            "VIEW_CONTRIBUTIONS", "RECORD_CONTRIBUTION", "EDIT_CONTRIBUTION", "APPROVE_CONTRIBUTION",
            "BULK_RECORD_CONTRIBUTIONS", "GENERATE_CONTRIBUTIONS", "RECORD_CONTRIBUTION_PAYMENT",
            "VIEW_LOANS", "APPROVE_LOAN", "DISBURSE_LOAN", "RECORD_LOAN_REPAYMENT",
            "VIEW_LOAN_PRODUCTS", "VIEW_LOAN_PENALTIES",
            "VIEW_SHARES", "RECORD_SHARE_PURCHASE", "EDIT_SHARE_ENTRY",
            "VIEW_WELFARE_EVENTS", "APPROVE_WELFARE_CLAIM", "DISBURSE_WELFARE",
            "VIEW_INVESTMENTS",
            "VIEW_FINANCE_SUMMARY", "VIEW_TRANSACTIONS", "RECORD_TRANSACTION", "EDIT_TRANSACTION",
            "VIEW_MERRY_GO_ROUND",
            "VIEW_FINES", "RECORD_FINE",
            "VIEW_PENALTIES", "RECORD_PENALTY",
            "VIEW_CONTRIBUTION_TYPES", "VIEW_FINE_TYPES",
            "VIEW_TENANT_SETTINGS"
        );
        for (String code : treasurerPermissions) {
            RolePermission rp = RolePermission.builder()
                    .role(treasurerRole)
                    .permissionCode(code)
                    .build();
            treasurerRole.addRolePermission(rp);
        }
        treasurerRole = roleRepository.save(treasurerRole);
        
        // Legacy permissions for Treasurer
        for (String module : MODULES) {
            boolean hasFinanceAccess = module.equals("contributions") || module.equals("loans") || 
                    module.equals("finance") || module.equals("shares");
            Permission p = Permission.builder()
                    .role(treasurerRole)
                    .module(module)
                    .canView(true)
                    .canCreate(hasFinanceAccess)
                    .canEdit(hasFinanceAccess)
                    .canApprove(hasFinanceAccess)
                    .canDelete(false)
                    .build();
            permissionRepository.save(p);
        }
        rolesMap.put("Treasurer", treasurerRole);

        // ==================== SECRETARY ROLE - Meetings & Records focused ====================
        Role secretaryRole = Role.builder()
                .name("Secretary")
                .systemDefault(false)
                .tenant(tenant)
                .build();
        secretaryRole = roleRepository.save(secretaryRole);
        
        List<String> secretaryPermissions = List.of(
            "VIEW_DASHBOARD",
            "VIEW_MEMBERS", "CREATE_MEMBER", "EDIT_MEMBER", "VIEW_MEMBER_DETAILS",
            "VIEW_MEETINGS", "CREATE_MEETING", "EDIT_MEETING", "SCHEDULE_MEETING",
            "RECORD_MEETING_ATTENDANCE", "MANAGE_MEETING_ATTENDANCE", "MANAGE_MEETING_FINES",
            "VIEW_CONTRIBUTIONS",
            "VIEW_LOANS",
            "VIEW_SHARES",
            "VIEW_WELFARE_EVENTS", "CREATE_WELFARE_EVENT", "EDIT_WELFARE_EVENT",
            "VIEW_INVESTMENTS",
            "VIEW_FINANCE_SUMMARY", "VIEW_TRANSACTIONS",
            "VIEW_MERRY_GO_ROUND",
            "VIEW_FINES", "RECORD_FINE", "EDIT_FINE",
            "VIEW_PENALTIES",
            "VIEW_ROLES",
            "VIEW_CONTRIBUTION_TYPES", "VIEW_FINE_TYPES", "VIEW_LOAN_PRODUCTS",
            "VIEW_TENANT_SETTINGS"
        );
        for (String code : secretaryPermissions) {
            RolePermission rp = RolePermission.builder()
                    .role(secretaryRole)
                    .permissionCode(code)
                    .build();
            secretaryRole.addRolePermission(rp);
        }
        secretaryRole = roleRepository.save(secretaryRole);
        
        // Legacy permissions for Secretary
        for (String module : MODULES) {
            boolean hasMeetingAccess = module.equals("meetings") || module.equals("members") || module.equals("welfare");
            Permission p = Permission.builder()
                    .role(secretaryRole)
                    .module(module)
                    .canView(true)
                    .canCreate(hasMeetingAccess)
                    .canEdit(hasMeetingAccess)
                    .canApprove(false)
                    .canDelete(false)
                    .build();
            permissionRepository.save(p);
        }
        rolesMap.put("Secretary", secretaryRole);

        // ==================== MEMBER ROLE - Basic view + loan application ====================
        Role memberRole = Role.builder()
                .name("Member")
                .systemDefault(false)
                .tenant(tenant)
                .build();
        memberRole = roleRepository.save(memberRole);
        
        List<String> memberPermissions = List.of(
            "VIEW_DASHBOARD",
            "VIEW_MEMBERS",
            "VIEW_MEETINGS",
            "VIEW_CONTRIBUTIONS",
            "VIEW_LOANS", "APPLY_FOR_LOAN",
            "VIEW_SHARES",
            "VIEW_WELFARE_EVENTS", "CREATE_WELFARE_EVENT",
            "VIEW_INVESTMENTS",
            "VIEW_MERRY_GO_ROUND",
            "VIEW_FINES",
            "VIEW_PENALTIES"
        );
        for (String code : memberPermissions) {
            RolePermission rp = RolePermission.builder()
                    .role(memberRole)
                    .permissionCode(code)
                    .build();
            memberRole.addRolePermission(rp);
        }
        memberRole = roleRepository.save(memberRole);
        
        // Legacy permissions for Member
        for (String module : MODULES) {
            boolean createAllowed = module.equals("loans") || module.equals("welfare");
            boolean viewAllowed = !module.equals("roles") && !module.equals("settings");
            Permission p = Permission.builder()
                    .role(memberRole)
                    .module(module)
                    .canView(viewAllowed)
                    .canCreate(createAllowed)
                    .canEdit(false)
                    .canApprove(false)
                    .canDelete(false)
                    .build();
            permissionRepository.save(p);
        }
        rolesMap.put("Member", memberRole);

        return rolesMap;
    }
    
    /**
     * Get all permission codes from HttpConfigurer
     */
    private List<String> getAllPermissionCodes() {
        java.util.Map<String, java.util.List<com.calpex_technologies.chama.security.HttpConfigurer.PermissionDefinition>> allPermissions = 
            com.calpex_technologies.chama.security.HttpConfigurer.getAllPermissions();
        return allPermissions.values().stream()
                .flatMap(java.util.List::stream)
                .map(com.calpex_technologies.chama.security.HttpConfigurer.PermissionDefinition::code)
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional
    public TenantDto updateTenant(Long id, TenantDto dto) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        tenant.setName(dto.getName());
        tenant.setContactEmail(dto.getContactEmail());
        tenant.setContactPhone(dto.getContactPhone());
        
        if (dto.getStatus() != null) {
            tenant.setStatus(Tenant.TenantStatus.valueOf(dto.getStatus()));
        }
        if (dto.getPlan() != null) {
            tenant.setPlan(Tenant.TenantPlan.valueOf(dto.getPlan()));
        }

        tenant = tenantRepository.save(tenant);
        return toDto(tenant);
    }

    @Transactional
    public void suspendTenant(Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));
        tenant.setStatus(Tenant.TenantStatus.SUSPENDED);
        tenantRepository.save(tenant);
    }

    @Transactional
    public void activateTenant(Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));
        tenant.setStatus(Tenant.TenantStatus.ACTIVE);
        tenantRepository.save(tenant);
    }

    // ==================== TENANT SETTINGS ====================
    
    /**
     * Get tenant settings (General Settings tab)
     */
    @Transactional(readOnly = true)
    public TenantSettingsDto getTenantSettings(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));
        
        return TenantSettingsDto.builder()
                .tenantId(tenant.getId())
                .name(tenant.getName())
                .registrationFee(tenant.getRegistrationFee())
                .minMembers(tenant.getMinMembers())
                .maxMembers(tenant.getMaxMembers())
                .signaturesRequired(tenant.getSignaturesRequired())
                .build();
    }

    /**
     * Update tenant settings (General Settings tab)
     */
    @Transactional
    public TenantSettingsDto updateTenantSettings(Long tenantId, TenantSettingsDto dto) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));
        
        // Update settings fields
        if (dto.getName() != null && !dto.getName().isEmpty()) {
            tenant.setName(dto.getName());
        }
        if (dto.getRegistrationFee() != null) {
            tenant.setRegistrationFee(dto.getRegistrationFee());
        }
        if (dto.getMinMembers() != null) {
            tenant.setMinMembers(dto.getMinMembers());
        }
        if (dto.getMaxMembers() != null) {
            tenant.setMaxMembers(dto.getMaxMembers());
        }
        if (dto.getSignaturesRequired() != null) {
            tenant.setSignaturesRequired(dto.getSignaturesRequired());
        }
        
        tenant = tenantRepository.save(tenant);
        
        return TenantSettingsDto.builder()
                .tenantId(tenant.getId())
                .name(tenant.getName())
                .registrationFee(tenant.getRegistrationFee())
                .minMembers(tenant.getMinMembers())
                .maxMembers(tenant.getMaxMembers())
                .signaturesRequired(tenant.getSignaturesRequired())
                .build();
    }

    private TenantDto toDto(Tenant tenant) {
        long memberCount = memberRepository.countByTenantId(tenant.getId());
        
        return TenantDto.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .code(tenant.getCode())
                .contactEmail(tenant.getContactEmail())
                .contactPhone(tenant.getContactPhone())
                .memberCount((int) memberCount)
                .status(tenant.getStatus().name())
                .plan(tenant.getPlan().name())
                .createdAt(tenant.getCreatedAt() != null ? tenant.getCreatedAt().toString() : null)
                .build();
    }

    private MemberDto toMemberDto(Member member) {
        return MemberDto.builder()
                .id(member.getId())
                .name(member.getName())
                .phone(member.getPhone())
                .email(member.getEmail())
                .memberNo(member.getMemberNo())
                .joinDate(member.getJoinDate() != null ? member.getJoinDate().toString() : null)
                .status(member.getStatus().name())
                .role(member.getRole() != null ? member.getRole().getName() : "Member")
                .roleId(member.getRole() != null ? member.getRole().getId() : null)
                .totalSavings(member.getTotalSavings())
                .totalShares(member.getTotalShares())
                .outstandingLoan(member.getOutstandingLoan())
                .arrearsMonths(member.getArrearsMonths())
                .build();
    }
}
