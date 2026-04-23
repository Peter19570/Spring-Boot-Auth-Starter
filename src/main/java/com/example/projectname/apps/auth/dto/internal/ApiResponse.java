package com.example.projectname.apps.auth.dto.internal;

import lombok.NonNull;

public record ApiResponse<T>(
        @NonNull String msg,
        T data
) {
}
