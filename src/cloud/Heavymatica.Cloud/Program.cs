using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Features.Admin;
using Heavymatica.Cloud.Features.Identity;
using Heavymatica.Cloud.Features.Links;
using Heavymatica.Cloud.Features.Schematics;
using Heavymatica.Cloud.Features.CloudStatus;
using Heavymatica.Cloud.Features.Web;
using Heavymatica.Cloud.Infrastructure;
using Heavymatica.Cloud.Infrastructure.Contract;
using Heavymatica.Cloud.Infrastructure.Mongo;
using Heavymatica.Cloud.Infrastructure.S3;

if (SetupCodeCommand.Parse(args) is { } setupCode)
{
    return await SetupCodeCommand.RunAsync(setupCode, Console.Out, Console.Error);
}

var builder = WebApplication.CreateBuilder(args);

builder.AddServiceDefaults();
builder.AddMongo();
builder.AddBlobStore();
builder.AddAccounts();
builder.AddIdentity();
builder.AddSchematics();
builder.AddWeb();
builder.AddLinks();
builder.AddAdmin();
builder.AddStatus();
builder.Services.AddContract();

var app = builder.Build();

app.UseWebsite();
app.UseAdminSite();
app.UseRouting();
app.UseAdminListener();
app.UseAuthentication();
app.UseWebRequestCheck();
app.UseRateLimiter();
app.UseAuthorization();

app.MapServiceDefaults();
app.MapDevMojang();
app.MapWeb();
app.MapContract();

await app.RunAsync();
return 0;
