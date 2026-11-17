package com.agentsquad.squad.gateway.config;

import com.agentsquad.squad.gateway.config.properties.AuthProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import jakarta.annotation.Resource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * JWT固定算法与密钥配置
 * <p>
 * 所属网关认证模块，登录身份及请求准入统一在网关裁决。
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {
    @Resource
    private AuthProperties properties;

    /**
     * Feign为阻塞HTTP客户端，WebFlux应用需显式提供JSON消息转换器。
     */
    @Bean
    public org.springframework.boot.autoconfigure.http.HttpMessageConverters feignMessageConverters(
            com.fasterxml.jackson.databind.ObjectMapper mapper) {
        return new org.springframework.boot.autoconfigure.http.HttpMessageConverters(
                new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(mapper));
    }

    @Bean
    public JwtEncoder jwtEncoder() throws Exception {
        KeyFactory factory = KeyFactory.getInstance("RSA");
        RSAPrivateKey privateKey = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(read(properties.getPrivateKey())));
        RSAPublicKey publicKey = publicKey();
        RSAKey rsa = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID("squad-auth-v1").build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsa)));
    }

    @Bean
    public JwtDecoder jwtDecoder() throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey()).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.getIssuer()));
        return decoder;
    }

    private RSAPublicKey publicKey() throws Exception {
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(read(properties.getPublicKey())));
    }

    private byte[] read(String path) throws Exception {
        return Base64.getMimeDecoder().decode(Files.readString(Path.of(path)).replaceAll("-----[^-]+-----", ""));
    }
}
