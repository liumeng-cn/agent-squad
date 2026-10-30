package com.agentsquad.squad.tools.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 本地密钥保险库 SQL 边界；只存 AES-GCM 密文与密钥版本，解密在内存完成。
 */
@Mapper
public interface SecretVaultMapper {

    int insert(@Param("secretRef") String secretRef, @Param("keyId") String keyId,
               @Param("cipherBytes") byte[] cipherBytes);

    String findKeyId(@Param("secretRef") String secretRef);

    byte[] findCipher(@Param("secretRef") String secretRef);

    int updateCipher(@Param("secretRef") String secretRef, @Param("keyId") String keyId,
                     @Param("cipherBytes") byte[] cipherBytes);
}
