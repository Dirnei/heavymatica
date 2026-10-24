using Akka.Hosting;
using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Features.Schematics.Delete;
using Heavymatica.Cloud.Features.Schematics.Details;
using Heavymatica.Cloud.Features.Schematics.Folders;
using Heavymatica.Cloud.Features.Schematics.Links;
using Heavymatica.Cloud.Features.Schematics.Listing;
using Heavymatica.Cloud.Features.Schematics.Organize;
using Heavymatica.Cloud.Features.Schematics.Sweeper;
using Heavymatica.Cloud.Features.Schematics.Tags;
using Heavymatica.Cloud.Features.Schematics.Thumbnails;
using Heavymatica.Cloud.Features.Schematics.Upload;
using Heavymatica.Cloud.Infrastructure.Mongo;

namespace Heavymatica.Cloud.Features.Schematics;

internal static class SchematicServices
{
    public static WebApplicationBuilder AddSchematics(this WebApplicationBuilder builder)
    {
        var services = builder.Services;
        services.AddOptions<StorageOptions>().BindConfiguration(StorageOptions.Section);
        services.AddMongoIndexes<SchematicIndexes>();
        services.AddSingleton<Quota>();
        services.AddSingleton<UploadSpool>();
        services.AddSingleton<UploadSchematic>();
        services.AddSingleton<ListSchematics>();
        services.AddSingleton<GetSchematic>();
        services.AddSingleton<OrganizeSchematic>();
        services.AddSingleton<MoveSchematics>();
        services.AddSingleton<ListFolders>();
        services.AddSingleton<RenameFolder>();
        services.AddSingleton<DeleteSchematics>();
        services.AddSingleton<AttachThumbnail>();
        services.AddSingleton<CreateLinks>();
        services.AddSingleton<ShowThumbnail>();
        services.AddSingleton<ListTags>();
        services.AddSingleton<BlobSweeper>();

        services.AddAkka("heavymatica", (akka, provider) => akka.WithActors((system, registry, resolver) =>
            registry.Register<BlobSweeperActor>(system.ActorOf(resolver.Props<BlobSweeperActor>(), "blob-sweeper"))));
        return builder;
    }
}
