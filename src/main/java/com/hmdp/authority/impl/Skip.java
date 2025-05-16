package com.hmdp.authority.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.hmdp.authority.VerifyRule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

@Component
public class Skip implements VerifyRule {
    @Override
    public boolean verifyRule(HttpServletRequest request, HttpServletResponse response) throws JsonProcessingException {
        return true;
    }
}
