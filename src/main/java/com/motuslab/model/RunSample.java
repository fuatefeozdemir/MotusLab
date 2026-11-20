package com.motuslab.model;

public class RunSample {

    private int id;
    private int runId;
    private int sampleIndex;
    private double timeSeconds;
    private double positionX;
    private Double positionY;
    private double velocityX;
    private Double velocityY;

    public RunSample() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getRunId() {
        return runId;
    }

    public void setRunId(int runId) {
        this.runId = runId;
    }

    public int getSampleIndex() {
        return sampleIndex;
    }

    public void setSampleIndex(int sampleIndex) {
        this.sampleIndex = sampleIndex;
    }

    public double getTimeSeconds() {
        return timeSeconds;
    }

    public void setTimeSeconds(double timeSeconds) {
        this.timeSeconds = timeSeconds;
    }

    public double getPositionX() {
        return positionX;
    }

    public void setPositionX(double positionX) {
        this.positionX = positionX;
    }

    public Double getPositionY() {
        return positionY;
    }

    public void setPositionY(Double positionY) {
        this.positionY = positionY;
    }

    public double getVelocityX() {
        return velocityX;
    }

    public void setVelocityX(double velocityX) {
        this.velocityX = velocityX;
    }

    public Double getVelocityY() {
        return velocityY;
    }

    public void setVelocityY(Double velocityY) {
        this.velocityY = velocityY;
    }
}