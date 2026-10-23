using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Features.Identity;
using Heavymatica.Cloud.Features.Schematics;
using Heavymatica.Cloud.Features.Web;
using Heavymatica.Cloud.Infrastructure;
using Heavymatica.Cloud.Infrastructure.Contract;
using Heavymatica.Cloud.Infrastructure.Mongo;
using Heavymatica.Cloud.Infrastructure.S3;

var builder = WebApplication.CreateBuilder(args);

builder.AddServiceDefaults();
builder.AddMongo();
builder.AddBlobStore();
builder.AddAccounts();
builder.AddIdentity();
builder.AddSchematics();
builder.AddWeb();
builder.Services.AddContract();

var app = builder.Build();

app.UseWebsite();
app.UseRouting();
app.UseAuthentication();
app.UseWebRequestCheck();
app.UseRateLimiter();
app.UseAuthorization();

app.MapServiceDefaults();
app.MapDevMojang();
app.MapWeb();
app.MapContract();

app.Run();
