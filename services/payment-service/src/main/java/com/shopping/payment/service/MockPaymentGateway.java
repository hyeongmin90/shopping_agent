package com.shopping.payment.service;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MockPaymentGateway {

    @Value("${payment.mock-gateway.min-delay-ms:100}")
    private int minDelayMs;

    @Value("${payment.mock-gateway.max-delay-ms:500}")
    private int maxDelayMs;

    public GatewayResult authorize(Integer amount) {
        int boundedMax = Math.max(minDelayMs, maxDelayMs);
        int delay = ThreadLocalRandom.current().nextInt(minDelayMs, boundedMax + 1);
        sleep(delay);

        int amountTail = Math.floorMod(amount, 100);
        if (amountTail == 99) {
            return new GatewayResult(GatewayOutcome.DECLINED, null, "DECLINED_BY_ISSUER");
        }
        if (amountTail == 98) {
            sleep(800);
            return new GatewayResult(GatewayOutcome.TIMEOUT, null, "GATEWAY_TIMEOUT");
        }

        String authCode = "AUTH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
        return new GatewayResult(GatewayOutcome.APPROVED, authCode, null);
    }

    private void sleep(int delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    public enum GatewayOutcome {
        APPROVED,
        DECLINED,
        TIMEOUT
    }

    public record GatewayResult(
            GatewayOutcome outcome,
            String authorizationCode,
            String failureReason
    ) {
    }
}
