package com.shopping.review.repository;

import com.shopping.review.domain.SizeFeedback;

public interface SizeFeedbackCountView {
    SizeFeedback getSizeFeedback();

    Long getCount();
}
