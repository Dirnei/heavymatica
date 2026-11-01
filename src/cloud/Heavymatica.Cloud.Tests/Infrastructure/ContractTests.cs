using System.Net;
using System.Reflection;
using Heavymatica.Cloud.Infrastructure;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.DependencyInjection;

namespace Heavymatica.Cloud.Tests.Infrastructure;

public sealed class ContractTests(CloudInfrastructure infrastructure) : IDisposable
{
    private readonly CloudFactory factory = new(infrastructure);

    public void Dispose() => factory.Dispose();

    private static readonly Assembly ServiceCode = typeof(ServiceAssembly).Assembly;

    [Fact]
    public void Every_generated_controller_interface_has_an_implementation()
    {
        var interfaces = ServiceCode.GetTypes()
            .Where(type => type.IsInterface && type.Namespace == "Heavymatica.Cloud.Contract" && type.Name.EndsWith("Controller", StringComparison.Ordinal))
            .ToList();

        using var scope = factory.Services.CreateScope();
        var missing = interfaces.Where(type => scope.ServiceProvider.GetService(type) is null).Select(type => type.Name).ToList();

        Assert.NotEmpty(interfaces);
        Assert.Empty(missing);
    }

    [Fact]
    public void Every_generated_controller_forwards_to_its_interface()
    {
        var controllers = ServiceCode.GetTypes()
            .Where(type => type.Namespace == "Heavymatica.Cloud.Contract" && typeof(ControllerBase).IsAssignableFrom(type))
            .ToList();

        Assert.Equal(["AccountController", "AuthController", "FoldersController", "LinksController", "SchematicsController", "TagsController", "WebController"], controllers.Select(type => type.Name).Order());
        foreach (var controller in controllers)
        {
            var parameter = Assert.Single(Assert.Single(controller.GetConstructors()).GetParameters());
            Assert.Equal("I" + controller.Name, parameter.ParameterType.Name);
        }
    }

    [Fact]
    public async Task Served_contract_is_byte_identical_to_the_source_file()
    {
        using var client = factory.CreateClient();

        var served = await client.GetByteArrayAsync(new Uri("/openapi/v1.yaml", UriKind.Relative), TestContext.Current.CancellationToken);

        Assert.Equal(await File.ReadAllBytesAsync(RepositoryFiles.Contract, TestContext.Current.CancellationToken), served);
    }
}
