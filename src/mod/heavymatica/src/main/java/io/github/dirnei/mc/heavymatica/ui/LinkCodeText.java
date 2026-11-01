package io.github.dirnei.mc.heavymatica.ui;

import io.github.dirnei.mc.heavymatica.account.CloudException;
import io.github.dirnei.mc.heavymatica.cloud.model.LinkCode;
import io.github.dirnei.mc.heavymatica.cloud.model.LinkCodeStatus;
import io.github.dirnei.mc.heavymatica.cloud.model.LinkParty;
import io.github.dirnei.mc.heavymatica.cloud.model.ProblemCode;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

/** What the link code screen says and offers for a code at a given moment, without any game classes. */
final class LinkCodeText {

    /** A translation key with its arguments; an argument is a {@link String} or another {@code Text}. */
    record Text(String key, List<Object> args) {
        static Text of(String key, Object... args) {
            return new Text(key, List.of(args));
        }
    }

    /**
     * @param showCode     the code and its QR code are shown (open and not expired)
     * @param canConfirm   Confirm and Decline are offered (redeemed, within the confirmation time)
     * @param offerNewCode the code is over without a link
     * @param live         the code can still be redeemed or confirmed, so it is polled and cancelled on close
     */
    record View(Text status, boolean showCode, boolean canConfirm, boolean offerNewCode, boolean live) {
    }

    private LinkCodeText() {
    }

    /**
     * @param code    the latest answer, null while the first one is on its way
     * @param joining the issuer is a pending sign-in that joins the redeemer's account
     */
    static View of(@Nullable LinkCode code, Instant now, boolean joining) {
        if (code == null) {
            return new View(Text.of("heavymatica.link.status.loading"), false, false, false, false);
        }
        LinkCodeStatus status = effective(code, now);
        return switch (status) {
            case OPEN -> new View(Text.of("heavymatica.link.status.open", countdown(left(code.getExpiresAt(), now))), true, false, false, true);
            case REDEEMED -> new View(Text.of(joining ? "heavymatica.link.confirm.account" : "heavymatica.link.confirm.identity",
                    party(code.getRedeemer()), countdown(left(code.getConfirmBy(), now))), false, true, false, true);
            case LINKED -> new View(joining ? Text.of("heavymatica.link.status.joined", party(code.getRedeemer()))
                    : Text.of("heavymatica.link.status.linked", party(code.getRedeemer())), false, false, false, false);
            case DECLINED -> new View(Text.of("heavymatica.link.status.declined"), false, false, true, false);
            case CANCELLED -> new View(Text.of("heavymatica.link.status.cancelled"), false, false, true, false);
            case EXPIRED -> new View(Text.of("heavymatica.link.status.expired"), false, false, true, false);
            default -> new View(Text.of("heavymatica.link.status.unknown"), false, false, true, false);
        };
    }

    /** The status, counting a code whose deadline has passed here as expired before the cloud says so. */
    static LinkCodeStatus effective(LinkCode code, Instant now) {
        LinkCodeStatus status = code.getStatus() == null ? LinkCodeStatus.UNKNOWN_DEFAULT_OPEN_API : code.getStatus();
        if (status == LinkCodeStatus.OPEN && passed(code.getExpiresAt(), now)) {
            return LinkCodeStatus.EXPIRED;
        }
        if (status == LinkCodeStatus.REDEEMED && passed(code.getConfirmBy(), now)) {
            return LinkCodeStatus.EXPIRED;
        }
        return status;
    }

    /** Minutes and seconds, for example {@code 4:05}; never below {@code 0:00}. */
    static String countdown(Duration left) {
        long seconds = Math.max(0, (left.toMillis() + 999) / 1000);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    /** A sign-in by its provider and name, or an account by its Minecraft names and web sign-ins. Never an email. */
    static Text party(@Nullable LinkParty party) {
        if (party == null) {
            return Text.of("heavymatica.link.party.unknown");
        }
        List<String> names = party.getAccountNames() == null ? List.of() : party.getAccountNames();
        List<String> providers = party.getAccountProviders() == null ? List.of() : party.getAccountProviders();
        if (!names.isEmpty() || !providers.isEmpty()) {
            List<String> all = new ArrayList<>(names);
            providers.stream().map(LinkCodeText::webSignIn).forEach(all::add);
            return Text.of("heavymatica.link.party.account", String.join(", ", all));
        }
        String name = party.getName() == null ? "?" : party.getName();
        return switch (party.getProvider() == null ? "" : party.getProvider()) {
            case "github" -> Text.of("heavymatica.link.party.github", name);
            case "minecraft" -> Text.of("heavymatica.link.party.minecraft", name);
            default -> Text.of("heavymatica.link.party.other", String.valueOf(party.getProvider()), name);
        };
    }

    /** {@code github:alex-builds} as {@code alex-builds (GitHub)}. */
    static String webSignIn(String providerAndLogin) {
        int colon = providerAndLogin.indexOf(':');
        if (colon < 0) {
            return providerAndLogin;
        }
        return providerAndLogin.substring(colon + 1) + " (" + providerName(providerAndLogin.substring(0, colon)) + ")";
    }

    static String providerName(@Nullable String provider) {
        return switch (provider == null ? "" : provider) {
            case "github" -> "GitHub";
            case "minecraft" -> "Minecraft";
            default -> String.valueOf(provider);
        };
    }

    /** The message for a failed link code or account call. */
    static Text problem(CloudException error) {
        if (error.unreachable()) {
            return Text.of("heavymatica.link.error.unreachable");
        }
        ProblemCode code = error.code().orElse(null);
        String key = code == null ? "unexpected" : switch (code) {
            case LINKING_UNAVAILABLE -> "unavailable";
            case RATE_LIMITED -> "rate_limited";
            case LINK_CODE_INVALID -> "invalid";
            case IDENTITY_ALREADY_LINKED -> "already_linked";
            case TOO_MANY_IDENTITIES -> "too_many";
            case LAST_IDENTITY -> "last_identity";
            case ACCOUNT_CHOICE_REQUIRED -> "not_joined";
            case UNAUTHENTICATED, REFRESH_TOKEN_INVALID -> "signed_out";
            default -> "unexpected";
        };
        return Text.of("heavymatica.link.error." + key);
    }

    /** Every translation key a text uses, its arguments' included. */
    static Stream<String> keys(Text text) {
        return Stream.concat(Stream.of(text.key()), text.args().stream().filter(Text.class::isInstance).map(Text.class::cast).flatMap(LinkCodeText::keys));
    }

    private static Duration left(@Nullable OffsetDateTime deadline, Instant now) {
        return deadline == null ? Duration.ZERO : Duration.between(now, deadline.toInstant());
    }

    private static boolean passed(@Nullable OffsetDateTime deadline, Instant now) {
        return deadline != null && !now.isBefore(deadline.toInstant());
    }
}
