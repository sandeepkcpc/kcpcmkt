package com.kcpc.mkt.common.entity;

import com.kcpc.mkt.common.util.UuidV7;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import java.util.UUID;

/**
 * Common surrogate-PK base for entities whose ERD table default is "Application UUIDv7".
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    private UUID id;

    @PrePersist
    void assignIdIfAbsent() {
        if (id == null) {
            id = UuidV7.generate();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }
}
