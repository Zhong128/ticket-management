package org.example.ticketmanagement.exception;

import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.exception.NotLoginException;
import org.example.ticketmanagement.controller.AgentConversationController;
import org.example.ticketmanagement.controller.AgentActionController;
import org.example.ticketmanagement.pojo.Result;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** SSE提交前的拒绝仍返回JSON，数据库或模型异常正文不得直接暴露。 */
@Order(-1)
@RestControllerAdvice(assignableTypes = {AgentConversationController.class, AgentActionController.class})
public class AgentConversationExceptionHandler {

    @ExceptionHandler(AgentRequestException.class)
    public ResponseEntity<Result<Void>> request(AgentRequestException failure) {
        return json(Result.error(failure.getCode(), failure.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> validation(MethodArgumentNotValidException failure) {
        return json(Result.error(400, "会话名称或消息参数无效"));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Result<Void>> malformed(Exception failure) {
        return json(Result.error(400, "请求格式或参数无效"));
    }

    @ExceptionHandler(SaTokenException.class)
    public ResponseEntity<Result<Void>> authentication(SaTokenException failure) {
        return json(failure instanceof NotLoginException
                ? Result.error(401, "登录已失效，请重新登录") : Result.error(403, "无权访问客服会话"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> unavailable(Exception failure) {
        return json(Result.error(503, "会话服务暂不可用，请稍后重试"));
    }

    private ResponseEntity<Result<Void>> json(Result<Void> result) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }
}
