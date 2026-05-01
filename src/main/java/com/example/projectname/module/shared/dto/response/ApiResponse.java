package com.example.projectname.module.shared.dto.response;

import lombok.NonNull;

public record ApiResponse<T>(
        @NonNull String msg,
        T data
) {
}
