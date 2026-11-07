package com.dndtool.service.storage;

/** A rule-library failure with bounded details suitable for the Host. */
public final class LocalRuleLibraryException extends RuntimeException {
    public record Issue(String stage, String moduleKey, String releaseVersion,
                        String expectedRawZipSha256, String expectedLogicalSha256) { }

    private final String code;
    private final Issue issue;
    private final String operationId;

    LocalRuleLibraryException(String code, String message, Issue issue, Throwable cause) {
        this(code, message, issue, cause, null);
    }

    LocalRuleLibraryException(String code, String message, Issue issue, Throwable cause, String operationId) {
        super(message, cause);
        this.code = code;
        this.issue = issue;
        this.operationId = operationId;
    }

    public String code() { return code; }
    public Issue issue() { return issue; }
    public String operationId() { return operationId; }
}
