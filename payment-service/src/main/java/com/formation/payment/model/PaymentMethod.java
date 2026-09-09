package com.formation.payment.model;

public enum PaymentMethod {
    CREDIT_CARD,
    DEBIT_CARD,
    PAYPAL,
    STRIPE;

    /** Seules les cartes portent un cardLastFour. */
    public boolean isCard() {
        return this == CREDIT_CARD || this == DEBIT_CARD;
    }
}
