package com.hmdp.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
@Slf4j
public class JwtUtil {
    private String secret;

    /**
     * 生成accesstoken
     * @param userInfo
     * @param autho
     * @return
     */
    public String generateAccessToken(String userInfo, List<String> autho) {
        Date date = new Date();
        Date expire = new Date(date.getTime() + 1000 * 60 * 12 * 100);

        Map<String, Object> header = new HashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        return JWT.create()
                .withHeader(header)
                .withIssuedAt(date)
                .withExpiresAt(expire)
                .withClaim("userInfo", userInfo)
                .withClaim("autho", autho)
                .withClaim("iat", date)
                .withClaim("exp", expire)
                .sign(Algorithm.HMAC256(secret));

    }

    /**
     * 生成refreshtoken
     * @param sub
     * @return
     */
    public String generateRefreshToken(String sub) {
        Date date = new Date();
        Date expire = new Date(date.getTime() + 1000 * 60 * 1);

        Map<String, Object> header = new HashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        return JWT.create()
                .withHeader(header)
                .withIssuedAt(date)
                .withExpiresAt(expire)
                .withClaim("sub", sub)
                .withClaim("jti", UUID.randomUUID().toString())
                .withClaim("iat", date)
                .withClaim("exp", expire)
                .sign(Algorithm.HMAC256(secret));

    }

    /**
     * 验证token
     * @param token
     * @return
     */
    public Boolean verifyToken(String token) {
        Boolean result = true;
        DecodedJWT verifyAccess;

        try {
            JWT.require(Algorithm.HMAC256(secret)).build().verify(token);
            log.info("token 验证成功");
        } catch (Exception e) {
            log.error("token 验证失败");
            result = false;
        }

        return result;
    }

    /**
     * 获取用户信息
     * @param token
     * @return
     */
    public String getUserInfo(String token) {
        try {
            DecodedJWT verify = JWT.require(Algorithm.HMAC256(secret)).build().verify(token);
            return verify.getClaim("userInfo").asString();
        } catch (Exception e) {
            log.error("获取用户信息失败");
        }
        return null;
    }

    public int getSub(String token) {
        try {
            DecodedJWT verify = JWT.require(Algorithm.HMAC256(secret)).build().verify(token);
            return verify.getClaim("sub").asInt();
        } catch (Exception e) {

        }

        return -1;
    }

    /**
     * 获取用户权限
     * @param token
     * @return
     */
    public List<String> getAutho(String token) {
        try {
            DecodedJWT verify = JWT.require(Algorithm.HMAC256(secret)).build().verify(token);

            return verify.getClaim("autho").asList(String.class);
        } catch (Exception e) {
            log.error("获取用户权限失败");
        }
        return null;
    }
}
