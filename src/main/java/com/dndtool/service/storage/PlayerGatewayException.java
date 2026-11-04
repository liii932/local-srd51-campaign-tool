package com.dndtool.service.storage;

/** Deliberately small public player error vocabulary. Internal storage details never cross it. */
public final class PlayerGatewayException extends RuntimeException {
    private final int status;
    private final String code;
    public PlayerGatewayException(int status, String code) { super(code); this.status = status; this.code = code; }
    public int status() { return status; }
    public String code() { return code; }
}
