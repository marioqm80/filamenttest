package com.example.filamenttestjava.filament.app;

import android.content.res.AssetManager;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AssetUtils {
    /** Lê asset como stream e retorna ByteBuffer direto (independente de compressão). */
    public static ByteBuffer readAssetAsDirectBuffer(AssetManager am, String assetPath) {
        try (InputStream in = am.open(assetPath)) {
            byte[] tmp = new byte[16 * 1024];
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(tmp.length);
            int n;
            while ((n = in.read(tmp)) != -1) out.write(tmp, 0, n);
            byte[] bytes = out.toByteArray();
            ByteBuffer direct = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
            direct.put(bytes).flip();
            return direct;
        } catch (Exception e) {
            throw new RuntimeException("Falha ao ler asset: " + assetPath, e);
        }
    }
}
