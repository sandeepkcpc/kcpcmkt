package com.kcpc.mkt.workflow.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.marks.domain.RoleType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** ERD-TBL-031: previous/new assignee detail per reassignment episode (ERD-CON-045/046). */
@Entity
@Table(name = "reassignment_assignees",
        uniqueConstraints = @UniqueConstraint(columnNames = {"reassignment_id", "user_id", "assignee_role", "set_side"}))
@AttributeOverride(name = "id", column = @Column(name = "reassignment_assignee_id"))
public class ReassignmentAssignee extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reassignment_id", nullable = false)
    private ReassignmentRecord reassignment;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignee_role", nullable = false, length = 20)
    private RoleType assigneeRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "set_side", nullable = false, length = 20)
    private AssigneeSide setSide;

    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected ReassignmentAssignee() {
    }

    public ReassignmentAssignee(ReassignmentRecord reassignment, User user, RoleType assigneeRole, AssigneeSide setSide) {
        this.reassignment = reassignment;
        this.user = user;
        this.assigneeRole = assigneeRole;
        this.setSide = setSide;
    }
}
