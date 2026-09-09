package com.association.assofin.user;

import com.association.assofin.role.Role;
import jakarta.persistence.*;
import lombok.*;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
public class User implements UserDetails {

    @Id
    @GeneratedValue
    private Integer id;
    private String firstname;
    private String lastname;
    private LocalDate birthDate;
    @Column(unique = true)
    private String email;
    private boolean enabled;
    private boolean accountLocked;
    private String password;

    private String username; // same as LDAP sAMAccountName
    private String domainName;


    @ManyToOne(fetch = FetchType.LAZY)
    private Role role;

    //    the bottom lines are related to logging @EntityListeners(AuditingEntityListener.class)
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDate createdDate;
    @LastModifiedDate
    @Column(insertable = false)
    private LocalDate lastModifiedDate;


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        // Add role (with ROLE_ prefix) to enable using hasRole and spring will detect this GrantedAuthority as a role
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));
        // Add permissions by default enable using has hasAuthority
        authorities.addAll(
                role.getRolePermissions().stream()
                        .map(rolePermission -> new SimpleGrantedAuthority(rolePermission.getPermission().getName()))
                        .toList()
        );
        return authorities;
    }
    @Override
    public @Nullable String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !accountLocked;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
