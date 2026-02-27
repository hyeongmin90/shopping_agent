package com.shopping.payment.repository;

import com.shopping.payment.domain.Refund;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    List<Refund> findByPaymentIdOrderByCreatedAtAsc(UUID paymentId);

    List<Refund> findByPaymentId(UUID paymentId);
}
