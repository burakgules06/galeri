package com.galeri.app.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GenericResponse<T> {
    private boolean success;
    private int code;
    private String message;
    private T data;
    private String requestId;

    public GenericResponse<T> setSuccess(boolean success) {
        this.success = success;
        return this;
    }

    public GenericResponse<T> setCode(int code) {
        this.code = code;
        return this;
    }

    public GenericResponse<T> setMessage(String message) {
        this.message = message;
        return this;
    }

    public GenericResponse<T> setData(T data) {
        this.data = data;
        return this;
    }

    public GenericResponse<T> setRequestId(String requestId) {
        this.requestId = requestId;
        return this;
    }
}
