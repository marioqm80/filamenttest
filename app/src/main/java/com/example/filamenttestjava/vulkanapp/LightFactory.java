package com.example.filamenttestjava.vulkanapp;

import com.google.android.filament.Colors;
import com.google.android.filament.Engine;
import com.google.android.filament.Entity;
import com.google.android.filament.EntityManager;
import com.google.android.filament.LightManager;
import com.google.android.filament.Scene;

public class LightFactory {

    /** Cria um “sol”: luz direcional ~5500K, 110k lux, sombras ON. */
    public static int createSun(Engine engine, Scene scene) {
        float[] eye    = { 0f, 0f, 4f };   // posição (primeiro triplo do lookAt)
        float[] center = { 0f, 0f, 0f };   // alvo     (segundo triplo do lookAt)

// forward = center - eye (normalizado)
        float dx = center[0] - eye[0];
        float dy = center[1] - eye[1];
        float dz = center[2] - eye[2];
        float len = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (len > 1e-6f) { dx/=len; dy/=len; dz/=len; }




        @Entity int light = EntityManager.get().create();
        float[] rgb = Colors.cct(5500.0f);

        LightManager.Builder b = new LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(rgb[0], rgb[1], rgb[2])
                .intensity(110_000.0f)
                .direction(0.0f, -0.5f, -0.5f)
                //.direction(0.0f, -1.0f, -0.25f)
                //.direction( dx, dy, dz)

                .castShadows(true);

        // métodos opcionais (disco do sol) podem não existir em todas as versões
        try {
            b.getClass().getMethod("sunAngularRadius", float.class).invoke(b, 0.00935f);
            b.getClass().getMethod("sunHaloSize", float.class).invoke(b, 10.0f);
            b.getClass().getMethod("sunHaloFalloff", float.class).invoke(b, 80.0f);
        } catch (Throwable ignored) {}

        b.build(engine, light);
        scene.addEntity(light);
        return light;
    }
}
