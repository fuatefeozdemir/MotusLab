package com.motuslab.model;

import java.time.OffsetDateTime;

public class SimulationRun {

    private int id;
    private int experimentId;
    private int triggeredBy;
    private String triggeredByUsername;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private String errorMessage;
    private String name;
    private String notes;

    public SimulationRun() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getExperimentId() {
        return experimentId;
    }

    public void setExperimentId(int experimentId) {
        this.experimentId = experimentId;
    }

    public int getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(int triggeredBy) {
        this.triggeredBy = triggeredBy;
    }

    public String getTriggeredByUsername() {
        return triggeredByUsername;
    }

    public void setTriggeredByUsername(String triggeredByUsername) {
        this.triggeredByUsername = triggeredByUsername;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(OffsetDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    private int runNumber;

    public int getRunNumber() {
        return runNumber;
    }

    public void setRunNumber(int runNumber) {
        this.runNumber = runNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getDisplayName() {
        if (name != null && !name.isBlank()) {
            return "Run #" + runNumber + " — " + name;
        }
        return "Run #" + runNumber;
    }
}