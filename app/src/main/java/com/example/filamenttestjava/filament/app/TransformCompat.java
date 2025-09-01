package com.example.filamenttestjava.filament.app;

import com.google.android.filament.Engine;
import com.google.android.filament.TransformManager;

/** Compat helper: funciona com bindings que usam int/long ou Instance. */
public class TransformCompat {
    public static void set(Engine engine, int entity, float[] m4x4) {
        TransformManager tcm = engine.getTransformManager();
        try {
            // caminho “primitivo” (int/long)
            try {
                int handle = (int) tcm.getClass().getMethod("getInstance", int.class).invoke(tcm, entity);
                tcm.getClass().getMethod("setTransform", int.class, float[].class).invoke(tcm, handle, m4x4);
                return;
            } catch (NoSuchMethodException ignore) {
                long handle = (long) tcm.getClass().getMethod("getInstance", int.class).invoke(tcm, entity);
                tcm.getClass().getMethod("setTransform", long.class, float[].class).invoke(tcm, handle, m4x4);
                return;
            }
        } catch (Throwable t) {
            // fallback: API com classe aninhada Instance
            try {
                Class<?> instCls = Class.forName("com.google.android.filament.TransformManager$Instance");
                Object inst = tcm.getClass().getMethod("getInstance", int.class).invoke(tcm, entity);
                tcm.getClass().getMethod("setTransform", instCls, float[].class).invoke(tcm, inst, m4x4);
            } catch (Throwable t2) {
                throw new RuntimeException("TransformManager incompatível com este helper", t2);
            }
        }
    }
}
