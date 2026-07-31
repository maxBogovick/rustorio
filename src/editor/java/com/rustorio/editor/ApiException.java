package com.rustorio.editor;

/** A request problem worth reporting to the browser as a specific HTTP status, not a 500. */
final class ApiException extends RuntimeException {

    final int status;

    ApiException(int status, String message) {
        super(message);
        this.status = status;
    }
}
