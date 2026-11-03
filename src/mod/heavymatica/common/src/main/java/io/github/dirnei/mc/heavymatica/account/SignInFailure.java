package io.github.dirnei.mc.heavymatica.account;

import io.github.dirnei.mc.heavymatica.cloud.model.ProblemCode;

public enum SignInFailure {
    /** The game's account cannot be verified, for example an offline-mode game. */
    UNVERIFIABLE_ACCOUNT("heavymatica.account.error.unverifiable"),
    MOJANG_UNAVAILABLE("heavymatica.account.error.mojang_unavailable"),
    CLOUD_UNREACHABLE("heavymatica.account.error.cloud_unreachable"),
    UNEXPECTED("heavymatica.account.error.unexpected");

    private final String translationKey;

    SignInFailure(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }

    static SignInFailure of(CloudException error) {
        if (error.unreachable()) {
            return CLOUD_UNREACHABLE;
        }
        ProblemCode code = error.code().orElse(null);
        if (code == ProblemCode.IDENTITY_NOT_CONFIRMED) {
            return UNVERIFIABLE_ACCOUNT;
        }
        if (code == ProblemCode.IDENTITY_PROVIDER_UNAVAILABLE) {
            return MOJANG_UNAVAILABLE;
        }
        return UNEXPECTED;
    }

    public static final class SignInException extends RuntimeException {
        private final SignInFailure failure;

        public SignInException(SignInFailure failure, Throwable cause) {
            super(failure.name(), cause);
            this.failure = failure;
        }

        public SignInFailure failure() {
            return failure;
        }
    }
}
