package com.coduk.duksungmap.domain.auth.service;

import com.coduk.duksungmap.domain.auth.exception.AuthErrorCode;
import com.coduk.duksungmap.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 인증 메일 발송/코드 검증에 대한 남용 방지.
 *
 * <p>SMTP 일일 할당량은 무료 티어 기준 수백 통이라, 제한이 없으면 스크립트 몇 번으로
 * 하루치가 소진되고 그동안 실제 학생들이 가입을 못 한다. 이메일 단위 제한만으로는
 * 주소를 바꿔가며 보내면 총량이 무제한이 되므로 전역 한도까지 함께 둔다.
 *
 * <p>카운터 키에 날짜/시각을 박아두는 이유: TTL 설정이 유실되더라도 다음 구간에는
 * 새 키를 쓰게 되어 영구히 막히는 상황이 생기지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AuthRateLimiter {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final StringRedisTemplate redis;

    @Value("${app.auth.verification-ttl-seconds}")
    private long verificationTtlSeconds;

    @Value("${app.auth.rate-limit.resend-cooldown-seconds}")
    private long resendCooldownSeconds;

    @Value("${app.auth.rate-limit.max-sends-per-email-daily}")
    private int maxSendsPerEmailDaily;

    @Value("${app.auth.rate-limit.max-sends-per-ip-hourly}")
    private int maxSendsPerIpHourly;

    @Value("${app.auth.rate-limit.max-sends-global-daily}")
    private int maxSendsGlobalDaily;

    @Value("${app.auth.rate-limit.max-verify-attempts}")
    private int maxVerifyAttempts;

    /**
     * 발송 전에 호출한다. 통과하면 쿨다운이 걸리고 각 카운터가 1 증가한 상태가 된다.
     * 뒤쪽 한도에서 걸리면 앞쪽 카운터는 이미 증가한 뒤지만, 어차피 시도 자체는
     * 발생한 것이라 그대로 둔다.
     */
    public void guardSend(String email, String clientIp) {
        String day = LocalDate.now().format(DAY);
        String hour = LocalDateTime.now().format(HOUR);

        // 1) 재발송 쿨다운 — 가장 흔한 케이스라 제일 먼저 본다.
        Boolean fresh = redis.opsForValue().setIfAbsent(
                "auth:send:cooldown:" + email, "1", Duration.ofSeconds(resendCooldownSeconds));
        if (!Boolean.TRUE.equals(fresh)) {
            throw new CustomException(AuthErrorCode.EMAIL_SEND_COOLDOWN, cooldownMessage(email));
        }

        // 2) 이메일당 일일 한도 — 한 학생 메일함으로 스팸이 쏟아지는 것을 막는다.
        if (exceeded("auth:send:daily:" + day + ":" + email, maxSendsPerEmailDaily, Duration.ofDays(1))) {
            throw new CustomException(AuthErrorCode.EMAIL_SEND_LIMIT_EXCEEDED);
        }

        // 3) IP당 시간 한도 — 주소를 바꿔가며 보내는 스크립트를 늦춘다.
        //    프록시 뒤에서는 X-Forwarded-For 위조가 가능하므로 보조 수단으로만 본다.
        if (clientIp != null
                && exceeded("auth:send:hourly:" + hour + ":" + clientIp, maxSendsPerIpHourly, Duration.ofHours(1))) {
            throw new CustomException(AuthErrorCode.EMAIL_SEND_LIMIT_EXCEEDED);
        }

        // 4) 전역 일일 한도 — SMTP 할당량이 완전히 바닥나 계정이 잠기는 것을 막는 최후의 방어선.
        if (exceeded("auth:send:daily:global:" + day, maxSendsGlobalDaily, Duration.ofDays(1))) {
            throw new CustomException(AuthErrorCode.EMAIL_SEND_UNAVAILABLE);
        }
    }

    /** 새 코드를 발급했으므로 이전 코드에 대한 실패 횟수는 지운다. */
    public void resetVerifyAttempts(String email) {
        redis.delete(verifyAttemptKey(email));
    }

    /**
     * 코드 검증 시도 1회를 기록한다. 6자리 숫자는 제한이 없으면 5분 안에도 전수 시도가
     * 가능하므로, 횟수를 넘기면 저장된 코드를 폐기해 재발송을 강제한다.
     *
     * @return 한도를 넘겨 코드를 폐기해야 하면 true
     */
    public boolean exceedsVerifyAttempts(String email) {
        String key = verifyAttemptKey(email);
        Long attempts = redis.opsForValue().increment(key);
        if (attempts != null && attempts == 1L) {
            redis.expire(key, Duration.ofSeconds(verificationTtlSeconds));
        }
        return attempts != null && attempts > maxVerifyAttempts;
    }

    private String verifyAttemptKey(String email) {
        return "auth:verify:attempt:" + email;
    }

    private boolean exceeded(String key, int max, Duration ttl) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, ttl);
        }
        return count != null && count > max;
    }

    private String cooldownMessage(String email) {
        Long remain = redis.getExpire("auth:send:cooldown:" + email);
        long seconds = (remain == null || remain < 0) ? resendCooldownSeconds : remain;
        return seconds + "초 후에 다시 요청할 수 있습니다.";
    }
}
