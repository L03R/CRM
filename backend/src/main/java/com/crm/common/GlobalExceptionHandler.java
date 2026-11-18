package com.crm.common;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(AuthenticationException.class)
    public ApiResult<Void> authentication(AuthenticationException e) {
        return new ApiResult<>(401, e.getMessage(), null);
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(AccessDeniedException.class)
    public ApiResult<Void> forbidden(AccessDeniedException e) {
        return new ApiResult<>(403, "没有操作权限", null);
    }
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(BusinessException.class)
    public ApiResult<Void> business(BusinessException e) {
        return new ApiResult<>(400, e.getMessage(), null);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult<Void> validation(MethodArgumentNotValidException e) {
        return new ApiResult<>(400, e.getBindingResult().getFieldErrors().getFirst().getDefaultMessage(), null);
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(DuplicateKeyException.class)
    public ApiResult<Void> duplicate(DuplicateKeyException e) {
        return new ApiResult<>(409, "编号或编码已存在（包含已删除记录）", null);
    }
}
