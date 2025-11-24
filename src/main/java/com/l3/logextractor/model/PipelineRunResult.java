package com.l3.logextractor.model;

import java.time.LocalDateTime;

/**
 * Model class representing the result of a log extraction pipeline run
 */
public class PipelineRunResult {
    private String runId;
    private String status;
    private String result;
    private LocalDateTime createdDate;
    private LocalDateTime finishedDate;
    private String downloadUrl;
    private String errorMessage;

    public enum Status {
        NOT_STARTED("notStarted"),
        IN_PROGRESS("inProgress"),
        COMPLETED("completed"),
        FAILED("failed"),
        CANCELED("canceled");

        private final String value;
        Status(String value) { this.value = value; }
        public String getValue() { return value; }

        public static Status fromString(String value) {
            for (Status status : Status.values()) {
                if (status.value.equalsIgnoreCase(value)) {
                    return status;
                }
            }
            return NOT_STARTED;
        }
    }

    public enum Result {
        NONE("none"),
        SUCCESS("succeeded"),
        PARTIAL("partiallySucceeded"),
        FAILED("failed"),
        CANCELED("canceled");

        private final String value;
        Result(String value) { this.value = value; }
        public String getValue() { return value; }

        public static Result fromString(String value) {
            for (Result result : Result.values()) {
                if (result.value.equalsIgnoreCase(value)) {
                    return result;
                }
            }
            return NONE;
        }
    }

    // Getters and Setters
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }

    public LocalDateTime getFinishedDate() { return finishedDate; }
    public void setFinishedDate(LocalDateTime finishedDate) { this.finishedDate = finishedDate; }

    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public boolean isCompleted() {
        return Status.fromString(status) == Status.COMPLETED;
    }

    public boolean isSuccessful() {
        return isCompleted() && Result.fromString(result) == Result.SUCCESS;
    }

    public boolean isFailed() {
        return Status.fromString(status) == Status.FAILED ||
               Result.fromString(result) == Result.FAILED;
    }
}
