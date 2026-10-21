using Heavymatica.Cloud.Features.Identity;
using Heavymatica.Cloud.Features.Schematics;
using Heavymatica.Cloud.Infrastructure;
using Heavymatica.Cloud.Infrastructure.Contract;
using Heavymatica.Cloud.Infrastructure.Mongo;
using Heavymatica.Cloud.Infrastructure.S3;

var builder = WebApplication.CreateBuilder(args);

builder.AddServiceDefaults();
builder.AddMongo();
builder.AddBlobStore();
builder.AddIdentity();
builder.AddSchematics();
builder.Services.AddContract();

var app = builder.Build();

app.UseAuthentication();
app.UseRateLimiter();
app.UseAuthorization();

app.MapServiceDefaults();
app.MapDevMojang();
app.MapContract();

app.Run();
