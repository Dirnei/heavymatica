package io.github.dirnei.mc.heavymatica.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dirnei.mc.heavymatica.account.CloudException;
import io.github.dirnei.mc.heavymatica.cloud.model.LinkCode;
import io.github.dirnei.mc.heavymatica.cloud.model.LinkCodeStatus;
import io.github.dirnei.mc.heavymatica.cloud.model.LinkParty;
import io.github.dirnei.mc.heavymatica.cloud.model.ProblemCode;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LinkCodeTextTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");
    private static final LinkParty GITHUB = new LinkParty().provider("github").name("alex-builds");
    private static final LinkParty ACCOUNT = new LinkParty().accountNames(List.of("Alex", "Alex2")).accountProviders(List.of("github:alex-builds"));

    @Test
    void an_open_code_shows_the_code_and_counts_down() {
        LinkCodeText.View view = LinkCodeText.of(code(LinkCodeStatus.OPEN, Duration.ofMinutes(5), null), NOW, false);

        assertTrue(view.showCode());
        assertTrue(view.live());
        assertFalse(view.canConfirm());
        assertFalse(view.offerNewCode());
        assertEquals(LinkCodeText.Text.of("heavymatica.link.status.open", "5:00"), view.status());
    }

    @Test
    void an_open_code_past_its_time_reads_as_expired_and_offers_a_new_one() {
        LinkCodeText.View view = LinkCodeText.of(code(LinkCodeStatus.OPEN, Duration.ZERO, null), NOW, false);

        assertFalse(view.showCode());
        assertFalse(view.live());
        assertTrue(view.offerNewCode());
        assertEquals("heavymatica.link.status.expired", view.status().key());
    }

    @Test
    void a_github_redemption_asks_to_link_that_sign_in_by_its_login() {
        LinkCode code = code(LinkCodeStatus.REDEEMED, Duration.ofMinutes(4), Duration.ofSeconds(185)).redeemer(GITHUB);

        LinkCodeText.View view = LinkCodeText.of(code, NOW, false);

        assertTrue(view.canConfirm());
        assertTrue(view.live());
        assertFalse(view.showCode());
        assertEquals(LinkCodeText.Text.of("heavymatica.link.confirm.identity", LinkCodeText.Text.of("heavymatica.link.party.github", "alex-builds"), "3:05"),
                view.status());
    }

    @Test
    void a_join_asks_to_join_the_redeeming_account_by_its_names() {
        LinkCode code = code(LinkCodeStatus.REDEEMED, Duration.ofMinutes(4), Duration.ofMinutes(5)).redeemer(ACCOUNT);

        LinkCodeText.View view = LinkCodeText.of(code, NOW, true);

        assertEquals(LinkCodeText.Text.of("heavymatica.link.confirm.account",
                LinkCodeText.Text.of("heavymatica.link.party.account", "Alex, Alex2, alex-builds (GitHub)"), "5:00"), view.status());
    }

    @Test
    void a_redemption_not_confirmed_in_time_reads_as_expired() {
        LinkCode code = code(LinkCodeStatus.REDEEMED, Duration.ofMinutes(4), Duration.ofSeconds(-1)).redeemer(GITHUB);

        LinkCodeText.View view = LinkCodeText.of(code, NOW, false);

        assertFalse(view.canConfirm());
        assertTrue(view.offerNewCode());
        assertEquals("heavymatica.link.status.expired", view.status().key());
    }

    @Test
    void finished_codes_say_how_they_ended() {
        assertEquals("heavymatica.link.status.linked", view(LinkCodeStatus.LINKED, false).status().key());
        assertEquals("heavymatica.link.status.joined", view(LinkCodeStatus.LINKED, true).status().key());
        assertEquals("heavymatica.link.status.declined", view(LinkCodeStatus.DECLINED, false).status().key());
        assertEquals("heavymatica.link.status.cancelled", view(LinkCodeStatus.CANCELLED, false).status().key());
        assertEquals("heavymatica.link.status.expired", view(LinkCodeStatus.EXPIRED, false).status().key());
        assertEquals("heavymatica.link.status.unknown", view(LinkCodeStatus.UNKNOWN_DEFAULT_OPEN_API, false).status().key());
        assertTrue(view(LinkCodeStatus.DECLINED, false).offerNewCode());
        assertFalse(view(LinkCodeStatus.LINKED, false).offerNewCode());
        assertFalse(view(LinkCodeStatus.LINKED, false).live());
    }

    @Test
    void nothing_is_shown_before_the_first_answer() {
        LinkCodeText.View view = LinkCodeText.of(null, NOW, false);

        assertFalse(view.showCode());
        assertFalse(view.live());
        assertEquals("heavymatica.link.status.loading", view.status().key());
    }

    @Test
    void countdowns_round_up_and_stop_at_zero() {
        assertEquals("4:05", LinkCodeText.countdown(Duration.ofMillis(244_100)));
        assertEquals("0:01", LinkCodeText.countdown(Duration.ofMillis(1)));
        assertEquals("0:00", LinkCodeText.countdown(Duration.ofSeconds(-30)));
    }

    @Test
    void parties_name_sign_ins_and_accounts_never_by_email() {
        assertEquals(LinkCodeText.Text.of("heavymatica.link.party.minecraft", "Steve"),
                LinkCodeText.party(new LinkParty().provider("minecraft").name("Steve")));
        assertEquals(LinkCodeText.Text.of("heavymatica.link.party.unknown"), LinkCodeText.party(null));
        assertEquals("alex (GitHub)", LinkCodeText.webSignIn("github:alex"));
    }

    @Test
    void problems_map_to_their_messages() {
        assertEquals("heavymatica.link.error.unavailable", LinkCodeText.problem(problem(503, ProblemCode.LINKING_UNAVAILABLE)).key());
        assertEquals("heavymatica.link.error.already_linked", LinkCodeText.problem(problem(409, ProblemCode.IDENTITY_ALREADY_LINKED)).key());
        assertEquals("heavymatica.link.error.too_many", LinkCodeText.problem(problem(409, ProblemCode.TOO_MANY_IDENTITIES)).key());
        assertEquals("heavymatica.link.error.last_identity", LinkCodeText.problem(problem(409, ProblemCode.LAST_IDENTITY)).key());
        assertEquals("heavymatica.link.error.rate_limited", LinkCodeText.problem(problem(429, ProblemCode.RATE_LIMITED)).key());
        assertEquals("heavymatica.link.error.unreachable", LinkCodeText.problem(new CloudException(0, null, "down", null)).key());
        assertEquals("heavymatica.link.error.unexpected", LinkCodeText.problem(problem(500, null)).key());
    }

    @Test
    void every_key_the_texts_use_is_translated() throws Exception {
        JsonNode lang;
        try (InputStream in = LinkCodeTextTest.class.getResourceAsStream("/assets/heavymatica/lang/en_us.json")) {
            lang = new ObjectMapper().readTree(in);
        }
        List<LinkCodeText.Text> texts = new ArrayList<>();
        for (LinkCodeStatus status : LinkCodeStatus.values()) {
            for (boolean joining : new boolean[] {false, true}) {
                texts.add(LinkCodeText.of(code(status, Duration.ofMinutes(5), Duration.ofMinutes(5)).redeemer(GITHUB), NOW, joining).status());
            }
        }
        texts.add(LinkCodeText.of(null, NOW, false).status());
        Stream.of(GITHUB, ACCOUNT, new LinkParty().provider("minecraft").name("Steve"), new LinkParty().provider("other").name("x"), null)
                .map(LinkCodeText::party).forEach(texts::add);
        for (ProblemCode code : ProblemCode.values()) {
            texts.add(LinkCodeText.problem(problem(400, code)));
        }
        texts.add(LinkCodeText.problem(new CloudException(0, null, "down", null)));

        texts.stream().flatMap(LinkCodeText::keys).distinct().forEach(key -> assertTrue(lang.has(key), "missing translation " + key));
    }

    private static LinkCodeText.View view(LinkCodeStatus status, boolean joining) {
        return LinkCodeText.of(code(status, Duration.ofMinutes(5), null).redeemer(GITHUB), NOW, joining);
    }

    private static LinkCode code(LinkCodeStatus status, Duration expiresIn, Duration confirmWithin) {
        return new LinkCode().id("code-1").code("K7QM-3XRP").status(status).expiresAt(NOW.plus(expiresIn).atOffset(ZoneOffset.UTC))
                .confirmBy(confirmWithin == null ? null : NOW.plus(confirmWithin).atOffset(ZoneOffset.UTC))
                .issuer(new LinkParty().provider("minecraft").name("Steve"));
    }

    private static CloudException problem(int status, ProblemCode code) {
        return new CloudException(status, code, "problem", null);
    }
}
