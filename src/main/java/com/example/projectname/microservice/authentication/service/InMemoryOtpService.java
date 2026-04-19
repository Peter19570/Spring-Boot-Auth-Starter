package com.example.projectname.microservice.authentication.service;

import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;

@Service
@Slf4j
public class InMemoryOtpService {

    private final ConcurrentHashMap<String, OtpData> otpStore =
            new ConcurrentHashMap<>();

    private final ScheduledExecutorService cleanupExecutor =
            Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void init() {
        // Clean up expired OTPs every minute
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredOtps, 1, 1, TimeUnit.MINUTES);
    }

    public String generateOtp(String identifier) {
        String otpCode = String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));

        OtpData otpData = new OtpData(otpCode, System.currentTimeMillis() + 300000); // 5 min expiry
        otpStore.put(identifier, otpData);

        log.info("OTP generated for {}", identifier);
        return otpCode;
    }

    public boolean validateOtp(String identifier, String otpCode) {
        OtpData otpData = otpStore.get(identifier);

        if (otpData == null) {
            log.warn("No OTP found for {}", identifier);
            return false;
        }

        if (System.currentTimeMillis() > otpData.expiryTime) {
            otpStore.remove(identifier);
            log.warn("OTP expired for {}", identifier);
            return false;
        }

        boolean isValid = otpData.otpCode.equals(otpCode);

        if (isValid) {
            otpStore.remove(identifier);
            log.info("OTP validated for {}", identifier);
        } else {
            log.warn("Invalid OTP attempt for {}", identifier);
        }

        return isValid;
    }

    private void cleanupExpiredOtps() {
        long now = System.currentTimeMillis();
        otpStore.entrySet().removeIf(entry -> now > entry.getValue().expiryTime);
    }

    @Data
    @AllArgsConstructor
    private static class OtpData {
        private String otpCode;
        private long expiryTime;
    }
}
