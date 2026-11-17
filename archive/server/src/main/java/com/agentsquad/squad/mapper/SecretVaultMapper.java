package com.agentsquad.squad.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 本地密钥保险库 ；只存 AES-GCM 密文与密钥版本，解密在内存完成
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
