package com.motuslab.model;

import java.time.OffsetDateTime;

public class Experiment {

    private int id;
    private int ownerId;
    private String ownerUsername;
    private int simulationTypeId;
    private String simulationTypeCode;
    private String simulationTypeName;
    private String title;
    private String description;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public Experiment() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(int ownerId) {
        this.ownerId = ownerId;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }

    public int getSimulationTypeId() {
        return simulationTypeId;
    }

    public void setSimulationTypeId(int simulationTypeId) {
        this.simulationTypeId = simulationTypeId;
    }

    public String getSimulationTypeCode() {
        return simulationTypeCode;
    }

    public void setSimulationTypeCode(String simulationTypeCode) {
        this.simulationTypeCode = simulationTypeCode;
    }

    public String getSimulationTypeName() {
        return simulationTypeName;
    }

    public void setSimulationTypeName(String simulationTypeName) {
        this.simulationTypeName = simulationTypeName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}