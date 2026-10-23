var builder = DistributedApplication.CreateBuilder(args);

// Tests run with ephemeral storage so their containers never share the development data volumes.
var persistent = !string.Equals(builder.Configuration["Heavymatica:EphemeralStorage"], "true", StringComparison.OrdinalIgnoreCase);
var withWebsite = !string.Equals(builder.Configuration["Heavymatica:WithWebsite"], "false", StringComparison.OrdinalIgnoreCase);
// GitHub's OAuth app matches the callback host and port exactly, so the website's dev port is fixed.
var websitePort = int.TryParse(builder.Configuration["Heavymatica:WebsitePort"], out var port) ? port : 4321;

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
    .WithEnvironment("S3__ServiceUrl", minio.GetEndpoint("http"))
    .WithEnvironment("S3__AccessKey", minioUser)
    .WithEnvironment("S3__SecretKey", minioPassword)
    .WithEnvironment("S3__Bucket", "heavymatica")
    // Locally the service asks its own DevMojang slice, so dev clients without a Microsoft session can sign in.
    .WithEnvironment("Identity__MojangSessionUrl", ReferenceExpression.Create($"{cloud.GetEndpoint("http")}/dev/mojang"))
    .WithEnvironment("GitHub__ClientId", gitHubClientId)
    .WithEnvironment("GitHub__ClientSecret", gitHubClientSecret)
    .WithHttpHealthCheck("/health");

if (withWebsite)
{
    // The dev server proxies /v1 to the cloud with the browser's Host header, so pages and API share one origin.
    builder.AddJavaScriptApp("website", "../../web/player", "dev")
        .WithHttpEndpoint(port: websitePort, env: "PORT", isProxied: false)
        .WithEnvironment("HEAVYMATICA_CLOUD_URL", cloud.GetEndpoint("http"))
        .WaitFor(cloud);
}

builder.Build().Run();
