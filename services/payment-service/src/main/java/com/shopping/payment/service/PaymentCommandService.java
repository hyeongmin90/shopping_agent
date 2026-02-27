package com.shopping.payment.service;

import com.shopping.payment.domain.Payment;
import com.shopping.payment.domain.PaymentStatus;
import com.shopping.payment.domain.Refund;
import com.shopping.payment.domain.RefundStatus;
import com.shopping.payment.exception.InvalidPaymentStateException;
import com.shopping.payment.exception.ResourceNotFoundException;
import com.shopping.payment.messaging.model.AuthorizePaymentCommand;
import com.shopping.payment.messaging.model.CapturePaymentCommand;
import com.shopping.payment.messaging.model.PaymentAuthorizationFailedData;
import com.shopping.payment.messaging.model.PaymentAuthorizedData;
import com.shopping.payment.messaging.model.PaymentCapturedData;
import com.shopping.payment.messaging.model.PaymentRefundedData;
import com.shopping.payment.messaging.model.PaymentVoidedData;
import com.shopping.payment.messaging.model.RefundPaymentCommand;
import com.shopping.payment.messaging.model.VoidPaymentCommand;
import com.shopping.payment.repository.PaymentRepository;
import com.shopping.payment.repository.RefundRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentCommandService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final MockPaymentGateway mockPaymentGateway;

    @Transactional
    public CommandProcessingResult handleAuthorize(AuthorizePaymentCommand command, String idempotencyKey) {
        Payment payment = new Payment();
        payment.setOrderId(command.orderId());
        payment.setUserId(command.userId());
        payment.setAmount(command.amount());
        payment.setCurrency(command.currency() == null ? "KRW" : command.currency().toUpperCase(Locale.ROOT));
        payment.setPaymentMethod(command.paymentMethod() == null ? "MOCK" : command.paymentMethod());
        payment.setIdempotencyKey(idempotencyKey);

        MockPaymentGateway.GatewayResult result = mockPaymentGateway.authorize(command.amount());
        if (result.outcome() == MockPaymentGateway.GatewayOutcome.APPROVED) {
            payment.setStatus(PaymentStatus.AUTHORIZED);
            payment.setAuthorizationCode(result.authorizationCode());
            payment.setFailureReason(null);

            Payment saved = paymentRepository.save(payment);
            return new CommandProcessingResult(
                    "PaymentAuthorized",
                    new PaymentAuthorizedData(saved.getOrderId(), saved.getId(), saved.getAmount(), saved.getAuthorizationCode())
            );
        }

        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(result.failureReason());
        Payment failed = paymentRepository.save(payment);

        String errorCode = result.outcome() == MockPaymentGateway.GatewayOutcome.TIMEOUT ? "TIMEOUT" : "DECLINED";
        return new CommandProcessingResult(
                "PaymentAuthorizationFailed",
                new PaymentAuthorizationFailedData(failed.getOrderId(), failed.getFailureReason(), errorCode, failed.getId())
        );
    }

    @Transactional
    public CommandProcessingResult handleCapture(CapturePaymentCommand command) {
        Payment payment = paymentRepository.findById(command.paymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + command.paymentId()));

        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new InvalidPaymentStateException("Payment is not in AUTHORIZED state: " + payment.getId());
        }

        int captureAmount = command.captureAmount() == null ? payment.getAmount() : command.captureAmount();
        payment.setStatus(PaymentStatus.CAPTURED);
        paymentRepository.save(payment);

        return new CommandProcessingResult(
                "PaymentCaptured",
                new PaymentCapturedData(payment.getOrderId(), payment.getId(), captureAmount, Instant.now())
        );
    }

    @Transactional
    public CommandProcessingResult handleVoid(VoidPaymentCommand command) {
        Payment payment = paymentRepository.findById(command.paymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + command.paymentId()));

        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new InvalidPaymentStateException("Only AUTHORIZED payments can be voided: " + payment.getId());
        }

        payment.setStatus(PaymentStatus.VOIDED);
        paymentRepository.save(payment);

        return new CommandProcessingResult(
                "PaymentVoided",
                new PaymentVoidedData(payment.getOrderId(), payment.getId(), command.reason(), Instant.now())
        );
    }

    @Transactional
    public CommandProcessingResult handleRefund(RefundPaymentCommand command) {
        Payment payment = paymentRepository.findById(command.paymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + command.paymentId()));

        if (payment.getStatus() != PaymentStatus.CAPTURED && payment.getStatus() != PaymentStatus.REFUNDED) {
            throw new InvalidPaymentStateException("Only CAPTURED/REFUNDED payments can be refunded: " + payment.getId());
        }

        int requestedRefund = command.refundAmount() == null ? payment.getAmount() : command.refundAmount();
        List<Refund> existingRefunds = refundRepository.findByPaymentId(payment.getId());
        int alreadyRefunded = existingRefunds.stream().mapToInt(Refund::getAmount).sum();
        int remaining = payment.getAmount() - alreadyRefunded;

        if (requestedRefund <= 0 || requestedRefund > remaining) {
            throw new InvalidPaymentStateException("Invalid refund amount for payment " + payment.getId());
        }

        Refund refund = new Refund();
        refund.setPaymentId(payment.getId());
        refund.setOrderId(payment.getOrderId());
        refund.setAmount(requestedRefund);
        refund.setReason(command.reason());
        refund.setStatus(RefundStatus.COMPLETED);
        Refund savedRefund = refundRepository.save(refund);

        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);

        return new CommandProcessingResult(
                "PaymentRefunded",
                new PaymentRefundedData(
                        payment.getOrderId(),
                        payment.getId(),
                        savedRefund.getId(),
                        savedRefund.getAmount(),
                        savedRefund.getReason(),
                        Instant.now())
        );
    }
}
