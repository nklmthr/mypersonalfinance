package com.nklmthr.finance.personal;

public class RequestContext {

	private static final ThreadLocal<String> xUniqueRequestId = new ThreadLocal<>();

    public static void setRequestId(String requestId) {
        xUniqueRequestId.set(requestId);
    }

    public static String getRequestId() {
        return xUniqueRequestId.get();
    }

    public static void clear() {
        xUniqueRequestId.remove();
    }
}
