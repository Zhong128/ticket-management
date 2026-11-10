package org.example.ticketmanagement.exception;

import lombok.Getter;

/** 运行预算错误码由服务端生成，消息不包含模型或工具原始内容。 */
@Getter
public class AgentRunException extends RuntimeException {

    private final String code;

    public AgentRunException(String code) {
        super(code);
        this.code = code;
    }
}
