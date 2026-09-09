package com.coduk.duksungmap.domain.auth.controller;

import com.coduk.duksungmap.domain.auth.dto.*;
import com.coduk.duksungmap.domain.auth.service.AuthEmailService;
import com.coduk.duksungmap.domain.auth.service.RefreshTokenService;
import com.coduk.duksungmap.domain.user.entity.User;
import com.coduk.duksungmap.global.response.ApiResponse;
import com.coduk.duksungmap.global.response.SuccessCode;
import com.coduk.duksungmap.global.security.JwtProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "이메일 인증 로그인 API",
        description = "덕성여대 이메일 인증을 통한 회원가입 및 로그인 API입니다."
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/email")
public class AuthEmailController {

    private final AuthEmailService authEmailService;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    @Operation(
            summary = "이메일 인증 코드 발송",
            description = """
                덕성여대 이메일(@duksung.ac.kr)로 6자리 인증 코드를 발송합니다.
                - 인증 코드는 5분 동안만 유효합니다.
                - 재발송은 60초 쿨다운이 있으며, 이메일/IP/전체 단위 발송 한도가 적용됩니다.
                """
    )
    @PostMapping("/send")
    public ResponseEntity<ApiResponse<SendCodeResponse>> sendCode(
            @RequestBody @Valid SendCodeRequest req,
            HttpServletRequest request
    ) {
        long ttl = authEmailService.sendCode(req.duksungId(), clientIp(request));

        return ResponseEntity
                .status(SuccessCode.OK.getHttpStatus())
                .body(ApiResponse.onSuccess(new SendCodeResponse(ttl), SuccessCode.OK));
    }

    @Operation(
            summary = "이메일 인증 코드 확인 및 로그인",
            description = """
                사용자가 입력한 인증 코드를 검증합니다.
                - 인증 성공 시 회원가입 또는 자동 로그인 처리됩니다.
                - Access Token을 반환합니다.
                - Refresh Token은 HttpOnly 쿠키로 설정됩니다.
                - X-Device-Id 헤더를 통해 디바이스를 구분합니다.
                - 코드 입력은 5회까지만 시도할 수 있고, 초과하면 코드가 폐기됩니다.
                """
    )
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<VerifyCodeResponse>> verifyCode(
            @RequestBody @Valid VerifyCodeRequest req,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            HttpServletResponse response
    ) {
        User user = authEmailService.verifyCode(req.duksungId(), req.code());

        // access token 생성
        String accessToken = jwtProvider.createAccessToken(user.getId(), user.isAdmin());

        // refresh token 생성 + DB 저장
        String refreshRaw = refreshTokenService.issue(user, deviceId);

        // refresh token 쿠키 설정
        refreshTokenService.setRefreshCookie(response, refreshRaw);

        return ResponseEntity
                .status(SuccessCode.OK.getHttpStatus())
                .body(ApiResponse.onSuccess(new VerifyCodeResponse(accessToken), SuccessCode.OK));
    }

    /**
     * Vercel 프록시와 Caddy를 거쳐 오므로 getRemoteAddr()는 프록시 IP다. 맨 앞 항목이
     * Vercel이 기록한 실제 클라이언트 IP다.
     *
     * <p>오리진(api.duksung-map.site)을 직접 때리면 이 헤더를 위조할 수 있으므로 IP 한도는
     * 보조 수단일 뿐이고, 실질적인 상한은 이메일당/전역 발송 한도가 담당한다. 반대로 Caddy에서
     * {@code header_up X-Forwarded-For {remote_host}} 로 덮어쓰면 위조는 막히지만 모든
     * 사용자가 Vercel IP 하나로 합쳐져 정상 트래픽이 차단되므로 그렇게 하면 안 된다.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}