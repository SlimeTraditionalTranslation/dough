package io.github.bakedlibs.dough.updater;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import io.github.bakedlibs.dough.versions.Version;

abstract class UpdaterTask<V extends Version> implements Runnable {

    private final Plugin plugin;
    private final File file;
    private final URL url;
    private final int timeout;
    private final V currentVersion;

    UpdaterTask(@Nonnull PluginUpdater<V> updater, @Nonnull URL url) {
        this.plugin = updater.getPlugin();
        this.file = updater.getFile();
        this.url = url;
        this.timeout = updater.getConnectionTimeout();
        this.currentVersion = updater.getCurrentVersion();
    }

    @Nullable
    public abstract UpdateInfo parse(String result) throws MalformedURLException;

    @Override
    public void run() {
        try {
            UpdateInfo latestVersion = getLatestVersion(url);

            if (latestVersion != null) {
                validateAndInstall(latestVersion);
            }
        } catch (NumberFormatException x) {
            plugin.getLogger().log(Level.SEVERE, "Could not auto-update {0}", plugin.getName());
            plugin.getLogger().log(Level.SEVERE, "Unrecognized Version: {0}", currentVersion);
        }
    }

    @Nullable
    private UpdateInfo getLatestVersion(@Nonnull URL url) {
        try {
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(timeout);
            connection.addRequestProperty("User-Agent", "Auto Updater (by Efina)");
            connection.setDoOutput(true);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                return parse(reader.readLine());
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not connect to the updating site, is it down?", e);
            return null;
        }
    }

    private void validateAndInstall(@Nonnull UpdateInfo updateInfo) {
        if (updateInfo.getVersion().isNewerThan(currentVersion)) {
            install(updateInfo);
        } else {
            plugin.getLogger().log(Level.INFO, "{0} 已經是最新的!", plugin.getName());
        }
    }

    private void install(@Nonnull UpdateInfo info) {
        plugin.getLogger().log(Level.INFO, "{0} 已過時!", plugin.getName());
        plugin.getLogger().log(Level.INFO, "下載 {0}, 版本: {1}", new Object[] { plugin.getName(), info.getVersion() });

        try (BufferedInputStream input = new BufferedInputStream(info.getUrl().openStream()); FileOutputStream output = new FileOutputStream(new File("plugins/" + Bukkit.getUpdateFolder(), file.getName()))) {
            byte[] data = new byte[1024];
            int read;

            while ((read = input.read(data, 0, 1024)) != -1) {
                output.write(data, 0, read);
            }
        } catch (Exception x) {
            plugin.getLogger().log(Level.SEVERE, x, () -> "Failed to auto-update " + plugin.getName());
        } finally {
            plugin.getLogger().log(Level.INFO, " ");
            plugin.getLogger().log(Level.INFO, "#################### - 更新 - ####################");
            plugin.getLogger().log(Level.INFO, "{0} 已成功更新 ({1} -> {2})", new Object[] { plugin.getName(), currentVersion, info.getVersion() });
            plugin.getLogger().log(Level.INFO, "請重啟你的伺服器來使用最新版本");
            plugin.getLogger().log(Level.INFO, " ");
        }
    }
}