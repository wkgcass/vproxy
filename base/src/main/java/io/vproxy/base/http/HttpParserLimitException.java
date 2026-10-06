package io.vproxy.base.http;

class HttpParserLimitException extends Exception {
    final int statusCode;

    HttpParserLimitException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }
}
