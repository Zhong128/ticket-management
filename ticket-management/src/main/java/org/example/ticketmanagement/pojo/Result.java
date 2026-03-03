package org.example.ticketmanagement.pojo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class Result<T> {
    private Integer code; //编码：1成功，0为失败
    private String msg; //错误信息
    private T data; //数据

    // 添加泛型支持，这样在使用时可以指定数据类型
    public Result() {
    }

    public Result(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    // 成功，无数据
    public static <T> Result<T> success() {
        return new Result<>(1, "success", null);
    }

    // 成功，有数据
    public static <T> Result<T> success(T data) {
        return new Result<>(1, "success", data);
    }

    // 成功，自定义消息
    public static <T> Result<T> success(String message) {
        return new Result<>(1, message, null);
    }

    // 成功，自定义消息和数据
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(1, message, data);
    }

    // 失败，自定义消息
    public static <T> Result<T> error(String msg) {
        return new Result<>(0, msg, null);
    }

    // 失败，自定义状态码和消息
    public static <T> Result<T> error(Integer code, String msg) {
        return new Result<>(code, msg, null);
    }
}