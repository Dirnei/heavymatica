using System.Buffers.Binary;
using System.Buffers.Text;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace Heavymatica.Cloud.Tests.Features.Admin;

/// <summary>
/// A passkey authenticator in memory, like a browser and its platform authenticator together: ES256 credentials with
/// <c>none</c> attestation, user presence and verification always set. It reads the WebAuthn JSON options the API
/// returns and answers with the credential JSON the browser would send, for the given origin.
/// </summary>
internal sealed class SoftwareAuthenticator : IDisposable
{
    private const byte UserPresent = 0x01;
    private const byte UserVerified = 0x04;
    private const byte AttestedCredentialData = 0x40;

    private readonly List<StoredCredential> credentials = [];

    public sealed class StoredCredential(byte[] id, ECDsa key, string rpId, byte[] userHandle)
    {
        public byte[] Id { get; } = id;

        public string IdText => Base64Url.EncodeToString(Id);

        public ECDsa Key { get; } = key;

        public string RpId { get; } = rpId;

        public byte[] UserHandle { get; } = userHandle;

        public uint SignCount { get; set; }
    }

    public IReadOnlyList<StoredCredential> Credentials => credentials;

    /// <summary>A new credential for <c>navigator.credentials.create</c> options.</summary>
    public JsonObject Create(JsonElement options, string origin)
    {
        var challenge = options.GetProperty("challenge").GetString()!;
        var rpId = options.GetProperty("rp").GetProperty("id").GetString()!;
        var userHandle = Base64Url.DecodeFromChars(options.GetProperty("user").GetProperty("id").GetString()!);

        var key = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var credential = new StoredCredential(RandomNumberGenerator.GetBytes(16), key, rpId, userHandle);
        credentials.Add(credential);

        var parameters = key.ExportParameters(false);
        var publicKey = Cbor.Encode(Cbor.Map((1, 2), (3, -7), (-1, 1), (-2, parameters.Q.X!), (-3, parameters.Q.Y!)));
        var attested = new List<byte>();
        attested.AddRange(new byte[16]);
        attested.Add((byte)(credential.Id.Length >> 8));
        attested.Add((byte)credential.Id.Length);
        attested.AddRange(credential.Id);
        attested.AddRange(publicKey);
        var authenticatorData = AuthenticatorData(rpId, UserPresent | UserVerified | AttestedCredentialData, 0, [.. attested]);
        var attestationObject = Cbor.Encode(Cbor.Map(("fmt", "none"), ("attStmt", Cbor.Map()), ("authData", authenticatorData)));

        return new JsonObject
        {
            ["id"] = credential.IdText,
            ["rawId"] = credential.IdText,
            ["type"] = "public-key",
            ["response"] = new JsonObject
            {
                ["clientDataJSON"] = Base64Url.EncodeToString(ClientData("webauthn.create", challenge, origin)),
                ["attestationObject"] = Base64Url.EncodeToString(attestationObject),
                ["transports"] = new JsonArray("internal"),
            },
            ["clientExtensionResults"] = new JsonObject(),
            ["authenticatorAttachment"] = "platform",
        };
    }

    /// <summary>
    /// An assertion for <c>navigator.credentials.get</c> options, with the given credential or the first one for the
    /// options' relying party. <paramref name="rpIdOverride"/> signs for another relying party, as a phishing site's
    /// passkey would.
    /// </summary>
    public JsonObject Get(JsonElement options, string origin, StoredCredential? credential = null, string? rpIdOverride = null)
    {
        var challenge = options.GetProperty("challenge").GetString()!;
        var rpId = options.GetProperty("rpId").GetString()!;
        credential ??= credentials.First(candidate => candidate.RpId == rpId);
        credential.SignCount++;

        var clientData = ClientData("webauthn.get", challenge, origin);
        var authenticatorData = AuthenticatorData(rpIdOverride ?? credential.RpId, UserPresent | UserVerified, credential.SignCount, []);
        var signature = credential.Key.SignData([.. authenticatorData, .. SHA256.HashData(clientData)], HashAlgorithmName.SHA256,
            DSASignatureFormat.Rfc3279DerSequence);

        return new JsonObject
        {
            ["id"] = credential.IdText,
            ["rawId"] = credential.IdText,
            ["type"] = "public-key",
            ["response"] = new JsonObject
            {
                ["clientDataJSON"] = Base64Url.EncodeToString(clientData),
                ["authenticatorData"] = Base64Url.EncodeToString(authenticatorData),
                ["signature"] = Base64Url.EncodeToString(signature),
                ["userHandle"] = Base64Url.EncodeToString(credential.UserHandle),
            },
            ["clientExtensionResults"] = new JsonObject(),
            ["authenticatorAttachment"] = "platform",
        };
    }

    public void Dispose() => credentials.ForEach(credential => credential.Key.Dispose());

    private static byte[] ClientData(string type, string challenge, string origin) =>
        Encoding.UTF8.GetBytes(JsonSerializer.Serialize(new { type, challenge, origin, crossOrigin = false }));

    private static byte[] AuthenticatorData(string rpId, int flags, uint signCount, byte[] attested)
    {
        var counter = new byte[4];
        BinaryPrimitives.WriteUInt32BigEndian(counter, signCount);
        return [.. SHA256.HashData(Encoding.UTF8.GetBytes(rpId)), (byte)flags, .. counter, .. attested];
    }
}
