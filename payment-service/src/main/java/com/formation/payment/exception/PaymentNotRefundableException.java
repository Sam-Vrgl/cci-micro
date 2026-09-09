package com.formation.payment.exception;

import com.formation.payment.model.PaymentStatus;

public class PaymentNotRefundableException extends RuntimeException {
    public PaymentNotRefundableException(Long paymentId, PaymentStatus status) {
        super("Le paiement " + paymentId + " n'est pas remboursable : statut " + status
                + " (seul un paiement SUCCESS peut etre rembourse)");
    }
}
