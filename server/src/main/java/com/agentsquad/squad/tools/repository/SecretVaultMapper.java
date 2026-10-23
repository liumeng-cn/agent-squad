package com.agentsquad.squad.tools.repository;

import org.apache.ibatis.annotations.*;

/**
 * 本地密钥保险库 SQL 边界；只存 AES-GCM 密文与密钥版本，解密在内存完成。
 */
@Mapper
public interface SecretVaultMapper {

    @Insert("""
            INSERT INTO sq_secret_vault (secret_ref, key_id, cipher_bytes)
            VALUES (#{secretRef}, #{keyId}, #{cipherBytes})
            """)
    int insert(@Param("secretRef") String secretRef, @Param("keyId") String keyId,
               @Param("cipherBytes") byte[] cipherBytes);

    @Select("SELECT key_id FROM sq_secret_vault WHERE secret_ref = #{secretRef}")
    String findKeyId(@Param("secretRef") String secretRef);

    @Select("SELECT cipher_bytes FROM sq_secret_vault WHERE secret_ref = #{secretRef}")
    byte[] findCipher(@Param("secretRef") String secretRef);

    @Update("""
            UPDATE sq_secret_vault SET key_id = #{keyId}, cipher_bytes = #{cipherBytes}
            WHERE secret_ref = #{secretRef}
            """)
    int updateCipher(@Param("secretRef") String secretRef, @Param("keyId") String keyId,
                     @Param("cipherBytes") byte[] cipherBytes);
}
