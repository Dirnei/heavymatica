var builder = DistributedApplication.CreateBuilder(args);

// Tests run with ephemeral storage so their containers never share the development data volumes.
var persistent = !string.Equals(builder.Configuration["Heavymatica:EphemeralStorage"], "true", StringComparison.OrdinalIgnoreCase);

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

// The service gets explicit configuration instead of Aspire client integrations,
// so it runs the same way outside Aspire.
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
    .WithHttpHealthCheck("/health");

builder.Build().Run();
