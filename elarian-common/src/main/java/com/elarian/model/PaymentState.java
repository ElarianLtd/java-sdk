package com.elarian.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PaymentState {
    public List<CustomerNumber> customerNumbers = new ArrayList<>();
    public List<PaymentChannel> paymentChannels = new ArrayList<>();
    public List<PaymentTransaction> transactionLog = new ArrayList<>();
    public List<PaymentTransaction> pendingTransactions = new ArrayList<>();
    public Map<String, PaymentBalance> wallets = new HashMap<>();


    public static final class PaymentTransaction {
        public String transactionId;
        public String appId;
        public PaymentCounterParty debitParty;
        public PaymentCounterParty creditParty;
        public Cash value;
        public PaymentStatus status;
        public long createdAt;
        public long updatedAt;
    }

    public static final class PaymentBalance {
        public String currencyCode;
        public Cash available;
        public Cash actual;
        public long sequenceNr;
        public Map<String, PendingPaymentTransaction> pending = new HashMap<>();
    }

    public static final class PendingPaymentTransaction {
        public long createdAt;
        public Cash value;
        public Cash converted;

        public PendingPaymentTransaction(long createdAt, Cash value, Cash converted) {
            this.createdAt = createdAt;
            this.value = value;
            this.converted = converted;
        }
    }
}
