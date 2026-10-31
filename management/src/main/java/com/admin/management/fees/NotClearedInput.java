package com.admin.management.fees;

/** The request to mark a cheque receipt "Cheque not cleared": the required reason and the balance version the screen showed. */
record NotClearedInput(String reason, String balanceVersion) {
}
