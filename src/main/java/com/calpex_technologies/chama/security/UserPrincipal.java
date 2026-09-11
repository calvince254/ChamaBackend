package com.calpex_technologies.chama.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserPrincipal {
    private final Long memberId;
    private final Long tenantId;
    private final String email;
    private final String name;
    private final boolean superAdmin;
    private final String roleName;
}
