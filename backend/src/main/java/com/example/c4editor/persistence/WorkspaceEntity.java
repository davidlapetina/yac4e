package com.example.c4editor.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "workspace")
public class WorkspaceEntity extends AuditedPanacheEntity {
    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "description", nullable = false)
    public String description = "";
}
