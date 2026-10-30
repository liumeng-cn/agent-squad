package com.agentsquad.squad.tools.connection;

import com.agentsquad.squad.common.exception.RuntimeBusinessException;
import com.agentsquad.squad.common.exception.RuntimeExceptionCode;
import com.agentsquad.squad.tools.repository.SecretVaultMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * 本地密钥保险库（M07 连接密钥）。
 *
 * <p>AES-256-GCM 加密，主密钥来自环境变量 SQUAD_CONNECTION_SECRET_KEY（Base64 的 32 字节，
 * openssl rand -base64 32 生成）；缺失时拒绝加解密而不做弱回退。secret_ref 格式
 * local-vault:keyId:uuid，keyId 支持轮换期间新旧并存；密文只落库，不写日志。</p>
 */
@Service
@Slf4j
public class LocalSecretVault {

    private static final String REF_PREFIX = "local-vault:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    @Value("${agentsquad.vault.master-key:}")
    private String masterKeyBase64;

    @Value("${agentsquad.vault.master-key-id:local-v1}")
    private String masterKeyId;

    @Resource
    private SecretVaultMapper secretVaultMapper;

    private final SecureRandom random = new SecureRandom();

    /**
     * 加密并保存密钥，返回不含明文的 secret_ref；重复保存同一引用按轮换覆盖。
     */
    public String store(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "密钥不能为空");
        }
        String secretRef = REF_PREFIX + masterKeyId + ":" + UUID.randomUUID();
        byte[] cipher = encrypt(secret);
        secretVaultMapper.insert(secretRef, masterKeyId, cipher);
        log.info("连接密钥已加密入库 keyId={} ref={} bytes={}", masterKeyId, shortRef(secretRef), cipher.length);
        return secretRef;
    }

    /**
     * 解密引用对应密钥；keyId 与当前主密钥不一致视为轮换未完成，明确失败。
     */
    public String load(String secretRef) {
        if (secretRef == null || !secretRef.startsWith(REF_PREFIX)) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.VALIDATION, "密钥引用格式无效");
        }
        String keyId = secretVaultMapper.findKeyId(secretRef);
        byte[] cipher = secretVaultMapper.findCipher(secretRef);
        if (keyId == null || cipher == null) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.NOT_FOUND, "密钥引用不存在");
        }
        if (!keyId.equals(masterKeyId)) {
            log.error("密钥版本不匹配拒绝解密 ref={} storedKeyId={} currentKeyId={}",
                    shortRef(secretRef), keyId, masterKeyId);
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "密钥版本尚未完成轮换");
        }
        return decrypt(cipher);
    }

    /**
     * 用当前主密钥重新加密已有引用；供密钥轮换后批量迁移使用。
     */
    public void rotate(String secretRef, String secret) {
        secretVaultMapper.updateCipher(secretRef, masterKeyId, encrypt(secret));
    }

    private byte[] encrypt(String plain) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(),
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
        } catch (Exception exception) {
            log.error("连接密钥加密失败 keyId={}", masterKeyId);
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "密钥加密失败");
        }
    }

    private String decrypt(byte[] stored) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(stored);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            log.error("连接密钥解密失败，疑似主密钥不匹配");
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT, "密钥解密失败，请核对 SQUAD_CONNECTION_SECRET_KEY");
        }
    }

    private SecretKeySpec secretKey() {
        if (masterKeyBase64 == null || masterKeyBase64.isBlank()) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "未配置 SQUAD_CONNECTION_SECRET_KEY，无法处理连接密钥");
        }
        try {
            byte[] key = Base64.getDecoder().decode(masterKeyBase64);
            if (key.length != 32) {
                throw new IllegalArgumentException("主密钥必须是 32 字节");
            }
            return new SecretKeySpec(key, "AES");
        } catch (IllegalArgumentException exception) {
            throw new RuntimeBusinessException(RuntimeExceptionCode.CONFLICT,
                    "SQUAD_CONNECTION_SECRET_KEY 不是有效的 Base64 32 字节密钥");
        }
    }

    private String shortRef(String secretRef) {
        int lastColon = secretRef.lastIndexOf(':');
        return lastColon < 0 ? secretRef : secretRef.substring(lastColon + 1);
    }
}
