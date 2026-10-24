package com.galeri.app.exception;

import com.galeri.app.constant.ErrorMessageConstant;
import com.galeri.app.exception.runtime.ApplicationRuntimeException;
import com.galeri.app.model.GenericResponse;
import com.galeri.app.service.impl.GenericResponseService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.stream.Collectors;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final GenericResponseService genericResponseService;

    @ExceptionHandler(ApplicationRuntimeException.class)
    public ResponseEntity<GenericResponse<Object>> handleApplicationRuntimeException(ApplicationRuntimeException e) {
        log.warn("Is kurali hatasi [{}]: {}", e.getError_code(), e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(genericResponseService.createResponseWithError(e.getMessage(), e.getError_code()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GenericResponse<Object>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getAllErrors().stream()
                .map(error -> error.getObjectName() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validasyon hatasi: {}", errorMessage);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(genericResponseService.createResponseWithError(errorMessage, ErrorMessageConstant.VALIDATION_ERROR));
    }

    // @PreAuthorize reddi: genel Exception handler'a dusup 500 donmesin
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<GenericResponse<Object>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Yetkisiz erisim denemesi: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(genericResponseService.createResponseWithError("Bu işlem için yetkiniz yok", ErrorMessageConstant.YETKISIZ));
    }

    // Bozuk JSON, bilinmeyen enum degeri, yanlis tipte path/query parametresi
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, HandlerMethodValidationException.class,
            ConstraintViolationException.class})
    public ResponseEntity<GenericResponse<Object>> handleBadRequest(Exception ex) {
        log.warn("Gecersiz istek: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(genericResponseService.createResponseWithError("Gönderilen bilgiler geçersiz", ErrorMessageConstant.VALIDATION_ERROR));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GenericResponse<Object>> handleGeneralException(Exception ex) {
        log.error("Beklenmeyen hata: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(genericResponseService.createResponseWithError("Beklenmeyen bir hata oluştu.", ErrorMessageConstant.UNKNOWN_ERROR));
    }
}
