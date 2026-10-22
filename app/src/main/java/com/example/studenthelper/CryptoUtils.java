package com.example.studenthelper;

import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class CryptoUtils {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    // Salt to prevent rainbow table attacks on email hashes
    private static final String EMAIL_SALT = "StudentHelper_Secure_Salt_2026";

    // Application derivation key for email encryption (32 bytes = 256 bits)
    private static final byte[] APP_KEY_BYTES = new byte[]{
            (byte) 0x3d, (byte) 0x6e, (byte) 0x91, (byte) 0x4a,
            (byte) 0x22, (byte) 0x7b, (byte) 0x88, (byte) 0x1f,
            (byte) 0x5a, (byte) 0x4c, (byte) 0x32, (byte) 0x90,
            (byte) 0x12, (byte) 0x8e, (byte) 0x67, (byte) 0x44,
            (byte) 0x9f, (byte) 0x01, (byte) 0x38, (byte) 0xab,
            (byte) 0xcd, (byte) 0xef, (byte) 0x11, (byte) 0x22,
            (byte) 0x33, (byte) 0x44, (byte) 0x55, (byte) 0x66,
            (byte) 0x77, (byte) 0x88, (byte) 0x99, (byte) 0xaa
    };

    private CryptoUtils() {
    }

    /**
     * Hashes an email address with SHA-256 and a dedicated salt.
     * Perfect for zero-knowledge lookup and indexing in Firestore without storing raw email.
     */
    public static String hashEmail(String email) {
        if (email == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            String saltedInput = EMAIL_SALT + ":" + email.trim().toLowerCase();
            byte[] hashBytes = digest.digest(saltedInput.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(email.hashCode());
        }
    }

    /**
     * Encrypts plaintext email using AES-256-GCM.
     * Returns Base64-encoded string containing [IV + Ciphertext].
     */
    public static String encryptEmail(String email) {
        if (email == null || email.isEmpty()) return "";
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            SecretKey key = new SecretKeySpec(APP_KEY_BYTES, "AES");
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);

            byte[] cipherText = cipher.doFinal(email.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) {
            return hashEmail(email);
        }
    }

    /**
     * Decrypts AES-256-GCM encrypted email.
     */
    public static String decryptEmail(String encryptedBase64) {
        if (encryptedBase64 == null || encryptedBase64.isEmpty()) return "";
        try {
            byte[] combined = Base64.decode(encryptedBase64, Base64.NO_WRAP);
            if (combined.length <= GCM_IV_LENGTH) return encryptedBase64;

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];

            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            SecretKey key = new SecretKeySpec(APP_KEY_BYTES, "AES");
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return encryptedBase64;
        }
    }
}
