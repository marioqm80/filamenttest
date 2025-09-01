package com.example.filamenttestjava.filament.app;

import android.content.res.AssetManager;

import com.google.android.filament.Engine;
import com.google.android.filament.Material;

import java.nio.ByteBuffer;

public class MaterialProvider {

    /** Lê um .filamat dos assets (via stream, funciona mesmo comprimido) e cria o Material. */
    public static Material loadFromAssets(Engine engine, AssetManager am, String assetPath) {
        ByteBuffer buf = AssetUtils.readAssetAsDirectBuffer(am, assetPath);
        return new Material.Builder().payload(buf, buf.remaining()).build(engine);
    }
}
