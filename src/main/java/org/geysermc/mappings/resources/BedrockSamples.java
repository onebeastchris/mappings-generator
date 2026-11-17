package org.geysermc.mappings.resources;

import com.google.common.hash.Hashing;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.util.Util;
import org.geysermc.mappings.FileSystemAccess;
import org.geysermc.mappings.util.MappingsUtil;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/// This class downloads and manages access to [Mojang/bedrock-samples](https://github.com/Mojang/bedrock-samples), also known as `bedrock-samples`.
///
/// It can be accessed through the {@link BedrockSamples#openSamples(FileSystemUser)} method. All files in here must be interacted through using {@link FileSystemAccess} and {@link org.geysermc.mappings.FileType}s.
/// However, if the {@link FileSystemAccess} API does not provide your use case, you may also use {@link BedrockSamples#openSamplesRaw(RawFileSystemUser)} to interact with a {@link FileSystem} directly (not preferred).
///
/// Bedrock block states and items do not come from here, but from Paletter: see {@link MappingsUtil#bedrockLookup()}.
public final class BedrockSamples {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path SAMPLES = Path.of("bedrock-samples.zip");

    private static final String SAMPLES_RELEASE_INFO_BASE_URL = "https://api.github.com/repos/Mojang/bedrock-samples/releases/tags/v";

    private final Path samplesPath;

    public BedrockSamples(Path samplesPath) {
        this.samplesPath = samplesPath;
    }

    private <T> CompletableFuture<T> openFileSystemRaw(Path path, RawFileSystemUser<T> user) {
        return CompletableFuture.supplyAsync(() -> {
            try (FileSystem fileSystem = FileSystems.newFileSystem(path)) {
                return user.use(fileSystem);
            } catch (IOException exception) {
                throw new UncheckedIOException("Failed to open " + path.getFileName().toString(), exception);
            }
        }, Util.backgroundExecutor().forName("BedrockSamples#open"));
    }

    /// Prefer using {@link BedrockSamples#openSamples(FileSystemUser)} when possible.
    public <T> CompletableFuture<T> openSamplesRaw(RawFileSystemUser<T> user) {
        return openFileSystemRaw(samplesPath, user);
    }

    private <T> CompletableFuture<T> openFileSystem(Path path, Function<FileSystem, Path> rootGetter, FileSystemUser<T> user) {
        return openFileSystemRaw(path, system -> {
            Path root = rootGetter.apply(system);
            return user.use(() -> root).join();
        });
    }

    public <T> CompletableFuture<T> openSamples(FileSystemUser<T> user) {
        return openFileSystem(samplesPath, system -> system.getPath("/"), user);
    }

    @FunctionalInterface
    public interface RawFileSystemUser<T> {

        T use(FileSystem system) throws IOException;
    }

    @FunctionalInterface
    public interface FileSystemUser<T> {

        CompletableFuture<T> use(FileSystemAccess access) throws IOException;
    }

    public static CompletableFuture<BedrockSamples> load(Path root) {
        // Load bedrock version from FMJ fields
        String bedrockVersion = MappingsUtil.getBedrockVersion();

        // Try to load the samples asynchronously
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        return tryToLoadBedrockSamples(client, bedrockVersion, root).thenApply(BedrockSamples::new);
    }

    private static CompletableFuture<Path> tryToLoadBedrockSamples(HttpClient client, String bedrockVersion, Path root) {
        // Try to gather GH release info of bedrock version
        return tryToGatherSamplesReleaseInfo(client, bedrockVersion).thenCompose(release -> {
            Path destination = root.resolve(SAMPLES);
            // If there's already a pack downloaded, compare hashes with GH release info
            if (Files.exists(destination)) {
                try {
                    if (sha256File(destination).equals(release.digest())) {
                        LOGGER.info("bedrock-samples hash for release matches local pack, not re-downloading...");
                        client.close();
                        return CompletableFuture.completedFuture(destination);
                    }
                    Files.delete(destination);
                } catch (IOException exception) {
                    LOGGER.error("Failed to compute hash of existing bedrock-samples! Attempting to re-download...", exception);
                }
            }

            // Try to download if there is no pack or the hashes don't line up
            return tryToDownloadSamples(client, release, destination).thenApply(_ -> {
                client.close();

                // Now we compare hashes again and fail if they (still) don't line up
                String sha256;
                try {
                    sha256 = sha256File(destination);
                } catch (IOException exception) {
                    throw new UncheckedIOException("Failed to compute hash of downloaded bedrock-samples!", exception);
                }
                if (sha256.equals(release.digest)) {
                    return destination;
                }
                throw new IllegalStateException("Hashes of downloaded bedrock-samples and GitHub don't line up!\n" +
                        "GitHub: " + release.digest + "\n" +
                        "Local: " + sha256);
            });
        });
    }

    private static CompletableFuture<ReleaseInfo> tryToGatherSamplesReleaseInfo(HttpClient client, String bedrockVersion) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(SAMPLES_RELEASE_INFO_BASE_URL + bedrockVersion)).build();
        LOGGER.info("Gathering release info for bedrock-samples version {}", bedrockVersion);
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Received status code " + response.statusCode() + " when trying to gather bedrock-samples release info for bedrock version " + bedrockVersion);
            }
            try {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                JsonArray assets = json.getAsJsonArray("assets");

                for (JsonElement asset : assets) {
                    String name = asset.getAsJsonObject().get("name").getAsString();
                    if (name.endsWith("-min.zip")) {
                        return new ReleaseInfo(URI.create(asset.getAsJsonObject().get("browser_download_url").getAsString()),
                                asset.getAsJsonObject().get("digest").getAsString().replaceFirst("sha256:", ""), bedrockVersion);
                    }
                }
                throw new IllegalStateException("Unable to find asset that ends with -min.zip");
            } catch (Exception exception) {
                throw new RuntimeException("Caught exception when trying to gather bedrock-samples release info for bedrock version " + bedrockVersion, exception);
            }
        });
    }

    private static CompletableFuture<Path> tryToDownloadSamples(HttpClient client, ReleaseInfo release, Path destination) {
        HttpRequest request = HttpRequest.newBuilder(release.uri).build();
        LOGGER.info("Downloading bedrock-samples version {}", release.version);
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofFile(destination)).thenApply(response -> {
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Received status code " + response.statusCode() + " when trying to download bedrock-samples for bedrock version " + release.version);
            }
            return response.body();
        });
    }

    private static String sha256File(Path file) throws IOException {
        return Hashing.sha256().hashBytes(Files.readAllBytes(file)).toString();
    }

    private record ReleaseInfo(URI uri, String digest, String version) {}
}
