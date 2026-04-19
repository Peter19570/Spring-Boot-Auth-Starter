package com.example.projectname.microservice.authentication.dto.internal;

import lombok.NonNull;

public record ApiResponse<T>(
        @NonNull String msg,
        T data
) {
}
