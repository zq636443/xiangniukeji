package com.xniu.rental.auth.service;

import com.xniu.rental.common.BusinessException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Component;

@Component
public class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    public String encode(String rawPassword) {
        var salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        var hash = hash(rawPassword.toCharArray(), salt, ITERATIONS, HASH_BYTES);
        return "pbkdf2$" + ITERATIONS + "$"
            + Base64.getEncoder().encodeToString(salt) + "$"
            + Base64.getEncoder().encodeToString(hash);
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }
        var parts = encodedPassword.split("\\$");
        if (parts.length != 4 || !"pbkdf2".equals(parts[0])) {
            return false;
        }
        var iterations = Integer.parseInt(parts[1]);
        var salt = Base64.getDecoder().decode(parts[2]);
        var expected = Base64.getDecoder().decode(parts[3]);
        var actual = hash(rawPassword.toCharArray(), salt, iterations, expected.length);
        return constantTimeEquals(expected, actual);
    }

    private byte[] hash(char[] password, byte[] salt, int iterations, int length) {
        try {
            KeySpec spec = new PBEKeySpec(password, salt, iterations, length * 8);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException exception) {
            throw BusinessException.badRequest("密码校验失败");
        }
    }

    private boolean constantTimeEquals(byte[] expected, byte[] actual) {
        if (expected.length != actual.length) {
            return false;
        }
        var result = 0;
        for (var index = 0; index < expected.length; index++) {
            result |= expected[index] ^ actual[index];
        }
        return result == 0;
    }
}
