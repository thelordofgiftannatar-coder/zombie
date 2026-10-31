// Put in: android/app/src/main/java/<your/package/path>/LocalMusicPlugin.java
// Change the package line below to match your MainActivity's package.
package com.valiryan.app;

import android.Manifest;
import android.content.ContentResolver;
import android.database.Cursor;
import android.os.Build;
import android.provider.MediaStore;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.File;

@CapacitorPlugin(
    name = "LocalMusic",
    permissions = {
        @Permission(alias = "audio", strings = { "android.permission.READ_MEDIA_AUDIO" }),
        @Permission(alias = "storage", strings = { Manifest.permission.READ_EXTERNAL_STORAGE })
    }
)
public class LocalMusicPlugin extends Plugin {

    private String alias() { return Build.VERSION.SDK_INT >= 33 ? "audio" : "storage"; }

    @PluginMethod
    public void list(PluginCall call) {
        if (getPermissionState(alias()) != PermissionState.GRANTED) {
            requestPermissionForAlias(alias(), call, "afterPermission");
            return;
        }
        scan(call);
    }

    @PermissionCallback
    private void afterPermission(PluginCall call) {
        if (getPermissionState(alias()) == PermissionState.GRANTED) scan(call);
        else call.reject("Permission denied");
    }

    private void scan(PluginCall call) {
        final int minMs = call.getInt("minSeconds", 30) * 1000;
        new Thread(() -> {
            try {
                JSArray songs = new JSArray();
                ContentResolver cr = getContext().getContentResolver();
                String[] cols = {
                    MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DURATION
                };
                try (Cursor c = cr.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols,
                        MediaStore.Audio.Media.DURATION + ">=?", new String[]{ String.valueOf(minMs) },
                        MediaStore.Audio.Media.DATE_ADDED + " DESC")) {
                    if (c != null) while (c.moveToNext()) {
                        String path = c.getString(0);
                        if (path == null) continue;
                        JSObject o = new JSObject();
                        o.put("path", path);
                        o.put("title", c.getString(1));
                        String artist = c.getString(2);
                        if (artist != null && !artist.equals("<unknown>")) o.put("artist", artist);
                        String album = c.getString(3);
                        if (album != null && !album.equals("<unknown>")) o.put("album", album);
                        o.put("name", c.getString(4));
                        o.put("duration", c.getLong(5) / 1000);
                        File parent = new File(path).getParentFile();
                        o.put("folder", parent == null ? "" : parent.getName());
                        songs.put(o);
                    }
                }
                JSObject res = new JSObject();
                res.put("songs", songs);
                call.resolve(res);
            } catch (Exception e) {
                call.reject("Scan failed: " + e.getMessage());
            }
        }).start();
    }
}
