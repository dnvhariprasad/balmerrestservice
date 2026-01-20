package com.balmerlawrie.balmerrestservice.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class EncryptionUtil {

    private static final String ALGORITHM = "AES";
    // 16-byte key for AES-128. In production, this should be an environment
    // variable.
    private static final String KEY = "BalmerLawrie2025";

    /**
     * Encrypts the given string using AES.
     */
    public static String encrypt(String value) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedBytes = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Error encypting value", e);
        }
    }

    /**
     * Decrypts the given string using AES.
     */
    public static String decrypt(String encryptedValue) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedValue);
            return new String(cipher.doFinal(decodedBytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Error decrypting value", e);
        }
    }

    // Helper main method to generate encrypted passwords
    public static void main(String[] args) {
        if (args.length > 0) {
            String password = args[0];
            String encrypted = encrypt(password);
            System.out.println("Original: " + password);
            System.out.println("Encrypted: " + encrypted);
        } else {
            System.out.println("Usage: java EncryptionUtil <password>");
        }
    }
}
