package com.agentsquad.squad.gateway.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class AuthConflictException extends RuntimeException {
    public AuthConflictException(String message) { super(message); }
}
