package com.gbf.granblue_simulator._log.trace;

/**
 * TraceId, 메시지를 가지는 상태클래스.
 */
public class TraceStatus {

    private TraceId traceId;
    private String message;

    public TraceStatus(TraceId traceId, String message) {
        this.traceId = traceId;
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public TraceId getTraceId() {
        return traceId;
    }
}
