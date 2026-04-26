package com.strikerkk.aicommerce.payment_service.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Component
@Slf4j
public class VerifySignature {

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    public boolean verifyRazorpaySignature(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {
        try {
            // Razorpay signature verification formula:
            // HMAC_SHA256(razorpayOrderId + "|" + razorpayPaymentId, keySecret)
            String payload = razorpayOrderId + "|" + razorpayPaymentId;

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    razorpayKeySecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            mac.init(secretKey);

            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            // Convert bytes to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            String expectedSignature = hexString.toString();
            log.debug("Expected signature: {}", expectedSignature);
            log.debug("Received signature: {}", razorpaySignature);

            return expectedSignature.equals(razorpaySignature);

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Error during signature verification", e);
            return false;
        }
    }
}
