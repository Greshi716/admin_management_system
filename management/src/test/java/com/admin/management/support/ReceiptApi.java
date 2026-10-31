package com.admin.management.support;

import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/**
 * Saves receipts through the real API for the tests, reading the balance token first as the screen does. {@code fields}
 * are the JSON members of the receipt without braces, for example
 * {@code "receiptNo":"3100","receiptDate":"2060-10-01","feePaid":100.00,"paymentMode":"CASH"}.
 */
public final class ReceiptApi {

	private ReceiptApi() {
	}

	/** The balance token of the student now (GET fee-collection). */
	public static String token(SignedInClient client, long studentId) throws Exception {
		String json = client.get("/api/students/" + studentId + "/fee-collection")
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(json, "$.balanceVersion");
	}

	/** POST /api/receipts for a current-year receipt with the student's current token. */
	public static ResultActions create(SignedInClient client, long studentId, String fields) throws Exception {
		return createWithToken(client, studentId, token(client, studentId), fields);
	}

	public static ResultActions createWithToken(SignedInClient client, long studentId, String token, String fields)
			throws Exception {
		return client.post("/api/receipts", "{\"studentId\":" + studentId + ",\"receiptType\":\"CURRENT_YEAR\","
				+ "\"balanceVersion\":\"" + token + "\"," + fields + "}");
	}

	/** Cash, year fee only, today. */
	public static String cash(String receiptNo, String date, String feePaid) {
		return "\"receiptNo\":\"" + receiptNo + "\",\"receiptDate\":\"" + date + "\",\"feePaid\":" + feePaid
				+ ",\"paymentMode\":\"CASH\"";
	}

	/** The serial no. of a saved receipt from the response body. */
	public static long serial(ResultActions saved) throws Exception {
		Number serial = JsonPath.read(saved.andReturn().getResponse().getContentAsString(), "$.serialNo");
		return serial.longValue();
	}

}
