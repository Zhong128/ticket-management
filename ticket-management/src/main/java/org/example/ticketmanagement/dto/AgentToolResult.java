package org.example.ticketmanagement.dto;

/** 客服工具统一结果：空数据、条件不满足与查询异常不能混为一谈。 */
public record AgentToolResult<T>(String status, T data, String errorCode, String message) {

    public static <T> AgentToolResult<T> success(T data) {
        return new AgentToolResult<>("SUCCESS", data, null, "查询成功");
    }

    public static <T> AgentToolResult<T> empty(T data) {
        return new AgentToolResult<>("EMPTY", data, "NO_DATA", "未查询到匹配数据");
    }

    public static <T> AgentToolResult<T> rejected(String code, String message) {
        return new AgentToolResult<>("REJECTED", null, code, message);
    }

    public static <T> AgentToolResult<T> error() {
        return new AgentToolResult<>("ERROR", null, "QUERY_FAILED", "查询暂不可用，请稍后重试，不能据此判断无数据或无资格");
    }
}
