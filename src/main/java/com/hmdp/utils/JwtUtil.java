package com.hmdp.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT 规定了7个官方字段，供选用。
 * iss (issuer)：签发人
 * exp (expiration time)：过期时间
 * sub (subject)：主题
 * aud (audience)：受众
 * nbf (Not Before)：生效时间
 * iat (Issued At)：签发时间
 * jti (JWT ID)：编号
 * 除了官方字段，你还可以在这个部分定义私有字段。
 * {
 *   "sub": "1234567890",
 *   "name": "John Doe",
 *   "admin": true
 * }
 * 注意，JWT 默认是不加密的，任何人都可以读到，所以不要把秘密信息（密码，手机号等）放在这个部分。
 * 这个 JSON 对象也要使用 Base64URL 算法转成字符串。
 */
@Component
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
@Slf4j
public class JwtUtil {
    private String secret;
    public static final String AUTHORIZATIONS = "Authorizations";

    /**
     * 生成accesstoken
     * @param sub
     * @param autho
     * @return
     */
    public String generateAccessToken(String sub, List<String> autho, String iss, String jti, long exp) {
        Date date = new Date();
        Date expire = new Date(date.getTime() + exp);

        Map<String, Object> header = new HashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        return JWT.create()
                .withHeader(header)
                .withIssuedAt(date)
                .withExpiresAt(expire)
                .withClaim("sub", sub)
                .withClaim(AUTHORIZATIONS, autho)
                .withClaim("iat", date)
                .withClaim("jti", jti)
                .withClaim("iss", iss)
                .sign(Algorithm.HMAC256(secret));
    }

    /**
     * 验证token
     * @param token
     * @return
     */
    public Boolean verifyToken(String token) {
        Boolean result = true;

        try {
            JWT.require(Algorithm.HMAC256(secret)).build().verify(token);
            log.info("token 验证成功");
        } catch (Exception e) {
            log.error("token 验证失败");
            result = false;
        }

        return result;
    }

    public String getJti(String token) {
        try {
            DecodedJWT verify = JWT.require(Algorithm.HMAC256(secret)).build().verify(token);
            return verify.getClaim("jti").asString();
        } catch (Exception e) {
            log.error("jwt解析错误");
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

            return verify.getClaim(AUTHORIZATIONS).asList(String.class);
        } catch (Exception e) {
            log.error("获取用户权限失败");
        }
        return null;
    }
}
