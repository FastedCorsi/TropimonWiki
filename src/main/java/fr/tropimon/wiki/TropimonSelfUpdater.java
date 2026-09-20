package fr.tropimon.wiki;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;

/** Updater autonome de ce mod. Aucune classe d'un autre mod Tropimon n'est requise. */
final class TropimonSelfUpdater {
  private static final String MOD_ID = "tropimon_wiki";
  private static final String REPOSITORY = "TropimonWiki";
  private static final String RELEASE_API =
      "https://api.github.com/repos/FastedCorsi/" + REPOSITORY + "/releases/latest";
  private static final String RELEASE_DOWNLOAD_PREFIX =
      "https://github.com/FastedCorsi/" + REPOSITORY + "/releases/download/";
  private static final Duration CHECK_INTERVAL = Duration.ofHours(6);
  private static final long MAX_JAR_SIZE = 64L * 1024L * 1024L;
  private static final HttpClient HTTP =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .followRedirects(HttpClient.Redirect.NORMAL)
          .build();

  private TropimonSelfUpdater() {}

  static void start(Logger logger) {
    if (Boolean.getBoolean("tropimon.smoke")
        || FabricLoader.getInstance().isDevelopmentEnvironment()
        || !enabled(logger)) return;
    CompletableFuture.runAsync(() -> check(logger))
        .exceptionally(
            failure -> {
              logger.warn(
                  "Mise a jour automatique {} indisponible pour cette session ({}).",
                  MOD_ID,
                  rootCause(failure).getClass().getSimpleName());
              return null;
            });
  }

  private static boolean enabled(Logger logger) {
    Path config = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + "-updater.json");
    try {
      if (Files.notExists(config)) {
        Files.createDirectories(config.getParent());
        Files.writeString(config, "{\n  \"enabled\": true\n}\n", StandardCharsets.UTF_8);
        return true;
      }
      JsonObject json = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
      return !json.has("enabled") || json.get("enabled").getAsBoolean();
    } catch (Exception failure) {
      logger.warn("Configuration de mise a jour {} illisible; mise a jour desactivee.", MOD_ID);
      return false;
    }
  }

  private static void check(Logger logger) {
    if (!isWindows()) {
      logger.info(
          "Mise a jour automatique {}: installation differee disponible sous Windows uniquement.",
          MOD_ID);
      return;
    }
    ModContainer container = FabricLoader.getInstance().getModContainer(MOD_ID).orElse(null);
    if (container == null) return;
    Path installedJar = installedJar(container);
    // Fabric's loaded origin identifies the actual profile; the launcher root may be a mirror.
    if (installedJar != null && !installedJar.getParent().getFileName().toString().equals("mods"))
      return;
    if (installedJar == null || recentlyChecked()) return;

    try {
      markChecked();
      JsonObject release = requestJson(RELEASE_API);
      if (release.get("draft").getAsBoolean() || release.get("prerelease").getAsBoolean()) return;
      String currentVersion = container.getMetadata().getVersion().getFriendlyString();
      String releaseVersion = release.get("tag_name").getAsString().replaceFirst("^[vV]", "");
      if (compareVersions(releaseVersion, currentVersion) <= 0) {
        markChecked();
        return;
      }
      ReleaseAsset jarAsset = selectJar(release.getAsJsonArray("assets"));
      if (jarAsset == null) {
        throw new IOException("Release assets incomplete");
      }
      ReleaseAsset checksumAsset =
          selectChecksum(release.getAsJsonArray("assets"), jarAsset.name());
      if (checksumAsset == null) throw new IOException("Release assets incomplete");

      Path updateDir =
          installedJar.getParent().getParent().resolve("config/.tropimon-updates").resolve(MOD_ID);
      Files.createDirectories(updateDir);
      String expectedHash = requestText(checksumAsset.url(), 512).trim().split("\\s+", 2)[0];
      if (!expectedHash.matches("(?i)[0-9a-f]{64}")) {
        throw new IOException("Invalid SHA-256 sidecar");
      }

      Path staged = updateDir.resolve(jarAsset.name());
      download(jarAsset.url(), staged, MAX_JAR_SIZE);
      String downloadedHash = sha256(staged);
      if (!downloadedHash.equalsIgnoreCase(expectedHash)) {
        Files.deleteIfExists(staged);
        throw new IOException("SHA-256 mismatch");
      }

      JarMetadata metadata = inspectJar(staged);
      if (!MOD_ID.equals(metadata.id()) || !metadata.version().equals(releaseVersion)) {
        Files.deleteIfExists(staged);
        throw new IOException("Unexpected mod id");
      }
      if (compareVersions(metadata.version(), currentVersion) <= 0) {
        Files.deleteIfExists(staged);
        markChecked();
        return;
      }

      Path installer = updateDir.resolve("install-after-minecraft.ps1");
      Path instance = installedJar.getParent().getParent();
      boolean managed =
          Files.exists(instance.resolve("mods-user"))
              || Files.exists(instance.getParent().resolve("user-mods-tracked.json"));
      Files.writeString(
          staged.resolveSibling(staged.getFileName() + ".sha256"),
          downloadedHash + "\n",
          StandardCharsets.UTF_8);
      Files.writeString(
          installer,
          managed ? WINDOWS_MANAGED_INSTALLER : WINDOWS_INSTALLER,
          StandardCharsets.UTF_8);
      armInstaller(installer, staged, installedJar, sha256(installedJar), downloadedHash);
      logger.info(
          "Mise a jour {} {} preparee; installation automatique apres l'arret de Minecraft.",
          MOD_ID,
          metadata.version());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    } catch (Exception failure) {
      logger.warn(
          "Verification de mise a jour {} echouee ({}).",
          MOD_ID,
          failure.getClass().getSimpleName());
    }
  }

  private static Path installedJar(ModContainer container) {
    return container.getOrigin().getPaths().stream()
        .filter(Files::isRegularFile)
        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
        .findFirst()
        .map(Path::toAbsolutePath)
        .map(Path::normalize)
        .orElse(null);
  }

  private static boolean recentlyChecked() {
    Path marker = marker();
    try {
      if (Files.notExists(marker)) return false;
      Instant checkedAt = Instant.parse(Files.readString(marker).trim());
      return checkedAt.plus(CHECK_INTERVAL).isAfter(Instant.now());
    } catch (Exception ignored) {
      return false;
    }
  }

  private static void markChecked() throws IOException {
    Path marker = marker();
    Files.createDirectories(marker.getParent());
    Files.writeString(marker, Instant.now().toString(), StandardCharsets.UTF_8);
  }

  private static Path marker() {
    return FabricLoader.getInstance()
        .getConfigDir()
        .resolve(".tropimon-updates")
        .resolve(MOD_ID)
        .resolve("last-check.txt");
  }

  private static JsonObject requestJson(String url) throws IOException, InterruptedException {
    String body = requestText(url, 2 * 1024 * 1024);
    return JsonParser.parseString(body).getAsJsonObject();
  }

  private static String requestText(String url, long maxBytes)
      throws IOException, InterruptedException {
    HttpRequest request = request(url);
    HttpResponse<InputStream> response =
        HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
    if (response.statusCode() != 200) {
      response.body().close();
      throw new IOException("HTTP " + response.statusCode());
    }
    try (InputStream input = response.body()) {
      return new String(readLimited(input, maxBytes), StandardCharsets.UTF_8);
    }
  }

  private static void download(String url, Path destination, long maxBytes)
      throws IOException, InterruptedException {
    HttpResponse<InputStream> response =
        HTTP.send(request(url), HttpResponse.BodyHandlers.ofInputStream());
    if (response.statusCode() != 200) {
      response.body().close();
      throw new IOException("HTTP " + response.statusCode());
    }
    Path temporary = destination.resolveSibling(destination.getFileName() + ".part");
    try (InputStream input = response.body();
        var output = Files.newOutputStream(temporary)) {
      byte[] buffer = new byte[16 * 1024];
      long total = 0;
      int read;
      while ((read = input.read(buffer)) >= 0) {
        total += read;
        if (total > maxBytes) throw new IOException("Download too large");
        output.write(buffer, 0, read);
      }
    } catch (IOException | RuntimeException failure) {
      Files.deleteIfExists(temporary);
      throw failure;
    }
    Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
  }

  private static HttpRequest request(String url) throws IOException {
    if (!(url.equals(RELEASE_API) || url.startsWith(RELEASE_DOWNLOAD_PREFIX))) {
      throw new IOException("Untrusted update URL");
    }
    return HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(30))
        .header("Accept", "application/vnd.github+json")
        .header("User-Agent", "Tropimon-" + MOD_ID + "-Updater")
        .GET()
        .build();
  }

  private static byte[] readLimited(InputStream input, long maxBytes) throws IOException {
    byte[] buffer = new byte[8192];
    try (var output = new java.io.ByteArrayOutputStream()) {
      long total = 0;
      int read;
      while ((read = input.read(buffer)) >= 0) {
        total += read;
        if (total > maxBytes) throw new IOException("Response too large");
        output.write(buffer, 0, read);
      }
      return output.toByteArray();
    }
  }

  private static ReleaseAsset selectJar(JsonArray assets) throws IOException {
    if (assets == null) throw new IOException("Missing assets");
    ReleaseAsset selected = null;
    for (var element : assets) {
      JsonObject asset = element.getAsJsonObject();
      String name = asset.get("name").getAsString();
      String lower = name.toLowerCase(Locale.ROOT);
      if (lower.endsWith(".jar")
          && !lower.contains("sources")
          && !lower.contains("dev")
          && !lower.contains("local")) {
        if (selected != null) throw new IOException("Ambiguous release JARs");
        selected = checkedAsset(name, asset.get("browser_download_url").getAsString());
      }
    }
    return selected;
  }

  private static ReleaseAsset selectChecksum(JsonArray assets, String jarName) throws IOException {
    if (assets == null || jarName == null) return null;
    for (var element : assets) {
      JsonObject asset = element.getAsJsonObject();
      String name = asset.get("name").getAsString();
      if (name.equalsIgnoreCase(jarName + ".sha256")) {
        return checkedAsset(name, asset.get("browser_download_url").getAsString());
      }
    }
    return null;
  }

  private static ReleaseAsset checkedAsset(String name, String url) throws IOException {
    if (!name.matches("[A-Za-z0-9][A-Za-z0-9._+\\-]*")
        || !url.startsWith(RELEASE_DOWNLOAD_PREFIX)) {
      throw new IOException("Unsafe release asset");
    }
    return new ReleaseAsset(name, url);
  }

  private static JarMetadata inspectJar(Path jar) throws IOException {
    try (ZipFile zip = new ZipFile(jar.toFile())) {
      ZipEntry entry = zip.getEntry("fabric.mod.json");
      if (entry == null || entry.getSize() > 1024 * 1024) throw new IOException("Missing metadata");
      try (InputStream input = zip.getInputStream(entry)) {
        JsonObject json =
            JsonParser.parseString(
                    new String(readLimited(input, 1024 * 1024), StandardCharsets.UTF_8))
                .getAsJsonObject();
        return new JarMetadata(json.get("id").getAsString(), json.get("version").getAsString());
      }
    }
  }

  private static int compareVersions(String left, String right) {
    int[] a = numericParts(left);
    int[] b = numericParts(right);
    for (int index = 0; index < Math.max(a.length, b.length); index++) {
      int av = index < a.length ? a[index] : 0;
      int bv = index < b.length ? b[index] : 0;
      if (av != bv) return Integer.compare(av, bv);
    }
    return 0;
  }

  private static int[] numericParts(String version) {
    String core = version.split("[+-]", 2)[0];
    String[] parts = core.replaceFirst("^[vV]", "").split("\\.");
    int[] values = new int[parts.length];
    for (int index = 0; index < parts.length; index++) {
      try {
        values[index] = Integer.parseInt(parts[index].replaceAll("[^0-9].*$", ""));
      } catch (NumberFormatException ignored) {
        values[index] = 0;
      }
    }
    return values;
  }

  private static String sha256(Path file) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream input = Files.newInputStream(file)) {
      byte[] buffer = new byte[16 * 1024];
      int read;
      while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static void armInstaller(
      Path script, Path staged, Path target, String oldHash, String newHash) throws IOException {
    Path instance = target.getParent().getParent();
    boolean managed =
        Files.exists(instance.resolve("mods-user"))
            || Files.exists(instance.getParent().resolve("user-mods-tracked.json"));
    if (managed) {
      new ProcessBuilder(
              "powershell.exe",
              "-NoProfile",
              "-NonInteractive",
              "-WindowStyle",
              "Hidden",
              "-ExecutionPolicy",
              "Bypass",
              "-File",
              script.toString(),
              "-SourceJar",
              staged.toString(),
              "-ExpectedModId",
              MOD_ID,
              "-LauncherRoot",
              instance.toString(),
              "-LoadedTarget",
              target.toString(),
              "-ExpectedLoadedHash",
              oldHash)
          .redirectErrorStream(true)
          .redirectOutput(script.resolveSibling("install.log").toFile())
          .start();
      return;
    }
    new ProcessBuilder(
            "powershell.exe",
            "-NoProfile",
            "-NonInteractive",
            "-WindowStyle",
            "Hidden",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            script.toString(),
            "-ParentPid",
            Long.toString(ProcessHandle.current().pid()),
            "-Staged",
            staged.toString(),
            "-Target",
            target.toString(),
            "-ExpectedOldHash",
            oldHash,
            "-NewHash",
            newHash,
            "-ModId",
            MOD_ID)
        .redirectErrorStream(true)
        .redirectOutput(script.resolveSibling("install.log").toFile())
        .start();
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }

  private static Throwable rootCause(Throwable failure) {
    Throwable current = failure;
    while (current.getCause() != null) current = current.getCause();
    return current;
  }

  private record ReleaseAsset(String name, String url) {}

  private record JarMetadata(String id, String version) {}

  private static final String WINDOWS_INSTALLER =
      """
param(
 [Parameter(Mandatory=$true)][long]$ParentPid,
 [Parameter(Mandatory=$true)][string]$Staged,
 [Parameter(Mandatory=$true)][string]$Target,
 [Parameter(Mandatory=$true)][string]$ExpectedOldHash,
 [Parameter(Mandatory=$true)][string]$NewHash,
 [Parameter(Mandatory=$true)][string]$ModId
)
$ErrorActionPreference='Stop'
Import-Module (Join-Path $PSHOME 'Modules/Microsoft.PowerShell.Utility/Microsoft.PowerShell.Utility.psd1') -ErrorAction Stop
Import-Module (Join-Path $PSHOME 'Modules/CimCmdlets/CimCmdlets.psd1') -ErrorAction Stop
$locked=$null
$status=Join-Path (Split-Path -Path $Staged -Parent) 'update-status.json'
function Status([string]$state) { @{state=$state;updatedAt=[DateTimeOffset]::UtcNow.ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath $status -Encoding UTF8 }
function Running([string]$instance) {
 foreach($process in (Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'")){
  $line=$process.CommandLine
  if([string]::IsNullOrWhiteSpace($line)){throw 'Java process cannot be verified'}
  if($line -notmatch 'KnotClient|net\\.minecraft\\.client|--gameDir|--launchTarget'){continue}
  $match=[regex]::Match($line,'--gameDir(?:\\s+|=)(?:"([^"]+)"|([^\\s"]+))')
  if(!$match.Success){throw 'Minecraft instance cannot be verified'}
  $dir=if($match.Groups[1].Success){$match.Groups[1].Value}else{$match.Groups[2].Value}
  if([IO.Path]::GetFullPath($dir).TrimEnd('\\','/') -ieq $instance.TrimEnd('\\','/')){return $true}
 }
 return $false
}
try {
 $Target=[IO.Path]::GetFullPath($Target)
 $mods=Split-Path -Path $Target -Parent
 $instance=Split-Path -Path $mods -Parent
 if((Split-Path -Path $mods -Leaf) -cne 'mods'){throw 'Invalid target'}
 foreach($path in @($mods,$instance,$Target,$Staged)){if((Get-Item -LiteralPath $path).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Redirected path'}}
 Status 'waiting'
 while((Get-Process -Id $ParentPid -ErrorAction SilentlyContinue) -or (Running $instance)){Start-Sleep -Seconds 3}
 if((Get-FileHash -LiteralPath $Staged -Algorithm SHA256).Hash -ine $NewHash){throw 'Staged hash changed'}
 $archive=Join-Path $instance ('mod-archive/'+$ModId+'-'+[guid]::NewGuid().ToString('N'))
 New-Item -ItemType Directory -Path $archive | Out-Null
 $incoming=Join-Path $archive 'incoming.jar'
 Copy-Item -LiteralPath $Staged -Destination $incoming
 if((Get-FileHash -LiteralPath $incoming -Algorithm SHA256).Hash -ine $NewHash){throw 'Copy hash mismatch'}
 if(Running $instance){throw 'Minecraft restarted'}
 $locked=[IO.File]::Open($Target,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::Delete)
 if((Get-FileHash -InputStream $locked -Algorithm SHA256).Hash -ine $ExpectedOldHash){throw 'Target changed since preparation'}
 $backup=Join-Path $archive (Split-Path -Path $Target -Leaf)
 Move-Item -LiteralPath $Target -Destination $backup
 try {
  Move-Item -LiteralPath $incoming -Destination $Target
  if((Get-FileHash -LiteralPath $Target -Algorithm SHA256).Hash -ine $NewHash){throw 'Final hash mismatch'}
 } catch {
  if(Test-Path -LiteralPath $Target){Move-Item -LiteralPath $Target -Destination (Join-Path $archive 'failed.jar')}
  if(!(Test-Path -LiteralPath $Target)){Move-Item -LiteralPath $backup -Destination $Target}
  throw
 }
 $locked.Dispose();$locked=$null
 if((Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash -ine $ExpectedOldHash){throw 'Backup hash mismatch'}
 Status 'installed'
} catch { Status 'blocked'; exit 2 } finally {if($locked){$locked.Dispose()}}

""";

  private static final String WINDOWS_MANAGED_INSTALLER =
      """
param(
    [Parameter(Mandatory = $true)][string]$SourceJar,
    [Parameter(Mandatory = $true)][string]$ExpectedModId,
    [string]$LauncherRoot = $(if ($env:TROPIMON_HOME) { $env:TROPIMON_HOME } else { Join-Path $env:APPDATA '.tropimon' }),
    [int]$PollSeconds = 5,
    [switch]$CheckOnly,
    [string]$LoadedTarget,
    [string]$ExpectedLoadedHash
)

# By FastedCorsi. Autonomous managed update transaction.
# mods-user is the launcher's persistent import directory; mods is its runtime copy.
$ErrorActionPreference = 'Stop'
# Build processes may inherit a PowerShell 7 module path while invoking Windows PowerShell.
foreach ($module in @('Microsoft.PowerShell.Management', 'Microsoft.PowerShell.Utility', 'CimCmdlets')) {
    Import-Module (Join-Path $PSHOME ('Modules/' + $module + '/' + $module + '.psd1')) -ErrorAction Stop
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$locks = @()
$moves = @()
$created = @()
$mutex = $null
$ownsMutex = $false
$trackerReplaced = $false
$backupRoot = $null

function Hash([string]$Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash }
function Metadata([string]$Path) {
    $zip = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $entry = $zip.GetEntry('fabric.mod.json')
        if (!$entry -or $entry.Length -gt 1048576) { throw 'Invalid Fabric metadata.' }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
    } finally { $zip.Dispose() }
}
function Safe-Child([string]$Parent, [string]$Leaf) {
    if ([IO.Path]::GetFileName($Leaf) -cne $Leaf -or $Leaf -in @('.', '..')) { throw 'Unsafe file name.' }
    $path = [IO.Path]::GetFullPath((Join-Path $Parent $Leaf))
    if ([IO.Path]::GetDirectoryName($path) -ine $Parent.TrimEnd('\\', '/')) { throw 'Path outside expected directory.' }
    return $path
}
function No-Redirect([string]$Path) {
    if ((Get-Item -LiteralPath $Path).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Redirected installation path.' }
}
function Game-Running([string]$Instance) {
    foreach ($process in (Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'")) {
        $line = $process.CommandLine
        if ([string]::IsNullOrWhiteSpace($line)) { throw 'Cannot identify a Java process safely.' }
        if ($line -notmatch 'KnotClient|net\\.minecraft\\.client|--gameDir|--launchTarget') { continue }
        $match = [regex]::Match($line, '--gameDir(?:\\s+|=)(?:"([^"]+)"|([^\\s"]+))')
        if (!$match.Success) { throw 'Cannot identify a Minecraft instance safely.' }
        $gameDir = if ($match.Groups[1].Value) { $match.Groups[1].Value } else { $match.Groups[2].Value }
        if ([IO.Path]::GetFullPath($gameDir).TrimEnd('\\', '/') -ieq $Instance.TrimEnd('\\', '/')) { return $true }
    }
    return $false
}
function Mod-Files([string]$Directory) {
    foreach ($file in (Get-ChildItem -LiteralPath $Directory -Filter '*.jar' -File)) {
        No-Redirect $file.FullName
        $meta = Metadata $file.FullName
        if ($meta.id -ceq $ExpectedModId) {
            [pscustomobject]@{ Path = $file.FullName; Name = $file.Name; Hash = Hash $file.FullName; Version = [version]$meta.version }
        }
    }
}

try {
    $source = (Resolve-Path -LiteralPath $SourceJar).Path
    No-Redirect $source
    $name = [IO.Path]::GetFileName($source)
    if ($name -notmatch '^[A-Za-z0-9][A-Za-z0-9._+ -]*\\.jar$') { throw 'Invalid JAR filename.' }
    $checksum = ((Get-Content -LiteralPath ($source + '.sha256') -Raw).Trim() -split '\\s+')[0]
    if ($checksum -notmatch '^[0-9a-fA-F]{64}$' -or (Hash $source) -ine $checksum) { throw 'Source checksum mismatch.' }
    $incoming = Metadata $source
    if ($incoming.id -cne $ExpectedModId -or @($incoming.authors) -cnotcontains 'By FastedCorsi') { throw 'Unexpected mod identity or attribution.' }
    $incomingVersion = [version]$incoming.version
    $base = (Resolve-Path -LiteralPath $LauncherRoot).Path
    No-Redirect $base
    if (Test-Path -LiteralPath (Join-Path $base 'profiles') -PathType Container) {
        No-Redirect (Join-Path $base 'profiles')
        $profiles = @(Get-ChildItem -LiteralPath (Join-Path $base 'profiles') -Directory | Where-Object {
            Test-Path -LiteralPath (Join-Path $_.FullName 'instance/mods') -PathType Container
        })
        $running = @($profiles | Where-Object { Game-Running (Join-Path $_.FullName 'instance') })
        if ($running.Count -eq 1) { $profileRoot = $running[0].FullName }
        elseif ($profiles.Count -eq 1) { $profileRoot = $profiles[0].FullName }
        else { throw 'Ambiguous profile; provide its instance directory explicitly.' }
        $instance = Join-Path $profileRoot 'instance'
    } else {
        $instance = $base
        $profileRoot = Split-Path -Parent $instance
        if ((Split-Path -Leaf $instance) -cne 'instance') { throw 'Not a managed launcher instance.' }
    }
    $mods = Safe-Child $instance 'mods'
    $managed = Safe-Child $instance 'mods-user'
    $tracker = Safe-Child $profileRoot 'user-mods-tracked.json'
    foreach ($path in @($profileRoot, $instance, $mods, $managed, $tracker)) {
        if (!(Test-Path -LiteralPath $path)) { throw 'Managed launcher layout not recognized; no files changed.' }
        No-Redirect $path
    }
    if ((Split-Path -Parent $source) -in @($mods, $managed)) { throw 'Use a delivery JAR outside the launcher directories.' }

    $key = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($instance.ToLowerInvariant())).Replace('/', '_')
    $mutex = [Threading.Mutex]::new($false, ('Local\\TropimonLocalInstall-' + $key))
    $ownsMutex = $mutex.WaitOne(0)
    if (!$ownsMutex) { throw 'Another local installation is already in progress for this profile.' }
    $trackerHash = Hash $tracker
    $trackerText = Get-Content -LiteralPath $tracker -Raw
    if (!$trackerText.TrimStart().StartsWith('[')) { throw 'Unrecognized launcher tracking format.' }
    $parsedTracker = ConvertFrom-Json -InputObject $trackerText
    $tracked = @($parsedTracker)
    foreach ($item in $tracked) {
        if ($item -isnot [string] -or [IO.Path]::GetFileName($item) -cne $item -or $item -notmatch '\\.jar$') { throw 'Invalid launcher tracking entry.' }
    }
    $oldRuntime = @(Mod-Files $mods)
    $oldManaged = @(Mod-Files $managed)
    if ($oldRuntime.Count -gt 1 -or $oldManaged.Count -gt 1) { throw 'Multiple copies of the mod; refusing an ambiguous replacement.' }
    if ($LoadedTarget) {
        if ($oldRuntime.Count -ne 1 -or $oldRuntime[0].Path -ine $LoadedTarget -or $oldRuntime[0].Hash -ine $ExpectedLoadedHash) {
            throw 'The loaded mod changed before updater preparation.'
        }
    }
    $oldFiles = @($oldRuntime) + @($oldManaged)
    $oldPaths = @($oldFiles | ForEach-Object { $_.Path })
    if (@($oldFiles | Where-Object { $_.Version -gt $incomingVersion }).Count) { throw 'A newer version is already installed.' }
    foreach ($directory in @($mods, $managed)) {
        $target = Safe-Child $directory $name
        if ((Test-Path -LiteralPath $target) -and $target -notin $oldPaths) { throw 'Destination belongs to another mod.' }
    }
    $oldNames = @($oldFiles.Name)
    $newTracked = @($tracked | Where-Object { $_ -notin $oldNames -and $_ -cne $name }) + @($name)
    $alreadyInstalled = $oldFiles.Count -eq 2 -and @($oldFiles | Where-Object { $_.Name -cne $name -or $_.Hash -ine $checksum }).Count -eq 0 -and @($tracked | Where-Object { $_ -ceq $name }).Count -eq 1 -and @($tracked | Where-Object { $_ -in $oldNames -and $_ -cne $name }).Count -eq 0
    if ($CheckOnly -or $alreadyInstalled) {
        [pscustomobject]@{ state = $(if ($alreadyInstalled) { 'installed' } else { 'ready' }); modId = $ExpectedModId; version = $incoming.version; jar = $name; checkOnly = [bool]$CheckOnly } | ConvertTo-Json -Compress
        exit 0
    }
    while (Game-Running $instance) {
        Write-Output 'Waiting for this Minecraft instance to exit; launcher may remain open.'
        Start-Sleep -Seconds ([Math]::Max(2, $PollSeconds))
    }

    $archive = Safe-Child $instance 'mod-archive'
    if (!(Test-Path -LiteralPath $archive)) { New-Item -ItemType Directory -Path $archive | Out-Null }
    No-Redirect $archive
    $backupRoot = Safe-Child $archive ('managed-install-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $backupRoot | Out-Null
    foreach ($leaf in @('old-mods', 'old-mods-user', 'prepared', 'failed')) {
        New-Item -ItemType Directory -Path (Safe-Child $backupRoot $leaf) | Out-Null
    }
    $prepared = Safe-Child $backupRoot 'prepared'
    $stagedRuntime = Safe-Child $prepared 'runtime.jar'
    $stagedManaged = Safe-Child $prepared 'managed.jar'
    foreach ($stage in @($stagedRuntime, $stagedManaged)) {
        Copy-Item -LiteralPath $source -Destination $stage
        if ((Hash $stage) -ine $checksum) { throw 'Staged JAR checksum mismatch.' }
    }
    $newTracker = Safe-Child $prepared 'user-mods-tracked.json'
    [IO.File]::WriteAllText($newTracker, (ConvertTo-Json -InputObject @($newTracked)), [Text.UTF8Encoding]::new($false))
    $newTrackerHash = Hash $newTracker
    $trackerBackup = Safe-Child $backupRoot 'user-mods-tracked.before.json'

    # Hold read handles that deny writes while allowing our backup renames.
    foreach ($path in $oldPaths + @($tracker)) {
        $locks += [IO.File]::Open($path, [IO.FileMode]::Open, [IO.FileAccess]::Read, ([IO.FileShare]::Read -bor [IO.FileShare]::Delete))
    }
    if ((Hash $tracker) -ine $trackerHash) { throw 'Launcher tracking changed after preparation.' }
    foreach ($old in $oldFiles) { if ((Hash $old.Path) -ine $old.Hash) { throw 'An installed JAR changed after preparation.' } }
    $current = @(Mod-Files $mods) + @(Mod-Files $managed)
    if ($current.Count -ne $oldFiles.Count -or @($current | Where-Object { $_.Path -notin $oldPaths }).Count) { throw 'The installed mod set changed after preparation.' }
    if (Game-Running $instance) { throw 'Minecraft restarted; no replacement performed.' }

    foreach ($old in $oldFiles) {
        $bucket = if ((Split-Path -Parent $old.Path) -ieq $mods) { 'old-mods' } else { 'old-mods-user' }
        $backup = Safe-Child (Safe-Child $backupRoot $bucket) $old.Name
        Move-Item -LiteralPath $old.Path -Destination $backup
        $moves += [pscustomobject]@{ Original = $old.Path; Backup = $backup; Hash = $old.Hash }
    }
    $runtimeTarget = Safe-Child $mods $name
    $managedTarget = Safe-Child $managed $name
    Move-Item -LiteralPath $stagedRuntime -Destination $runtimeTarget
    $created += $runtimeTarget
    Move-Item -LiteralPath $stagedManaged -Destination $managedTarget
    $created += $managedTarget
    if ((Hash $runtimeTarget) -ine $checksum -or (Hash $managedTarget) -ine $checksum) { throw 'Installed JAR checksum mismatch.' }
    if ((Hash $tracker) -ine $trackerHash) { throw 'Launcher tracking changed during installation.' }
    [IO.File]::Replace($newTracker, $tracker, $trackerBackup)
    $trackerReplaced = $true
    foreach ($entry in $moves) { if ((Hash $entry.Backup) -ine $entry.Hash) { throw 'Backup integrity mismatch.' } }
    if ((Hash $trackerBackup) -ine $trackerHash -or (Hash $tracker) -ine $newTrackerHash) { throw 'Launcher tracking verification failed.' }
    foreach ($directory in @($mods, $managed)) {
        $installed = @(Mod-Files $directory)
        if ($installed.Count -ne 1 -or $installed[0].Name -cne $name -or $installed[0].Hash -ine $checksum) { throw 'Final installed mod verification failed.' }
    }
    $result = [pscustomobject]@{ state = 'installed'; modId = $ExpectedModId; version = $incoming.version; jar = $name; managed = $true; backup = (Split-Path -Leaf $backupRoot) }
    $result | ConvertTo-Json | Set-Content -LiteralPath (Safe-Child $backupRoot 'result.json') -Encoding UTF8
    $result | ConvertTo-Json -Compress
} catch {
    foreach ($lock in $locks) { $lock.Dispose() }
    $locks = @()
    $rollbackOk = $true
    try {
        if ($trackerReplaced) {
            if ((Hash $tracker) -ine $newTrackerHash) { throw 'Tracking changed externally; preserved for manual recovery.' }
            $restore = Safe-Child $backupRoot 'restore-tracker.json'
            Copy-Item -LiteralPath $trackerBackup -Destination $restore
            [IO.File]::Replace($restore, $tracker, (Safe-Child $backupRoot 'failed-tracker.json'))
        }
        foreach ($path in $created) {
            if (Test-Path -LiteralPath $path) {
                if ((Hash $path) -ine $checksum) { throw 'Installed target changed externally; preserved for manual recovery.' }
                $bucket = if ((Split-Path -Parent $path) -ieq $mods) { 'runtime.jar' } else { 'managed.jar' }
                Move-Item -LiteralPath $path -Destination (Safe-Child (Safe-Child $backupRoot 'failed') $bucket)
            }
        }
        foreach ($entry in $moves) {
            if (Test-Path -LiteralPath $entry.Original) { throw 'Rollback destination occupied; preserved for manual recovery.' }
            if ((Hash $entry.Backup) -ine $entry.Hash) { throw 'Rollback backup changed; preserved for manual recovery.' }
            Move-Item -LiteralPath $entry.Backup -Destination $entry.Original
        }
    } catch { $rollbackOk = $false }
    [pscustomobject]@{ state = 'blocked'; errorType = $_.Exception.GetType().Name; reason = $(if ($_.Exception -is [Management.Automation.RuntimeException] -and !$_.Exception.InnerException) { $_.Exception.Message } else { 'Installation could not be completed safely.' }); rollbackComplete = $rollbackOk } | ConvertTo-Json -Compress
    exit 2
} finally {
    foreach ($lock in $locks) { $lock.Dispose() }
    if ($ownsMutex) { $mutex.ReleaseMutex() }
    if ($mutex) { $mutex.Dispose() }
}

""";
}
