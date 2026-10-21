namespace Heavymatica.Cloud.Tests;

/// <summary>Locates files shared across the repository, such as the contract and the conformance set.</summary>
internal static class RepositoryFiles
{
    public static string SourceRoot { get; } = FindSourceRoot();

    public static string Contract => Path.Combine(SourceRoot, "api", "heavymatica-cloud.v1.yaml");

    public static string Conformance => Path.Combine(SourceRoot, "conformance");

    private static string FindSourceRoot()
    {
        for (var directory = new DirectoryInfo(AppContext.BaseDirectory); directory is not null; directory = directory.Parent)
        {
            if (File.Exists(Path.Combine(directory.FullName, "api", "heavymatica-cloud.v1.yaml")))
            {
                return directory.FullName;
            }
        }

        throw new InvalidOperationException("Could not find the src/ directory above " + AppContext.BaseDirectory);
    }
}
