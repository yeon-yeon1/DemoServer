package com.coduk.duksungmap.domain.auth.exception;

import com.coduk.duksungmap.global.response.BaseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AuthErrorCode implements BaseCode {

    // 이메일 인증
    EMAIL_INVALID(HttpStatus.BAD_REQUEST, "AUTH-001", "아이디 형식이 올바르지 않습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "AUTH-002", "이미 가입된 이메일입니다."),
    EMAIL_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "AUTH-003", "인증 코드가 만료되었습니다."),
    EMAIL_CODE_NOT_MATCH(HttpStatus.BAD_REQUEST, "AUTH-004", "인증 코드가 일치하지 않습니다."),
    EMAIL_SEND_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH-005", "이메일 전송에 실패했습니다."),
    EMAIL_SEND_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "AUTH-006", "인증 코드를 다시 요청하려면 잠시 기다려 주세요."),
    EMAIL_SEND_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "AUTH-007", "인증 코드 요청 횟수를 초과했습니다. 잠시 후 다시 시도해 주세요."),
    EMAIL_SEND_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AUTH-008", "인증 메일 발송이 일시적으로 제한되었습니다. 잠시 후 다시 시도해 주세요."),
    EMAIL_CODE_ATTEMPT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "AUTH-009", "인증 코드 입력 횟수를 초과했습니다. 코드를 다시 요청해 주세요."),

    // 인증 공통
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH-100", "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH-106", "접근 권한이 없습니다."),

    // 토큰
    ACCESS_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH-101", "유효하지 않은 액세스 토큰입니다."),
    ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH-102", "액세스 토큰이 만료되었습니다."),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH-103", "유효하지 않은 리프레시 토큰입니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH-104", "리프레시 토큰이 만료되었습니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH-105", "저장된 리프레시 토큰이 없습니다."),
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}