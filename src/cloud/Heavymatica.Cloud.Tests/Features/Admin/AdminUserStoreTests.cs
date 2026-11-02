using Heavymatica.Cloud.Features.Admin;
using Microsoft.AspNetCore.Identity;
using Microsoft.Extensions.DependencyInjection;

namespace Heavymatica.Cloud.Tests.Features.Admin;

[Trait("Category", "Integration")]
public sealed class AdminUserStoreTests(CloudInfrastructure infrastructure) : IDisposable
{
    private readonly CloudFactory factory = new(infrastructure);

    public void Dispose() => factory.Dispose();

    private static CancellationToken Cancel => TestContext.Current.CancellationToken;

    private static UserPasskeyInfo Passkey(byte id, uint signCount = 0) =>
        new([id, 1, 2, 3], [9, 9, 9], DateTimeOffset.UtcNow, signCount, ["internal"], true, false, false, [7], [8]) { Name = "Laptop" };

    [Fact]
    public async Task Identity_finds_admins_by_id_and_name_and_supports_only_passkeys()
    {
        using var scope = factory.Services.CreateScope();
        var users = scope.ServiceProvider.GetRequiredService<UserManager<AdminUser>>();
        var admin = new AdminUser { Id = AdminUser.NewId(), DisplayName = "Alice", CreatedAt = DateTime.UtcNow };

        Assert.True((await users.CreateAsync(admin)).Succeeded);

        Assert.Equal(admin.Id, (await users.FindByIdAsync(admin.Id))?.Id);
        Assert.Equal(admin.Id, (await users.FindByNameAsync(admin.Id.ToUpperInvariant()))?.Id);
        Assert.Null(await users.FindByIdAsync(AdminUser.NewId()));
        Assert.True(users.SupportsUserPasskey);
        Assert.False(users.SupportsUserPassword);
        Assert.False(users.SupportsUserEmail);
        Assert.False(users.SupportsUserLockout);
        Assert.False(users.SupportsUserPhoneNumber);
    }

    [Fact]
    public async Task Passkeys_are_added_found_updated_and_removed()
    {
        var store = new AdminUserStore(factory.Database, TimeProvider.System);
        var admin = new AdminUser { Id = AdminUser.NewId(), DisplayName = "Alice", CreatedAt = DateTime.UtcNow };
        var other = new AdminUser { Id = AdminUser.NewId(), DisplayName = "Bob", CreatedAt = DateTime.UtcNow };
        await store.CreateAsync(admin, Cancel);
        await store.CreateAsync(other, Cancel);

        await store.AddOrUpdatePasskeyAsync(admin, Passkey(1), Cancel);
        await store.AddOrUpdatePasskeyAsync(admin, Passkey(2), Cancel);
        await store.AddOrUpdatePasskeyAsync(admin, Passkey(1, signCount: 5), Cancel);

        var passkeys = await store.GetPasskeysAsync(admin, Cancel);
        Assert.Equal([5u, 0u], passkeys.OrderBy(passkey => passkey.CredentialId[0]).Select(passkey => passkey.SignCount));
        Assert.Equal(("Laptop", true), (passkeys[0].Name, passkeys[0].IsUserVerified));
        Assert.Equal([9, 9, 9], passkeys[0].PublicKey);
        Assert.Equal(admin.Id, (await store.FindByPasskeyIdAsync([1, 1, 2, 3], Cancel))?.Id);
        Assert.Null(await store.FindByPasskeyIdAsync([3, 1, 2, 3], Cancel));
        Assert.NotNull(await store.FindPasskeyAsync(admin, [2, 1, 2, 3], Cancel));
        Assert.Null(await store.FindPasskeyAsync(other, [2, 1, 2, 3], Cancel));

        await store.RemovePasskeyAsync(other, [2, 1, 2, 3], Cancel);
        Assert.Equal(2, (await store.GetPasskeysAsync(admin, Cancel)).Count);
        await store.RemovePasskeyAsync(admin, [2, 1, 2, 3], Cancel);
        Assert.Single(await store.GetPasskeysAsync(admin, Cancel));

        await store.DeleteAsync(admin, Cancel);
        Assert.Null(await store.FindByIdAsync(admin.Id, Cancel));
        Assert.Null(await store.FindByPasskeyIdAsync([1, 1, 2, 3], Cancel));
    }

    [Fact]
    public void The_passkey_relying_party_is_the_admin_host()
    {
        using var configured = new CloudFactory(infrastructure).With("Admin:Url", "http://localhost:5999").With("Admin:Host", "admin.heavymatica.example");

        var options = configured.Services.GetRequiredService<Microsoft.Extensions.Options.IOptions<IdentityPasskeyOptions>>().Value;

        Assert.Equal("admin.heavymatica.example", options.ServerDomain);
    }
}
