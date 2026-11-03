package io.github.dirnei.mc.heavymatica.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dirnei.mc.heavymatica.cloud.client.ApiClient;
import io.github.dirnei.mc.heavymatica.cloud.model.Account;
import io.github.dirnei.mc.heavymatica.cloud.model.Problem;
import io.github.dirnei.mc.heavymatica.cloud.model.ProblemCode;
import org.junit.jupiter.api.Test;

/** Answers of newer clouds must still deserialize: unknown enum values and fields, and fields that became optional. */
class ContractCompatibilityTest {

    private final ObjectMapper json = ApiClient.createDefaultObjectMapper();

    @Test
    void a_problem_with_an_unknown_code_deserializes() throws Exception {
        Problem problem = json.readValue("{\"code\":\"something_new\",\"title\":\"New\",\"status\":418}", Problem.class);

        assertEquals(ProblemCode.UNKNOWN_DEFAULT_OPEN_API, problem.getCode());
        assertEquals(418, problem.getStatus());
    }

    @Test
    void an_account_without_uuid_and_with_extra_fields_deserializes() throws Exception {
        Account account = json.readValue("{\"id\":\"0123456789abcdef0123456789abcdef\",\"provider\":\"github\",\"name\":\"octocat\","
                + "\"usedBytes\":10,\"quotaBytes\":100,\"somethingNew\":{\"nested\":true}}", Account.class);

        assertNull(account.getUuid());
        assertEquals("octocat", account.getName());
        assertEquals("github", account.getProvider());
        assertEquals(100L, account.getQuotaBytes());
    }

    @Test
    void cloud_exception_keeps_an_unknown_code_readable() {
        CloudException error = CloudException.fromResponse(403, "{\"code\":\"something_new\",\"title\":\"New\",\"status\":403,\"detail\":\"No.\"}",
                json, null);

        assertEquals(403, error.status());
        assertEquals(ProblemCode.UNKNOWN_DEFAULT_OPEN_API, error.code().orElseThrow());
        assertEquals("No.", error.getMessage());
    }
}
