package com.gbf.granblue_simulator._log.trace;

import lombok.Getter;

/**
 * TraceId, 메시지를 가지는 상태클래스.
 */
@Getter
public class TraceStatus {

    private final TraceId traceId;
    private final String message;
    private final long startTimeMs;

    public TraceStatus(TraceId traceId, String message) {
        this.traceId = traceId;
        this.message = message;
        this.startTimeMs = System.currentTimeMillis();
    }

}
