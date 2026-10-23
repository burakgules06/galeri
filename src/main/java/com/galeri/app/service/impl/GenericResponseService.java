package com.galeri.app.service.impl;

import com.galeri.app.constant.ErrorMessageConstant;
import com.galeri.app.model.GenericResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class GenericResponseService {

    public <T> GenericResponse<T> createResponse(boolean success, String message, T data, int code) {
        return new GenericResponse<T>().setData(data).setSuccess(success).setMessage(message).setCode(code)
                .setRequestId(MDC.get("requestId"));
    }

    public <T> GenericResponse<T> success(T data) {
        return createResponse(true, null, data, ErrorMessageConstant.NO_ERROR);
    }

    public <T> GenericResponse<T> createResponseWithError(String message, int errorCode) {
        return createResponse(false, message, null, errorCode);
    }
}
