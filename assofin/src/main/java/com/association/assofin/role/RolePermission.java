package com.association.assofin.role;

import com.association.assofin.permission.Permission;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "role_permission")
@EntityListeners(AuditingEntityListener.class)
public class RolePermission {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    @JoinColumn(name = "role_id")
    @JsonIgnore // avoid infinite eager fetch
    //@JsonBackReference other way to avoid infinite eager fetch
    private Role role;

    @ManyToOne
    @JoinColumn(name = "permission_id")
    private Permission permission;

    //    the bottom lines are related to logging @EntityListeners(AuditingEntityListener.class)
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDate createdDate;
    @LastModifiedDate
    @Column(insertable = false)
    private LocalDate lastModifiedDate;
}
