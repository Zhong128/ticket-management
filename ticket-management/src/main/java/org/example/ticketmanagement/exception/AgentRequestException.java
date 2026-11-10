package org.example.ticketmanagement.exception;

import lombok.Getter;

/** 仅携带可公开的客服业务错误，不透传底层异常信息。 */
@Getter
public class AgentRequestException extends RuntimeException {

    private final int code;

    public AgentRequestException(int code, String message) {
        super(message);
        this.code = code;
    }
}
