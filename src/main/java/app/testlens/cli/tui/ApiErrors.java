// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import app.testlens.cli.client.ApiException;

/** User-facing messages for API errors, shared by the screens that fetch asynchronously. */
public final class ApiErrors {

    private ApiErrors() {}

    public static String message(ApiException e, String notFoundMessage) {
        return switch (e.getCode()) {
            case 401 -> "Invalid GitHub token";
            case 404 -> notFoundMessage;
            default -> "Request failed (HTTP " + e.getCode() + ")";
        };
    }
}
