package com.shopping.payment.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopping.payment.api.dto.PaymentResponse;
import com.shopping.payment.domain.Payment;
import com.shopping.payment.domain.PaymentStatus;
import com.shopping.payment.service.PaymentQueryService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentQueryService paymentQueryService;

    @Test
    @DisplayName("결제 상세 조회 API")
    void getPayment_Api() throws Exception {
        // given
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Payment payment = new Payment();
        payment.setId(paymentId);
        payment.setOrderId(orderId);
        payment.setUserId(UUID.randomUUID());
        payment.setAmount(1000);
        payment.setStatus(PaymentStatus.CAPTURED);

        when(paymentQueryService.getPaymentById(paymentId)).thenReturn(payment);

        // when & then
        mockMvc.perform(get("/api/payments/{id}", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("CAPTURED"));
    }
}
