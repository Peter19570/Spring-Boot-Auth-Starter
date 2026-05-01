package com.example.projectname.module.auth.dto.internal;

import lombok.NonNull;

public record ApiResponse<T>(
        @NonNull String msg,
        T data
) {
}
