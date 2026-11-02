var builder = DistributedApplication.CreateBuilder(args);

// Tests run with ephemeral storage so their containers never share the development data volumes.
var persistent = !string.Equals(builder.Configuration["Heavymatica:EphemeralStorage"], "true", StringComparison.OrdinalIgnoreCase);
var withWebsite = !string.Equals(builder.Configuration["Heavymatica:WithWebsite"], "false", StringComparison.OrdinalIgnoreCase);
// GitHub's OAuth app matches the callback host and port exactly, so the website's dev port is fixed.
var websitePort = int.TryParse(builder.Configuration["Heavymatica:WebsitePort"], out var port) ? port : 4321;
// The admin listener and the admin site's dev server; passkeys for localhost need no TLS.
var adminPort = int.TryParse(builder.Configuration["Heavymatica:AdminPort"], out var configuredAdminPort) ? configuredAdminPort : 5181;
var adminSitePort = int.TryParse(builder.Configuration["Heavymatica:AdminSitePort"], out var configuredAdminSitePort) ? configuredAdminSitePort : 4322;

var mongo = builder.AddMongoDB("mongo");
var database = mongo.AddDatabase("heavymatica");

var minioUser = builder.AddParameter("minio-user", "heavymatica");
var minioPassword = builder.AddParameter("minio-password", "heavymatica-dev-secret", secret: true);
// MinIO no longer publishes community images on Docker Hub; Chainguard's build is freely pullable.
var minio = builder.AddMinioContainer("minio", minioUser, minioPassword)
    .WithImageRegistry("cgr.dev")
    .WithImage("chainguard/minio")
    .WithImageTag("latest");

if (persistent)
{
    mongo.WithDataVolume();
    minio.WithDataVolume();
}

// Empty unless configured (Parameters:github-client-id and Parameters:github-client-secret, for example in user
// secrets): without a GitHub OAuth app the website sign-in is unavailable and everything else runs. A parameter with
// a fixed value ignores configuration, so the value is read here.
var gitHubClientId = builder.AddParameter("github-client-id", builder.Configuration["Parameters:github-client-id"] ?? string.Empty);
var gitHubClientSecret = builder.AddParameter("github-client-secret", builder.Configuration["Parameters:github-client-secret"] ?? string.Empty,
    secret: true);

// Explicit configuration instead of Aspire client integrations: the service must also run outside Aspire.
var cloud = builder.AddProject<Projects.Heavymatica_Cloud>("cloud");
cloud
    .WithReference(database)
    .WaitFor(database)
    .WaitFor(minio)
    // Browsers send every localhost cookie to every localhost port, and MinIO refuses requests whose headers grow too
    // large (MetadataTooLarge), so presigned links name 127.0.0.1, which gets no localhost cookies.
    .WithEnvironment("S3__ServiceUrl", ReferenceExpression.Create($"http://127.0.0.1:{minio.GetEndpoint("http").Property(EndpointProperty.Port)}"))
    .WithEnvironment("S3__AccessKey", minioUser)
    .WithEnvironment("S3__SecretKey", minioPassword)
    .WithEnvironment("S3__Bucket", "heavymatica")
    // Locally the service asks its own DevMojang slice, so dev clients without a Microsoft session can sign in.
    .WithEnvironment("Identity__MojangSessionUrl", ReferenceExpression.Create($"{cloud.GetEndpoint("http")}/dev/mojang"))
    .WithEnvironment("GitHub__ClientId", gitHubClientId)
    .WithEnvironment("GitHub__ClientSecret", gitHubClientSecret)
    // Link codes point at the website's dev server; setting it turns on linking and pending sign-ins.
    .WithEnvironment("Accounts__LinkBaseUrl", "http://localhost:" + websitePort)
    .WithEnvironment("Admin__Url", "http://localhost:" + adminPort)
    .WithEnvironment("Admin__Host", "localhost")
    .WithEnvironment("Admin__SiteUrl", "http://localhost:" + adminSitePort)
    // The service reports itself closed unless configured; the local cloud is always open to sign-ins.
    .WithEnvironment("Status__SignIn", "open")
    .WithHttpHealthCheck("/health");

if (withWebsite)
{
    // The dev server proxies /v1 to the cloud with the browser's Host header, so pages and API share one origin.
    builder.AddJavaScriptApp("website", "../../web/player", "dev")
        .WithHttpEndpoint(port: websitePort, env: "PORT", isProxied: false)
        .WithEnvironment("HEAVYMATICA_CLOUD_URL", cloud.GetEndpoint("http"))
        .WaitFor(cloud);

    // Proxies /admin/v1 to the admin listener with the browser's Host header, so passkeys see one origin.
    builder.AddJavaScriptApp("admin-website", "../../web/admin", "dev")
        .WithHttpEndpoint(port: adminSitePort, env: "PORT", isProxied: false)
        .WithEnvironment("HEAVYMATICA_ADMIN_URL", "http://localhost:" + adminPort)
        .WaitFor(cloud);
}

builder.Build().Run();
