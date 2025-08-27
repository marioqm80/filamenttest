package com.example.filamenttestjava;

import android.app.Activity;
import android.os.Bundle;
import android.view.Choreographer;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.google.android.filament.Box;
import com.google.android.filament.Camera;
import com.google.android.filament.Engine;
import com.google.android.filament.EntityManager;
import com.google.android.filament.IndexBuffer;
import com.google.android.filament.LightManager;
import com.google.android.filament.Material;
import com.google.android.filament.MaterialInstance;
import com.google.android.filament.RenderableManager;
import com.google.android.filament.Renderer;
import com.google.android.filament.Scene;
import com.google.android.filament.Skybox;
import com.google.android.filament.SwapChain;
import com.google.android.filament.TransformManager;
import com.google.android.filament.VertexBuffer;
import com.google.android.filament.View;
import com.google.android.filament.Viewport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Random;

public class MainActivity extends Activity implements SurfaceHolder.Callback, Choreographer.FrameCallback {

    // Libs JNI
    static {
        try { System.loadLibrary("filament-jni"); } catch (Throwable ignored) {}
        try { System.loadLibrary("filamat-jni"); } catch (Throwable ignored) {}
        try { System.loadLibrary("filament-utils-jni"); } catch (Throwable ignored) {}
    }

    private SurfaceView surfaceView;

    private Engine engine;
    private Renderer renderer;
    private SwapChain swapChain;
    private Scene scene;
    private View view;
    private Camera camera;

    private int cubeEntity;
    private Material cubeMaterial;
    private MaterialInstance cubeMI;
    private VertexBuffer vbo;
    private IndexBuffer ibo;

    // Múltiplas luzes
    private int keyLight, fillLightA, fillLightB, rimLight, bouncePoint;

    private float axisX, axisY, axisZ;
    private float angularSpeedDegPerSec;
    private float angleDeg = 0f;
    private long lastFrameNanos = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        surfaceView = findViewById(R.id.surfaceView);
        surfaceView.getHolder().addCallback(this);

        // Engine (troque para OPENGL se Vulkan não for suportado no device)
        engine = Engine.create(Engine.Backend.VULKAN);
        renderer = engine.createRenderer();
        scene = engine.createScene();
        view = engine.createView();
        view.setScene(scene);
        scene.setSkybox(new Skybox.Builder().color(0, 0, 0, 1).build(engine));

        // Câmera
        int camEntity = EntityManager.get().create();
        camera = engine.createCamera(camEntity);
        view.setCamera(camera);
        camera.setProjection(
                45.0,          // FOV (graus)
                16.0/9.0,      // aspect inicial
                0.1,
                100.0,
                Camera.Fov.VERTICAL
        );
        camera.lookAt(0.0, 2.0, 6.0,   0.0, 0.0, 0.0,   0.0, 1.0, 0.0);

        // Exposição sugerida (ajuste fino conforme as intensidades abaixo)
        camera.setExposure(16.0f, 1.0f/125.0f, 100.0f); // f/16, 1/125s, ISO100

        // Luzes
        createLights();

        // Eixo/velocidade aleatórios (apenas o cubo gira)
        Random rnd = new Random();
        axisX = rnd.nextFloat() * 2f - 1f;
        axisY = rnd.nextFloat() * 2f - 1f;
        axisZ = rnd.nextFloat() * 2f - 1f;
        float len = (float) Math.sqrt(axisX*axisX + axisY*axisY + axisZ*axisZ);
        if (len < 1e-6f) { axisX = 0; axisY = 1; axisZ = 0; } else { axisX/=len; axisY/=len; axisZ/=len; }
        angularSpeedDegPerSec = 30f + rnd.nextFloat() * 60f;

        // Cubo
        createCube();

        // Loop de render
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onDestroy() {
        Choreographer.getInstance().removeFrameCallback(this);

        if (scene != null && cubeEntity != 0) scene.remove(cubeEntity);

        if (engine != null) {
            // Destrói luzes
            if (bouncePoint != 0) { engine.destroyEntity(bouncePoint); bouncePoint = 0; }
            if (rimLight != 0)    { engine.destroyEntity(rimLight);    rimLight = 0; }
            if (fillLightB != 0)  { engine.destroyEntity(fillLightB);  fillLightB = 0; }
            if (fillLightA != 0)  { engine.destroyEntity(fillLightA);  fillLightA = 0; }
            if (keyLight != 0)    { engine.destroyEntity(keyLight);    keyLight = 0; }

            if (vbo != null) { engine.destroyVertexBuffer(vbo); vbo = null; }
            if (ibo != null) { engine.destroyIndexBuffer(ibo);  ibo = null; }
            if (cubeMI != null) { engine.destroyMaterialInstance(cubeMI); cubeMI = null; }
            if (cubeMaterial != null) { engine.destroyMaterial(cubeMaterial); cubeMaterial = null; }
            if (cubeEntity != 0) { engine.destroyEntity(cubeEntity);   cubeEntity = 0; }

            if (camera != null) { engine.destroyCameraComponent(camera.getEntity()); camera = null; }
            if (view != null)   { engine.destroyView(view); view = null; }
            if (renderer != null) { engine.destroyRenderer(renderer); renderer = null; }
            if (swapChain != null) { engine.destroySwapChain(swapChain); swapChain = null; }
            if (scene != null)  { engine.destroyScene(scene); scene = null; }

            engine.destroy();
            engine = null;
        }
        super.onDestroy();
    }

    // ---------- Surface ----------
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Surface surface = holder.getSurface();
        swapChain = engine.createSwapChain(surface);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        view.setViewport(new Viewport(0, 0, w, h));
        double aspect = (double) w / Math.max(1, h);
        camera.setProjection(45.0, aspect, 0.1, 100.0, Camera.Fov.VERTICAL);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (swapChain != null) {
            engine.destroySwapChain(swapChain);
            swapChain = null;
        }
    }

    // ---------- Render loop ----------
    @Override
    public void doFrame(long frameTimeNanos) {
        if (lastFrameNanos == 0) lastFrameNanos = frameTimeNanos;
        float dt = (frameTimeNanos - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = frameTimeNanos;

        angleDeg += angularSpeedDegPerSec * dt;

        float[] m = quatToMatrix(axisX, axisY, axisZ, angleDeg);
        TransformManager tm = engine.getTransformManager();
        tm.setTransform(tm.getInstance(cubeEntity), m);

        if (swapChain != null && renderer.beginFrame(swapChain, frameTimeNanos)) {
            renderer.render(view);
            renderer.endFrame();
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    // ---------- Luzes ----------
    private void createLights() {
        // Key light (com sombras)
        keyLight = EntityManager.get().create();
        new LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(1f, 1f, 1f)
                .intensity(40_000f)
                .direction(-0.5f, -1.0f, -0.3f)
                .castShadows(true)
                .build(engine, keyLight);
        scene.addEntity(keyLight);

        // Fill A — lateral, sem sombras
        fillLightA = EntityManager.get().create();
        new LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(1f, 1f, 1f)
                .intensity(6000f)
                .direction(0.8f, -0.3f, 0.1f)
                .castShadows(false)
                .build(engine, fillLightA);
        scene.addEntity(fillLightA);

        // Fill B — outra lateral, sem sombras
        fillLightB = EntityManager.get().create();
        new LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(1f, 1f, 1f)
                .intensity(3500f)
                .direction(-0.2f, -0.2f, 0.9f)
                .castShadows(false)
                .build(engine, fillLightB);
        scene.addEntity(fillLightB);

        // Rim (contra-luz) para recorte do contorno
        rimLight = EntityManager.get().create();
        new LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(1f, 1f, 1f)
                .intensity(8000f)
                .direction(0.2f, -0.3f, -1.0f)
                .castShadows(false)
                .build(engine, rimLight);
        scene.addEntity(rimLight);

        // “Ambiente fake”: point light fraca acima do cubo (sem sombras)
        bouncePoint = EntityManager.get().create();
        new LightManager.Builder(LightManager.Type.POINT)
                .color(1f, 1f, 1f)
                .intensity(1500f)   // discreta
                .falloff(10.0f)     // alcance em metros
                .position(0f, 2.0f, 0f)
                .castShadows(false)
                .build(engine, bouncePoint);
        scene.addEntity(bouncePoint);
    }

    // ---------- Cena / cubo ----------
    private void createCube() {
        // Material (.filamat)
        cubeMaterial = loadMaterialFromAssets("materials/LitPBR.filamat");
        cubeMI = cubeMaterial.createInstance();

        // baseColor linear (exemplo simples)
        float[] srgb = new float[]{0.65f, 0.72f, 0.90f, 1.0f};
        float[] linear = new float[]{
                srgbToLinear(srgb[0]),
                srgbToLinear(srgb[1]),
                srgbToLinear(srgb[2]),
                srgb[3]
        };
        cubeMI.setParameter("baseColor", linear[0], linear[1], linear[2], linear[3]);
        cubeMI.setParameter("metallic", 0.05f);
        cubeMI.setParameter("roughness", 0.4f);

        // Geometria (24 vértices, normais por face — flat shading)
        float[] positions = new float[]{
                // +X
                0.5f,-0.5f,-0.5f,  0.5f,-0.5f, 0.5f,   0.5f, 0.5f, 0.5f,   0.5f, 0.5f,-0.5f,
                // -X
                -0.5f,-0.5f, 0.5f, -0.5f,-0.5f,-0.5f, -0.5f, 0.5f,-0.5f, -0.5f, 0.5f, 0.5f,
                // +Y
                -0.5f, 0.5f,-0.5f,  0.5f, 0.5f,-0.5f,  0.5f, 0.5f, 0.5f,  -0.5f, 0.5f, 0.5f,
                // -Y
                -0.5f,-0.5f, 0.5f,  0.5f,-0.5f, 0.5f,  0.5f,-0.5f,-0.5f, -0.5f,-0.5f,-0.5f,
                // +Z
                -0.5f,-0.5f, 0.5f,  0.5f,-0.5f, 0.5f,  0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f,
                // -Z
                0.5f,-0.5f,-0.5f, -0.5f,-0.5f,-0.5f, -0.5f, 0.5f,-0.5f,  0.5f, 0.5f,-0.5f
        };
        float[] normals = new float[]{
                // +X
                1,0,0, 1,0,0, 1,0,0, 1,0,0,
                // -X
                -1,0,0,-1,0,0,-1,0,0,-1,0,0,
                // +Y
                0,1,0, 0,1,0, 0,1,0, 0,1,0,
                // -Y
                0,-1,0,0,-1,0,0,-1,0,0,-1,0,
                // +Z
                0,0,1,0,0,1,0,0,1,0,0,1,
                // -Z
                0,0,-1,0,0,-1,0,0,-1,0,0,-1
        };
        short[] indices = new short[]{
                0,1,2, 0,2,3,      // +X
                4,5,6, 4,6,7,      // -X
                8,9,10, 8,10,11,   // +Y
                12,13,14, 12,14,15,// -Y
                16,17,18, 16,18,19,// +Z
                20,21,22, 20,22,23 // -Z
        };

        final int vertexCount = 24;

        // POSITION
        FloatBuffer posFb = ByteBuffer
                .allocateDirect(positions.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        posFb.put(positions).flip();

        // “NORMAL” usando o atributo TANGENTS (FLOAT3) — compat com versões antigas
        FloatBuffer nrmFb = ByteBuffer
                .allocateDirect(normals.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        nrmFb.put(normals).flip();

        vbo = new VertexBuffer.Builder()
                .bufferCount(2)
                .vertexCount(vertexCount)
                .attribute(VertexBuffer.VertexAttribute.POSITION, 0,
                        VertexBuffer.AttributeType.FLOAT3, 0, 12)
                .attribute(VertexBuffer.VertexAttribute.TANGENTS, 1,
                        VertexBuffer.AttributeType.FLOAT3, 0, 12) // usa TANGENTS para normais
                .build(engine);

        vbo.setBufferAt(engine, 0, posFb);
        vbo.setBufferAt(engine, 1, nrmFb);

        // Índices
        ShortBuffer idxSb = ByteBuffer
                .allocateDirect(indices.length * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer();
        idxSb.put(indices).flip();

        ibo = new IndexBuffer.Builder()
                .indexCount(indices.length)
                .bufferType(IndexBuffer.Builder.IndexType.USHORT)
                .build(engine);
        ibo.setBuffer(engine, idxSb);

        // Renderable
        cubeEntity = EntityManager.get().create();
        Box box = new Box(
                new float[]{ -0.75f, -0.75f, -0.75f }, // min
                new float[]{  0.75f,  0.75f,  0.75f }  // max
        );

        new RenderableManager.Builder(1)
                .boundingBox(box)
                .material(0, cubeMI)
                .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vbo, ibo)
                .castShadows(true)
                .receiveShadows(true)
                .build(engine, cubeEntity);

        scene.addEntity(cubeEntity);
    }

    private Material loadMaterialFromAssets(String assetPath) {
        try (InputStream is = getAssets().open(assetPath)) {
            byte[] bytes = new byte[is.available()];
            int read = is.read(bytes);
            if (read != bytes.length) throw new IOException("Falha ao ler " + assetPath);
            ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
            buffer.put(bytes).flip();
            return new Material.Builder()
                    .payload(buffer, buffer.remaining())
                    .build(engine);
        } catch (IOException e) {
            throw new RuntimeException("Material não encontrado: " + assetPath, e);
        }
    }

    // ---------- Helpers ----------
    private static float srgbToLinear(float c) {
        if (c <= 0.04045f) return c / 12.92f;
        return (float) Math.pow((c + 0.055f) / 1.055f, 2.4f);
    }

    // eixo + ângulo (graus) -> matriz 4x4 column-major
    private static float[] quatToMatrix(float ax, float ay, float az, float angleDeg) {
        float angleRad = (float) Math.toRadians(angleDeg);
        float s = (float) Math.sin(angleRad * 0.5f);
        float qw = (float) Math.cos(angleRad * 0.5f);
        float qx = ax * s, qy = ay * s, qz = az * s;

        float xx = qx*qx, yy = qy*qy, zz = qz*qz;
        float xy = qx*qy, xz = qx*qz, yz = qy*qz;
        float wx = qw*qx, wy = qw*qy, wz = qw*qz;

        float[] m = new float[16];
        m[0] = 1 - 2*(yy + zz);
        m[1] = 2*(xy + wz);
        m[2] = 2*(xz - wy);
        m[3] = 0;

        m[4] = 2*(xy - wz);
        m[5] = 1 - 2*(xx + zz);
        m[6] = 2*(yz + wx);
        m[7] = 0;

        m[8]  = 2*(xz + wy);
        m[9]  = 2*(yz - wx);
        m[10] = 1 - 2*(xx + yy);
        m[11] = 0;

        m[12] = 0; m[13] = 0; m[14] = 0; m[15] = 1;
        return m;
    }
}
