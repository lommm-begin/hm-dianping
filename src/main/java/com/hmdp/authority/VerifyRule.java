package com.hmdp.authority;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface VerifyRule {
    boolean verifyRule(HttpServletRequest request, HttpServletResponse response) throws JsonProcessingException;
}
