package com.example.projectname.microservice.authentication.enums;

public enum AuditAction {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGOUT,
    PASSWORD_CHANGE,
    EMAIL_VERIFIED,
    EMAIL_CHANGE_REQUEST,
    EMAIL_CHANGE_CONFIRM,
    ACCOUNT_SOFT_DELETE,
    SOCIAL_LINK,
    SOCIAL_UNLINK,
    MFA_ENABLED,
    MFA_DISABLED
}
