package com.example.filamenttestjava;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.opengl.Matrix;
import android.os.Bundle;
import android.view.Choreographer;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.animation.LinearInterpolator;

import com.google.android.filament.Box;
import com.google.android.filament.Camera;
import com.google.android.filament.Colors;
import com.google.android.filament.Engine;
import com.google.android.filament.Entity;
import com.google.android.filament.EntityManager;
import com.google.android.filament.IndexBuffer;
import com.google.android.filament.LightManager;
import com.google.android.filament.Material;
import com.google.android.filament.MaterialInstance;
import com.google.android.filament.MathUtils; // se sua versão estiver em outro pacote, ajuste o import
import com.google.android.filament.RenderableManager;
import com.google.android.filament.Renderer;
import com.google.android.filament.Scene;
import com.google.android.filament.Skybox;
import com.google.android.filament.SwapChain;
import com.google.android.filament.TransformManager;
import com.google.android.filament.VertexBuffer;
import com.google.android.filament.View;
import com.google.android.filament.Viewport;
import com.google.android.filament.RenderableManager.PrimitiveType;
import com.google.android.filament.VertexBuffer.AttributeType;
import com.google.android.filament.VertexBuffer.VertexAttribute;
import com.google.android.filament.android.DisplayHelper;
import com.google.android.filament.android.FilamentHelper;
import com.google.android.filament.android.UiHelper;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;

public class MainActivity4 extends Activity {

    static {
        try {
            System.loadLibrary("filament-jni");
            // Se você usar gltfio ou utils em JNI:
            // System.loadLibrary("gltfio-jni");
            // System.loadLibrary("filament-utils-jni");
        } catch (UnsatisfiedLinkError e) {
            throw new RuntimeException("Falha ao carregar filament-jni", e);
        }
    }

    private SurfaceView surfaceView;
    private UiHelper uiHelper;
    private DisplayHelper displayHelper;
    private Choreographer choreographer;

    private Engine engine;
    private Renderer renderer;
    private Scene scene;
    private View view;
    private Camera camera;

    private Material material;
    private MaterialInstance materialInstance;
    private VertexBuffer vertexBuffer;
    private IndexBuffer indexBuffer;

    @Entity private int renderable = 0;
    @Entity private int light = 0;

    private SwapChain swapChain;

    private final FrameCallback frameScheduler = new FrameCallback();
    private final ValueAnimator animator = ValueAnimator.ofFloat(0.0f, 360.0f);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        surfaceView = new SurfaceView(this);
        setContentView(surfaceView);

        choreographer = Choreographer.getInstance();
        displayHelper = new DisplayHelper(this);

        setupSurfaceView();
        setupFilament();
        setupView();
        setupScene();
    }

    private void setupSurfaceView() {
        uiHelper = new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK);
        uiHelper.setRenderCallback(new SurfaceCallback());
        // uiHelper.setDesiredSize(1280, 720); // opcional
        uiHelper.attachTo(surfaceView);
    }

    private void setupFilament() {
        engine = Engine.create(Engine.Backend.VULKAN);
        if (engine == null) {
            throw new RuntimeException("Vulkan não suportado neste dispositivo");
        }
        renderer = engine.createRenderer();
        scene = engine.createScene();
        view = engine.createView();
        int camEntity = EntityManager.get().create();
        camera = engine.createCamera(camEntity);
    }

    private void setupView() {
        scene.setSkybox(new Skybox.Builder().color(0.035f, 0.035f, 0.035f, 1.0f).build(engine));
        view.setCamera(camera);
        view.setScene(scene);
        // Se quiser ver sem pós-processamento:
        // view.setPostProcessingEnabled(false);
    }

    private void setupScene() {
        loadMaterial();
        setupMaterial();
        createMesh();

        renderable = EntityManager.get().create();

        new RenderableManager.Builder(1)
                .boundingBox(new Box(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f))
                .geometry(0, PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer, 0, 6 * 6)
                .material(0, materialInstance)
                .build(engine, renderable);

        scene.addEntity(renderable);

        // SOL: luz direcional com intensidade física em lux
        light = EntityManager.get().create();
        float[] sunRGB = Colors.cct(5500.0f); // cor de sol ao meio-dia (~D55)

// Builder básico (funciona em todas as versões)
        LightManager.Builder b = new LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(sunRGB[0], sunRGB[1], sunRGB[2])
                .intensity(110_000.0f)          // céu claro ~110k lux
                .direction(0.0f, -1.0f, -0.25f) // vindo de cima, levemente à frente
                .castShadows(true);

// (Opcional) Disco/halo do sol — só se sua API tiver esses métodos
        try {
            b.getClass().getMethod("sunAngularRadius", float.class).invoke(b, 0.00935f); // ~0.53°
            b.getClass().getMethod("sunHaloSize", float.class).invoke(b, 10.0f);
            b.getClass().getMethod("sunHaloFalloff", float.class).invoke(b, 80.0f);
        } catch (Throwable ignored) {
            // sua versão pode não expor esses métodos — tudo bem ignorar
        }

        b.build(engine, light);
        scene.addEntity(light);

// Combine com exposição "sunny f/16", para bater com 110k lux:
        camera.setExposure(16.0f, 1.0f / 125.0f, 100.0f);


        // exposição “sunny f/16”
        camera.setExposure(16.0f, 1.0f / 125.0f, 100.0f);
        // posiciona câmera
        camera.lookAt(0.0, 3.0, 4.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0);

        startAnimation();
    }

    private void loadMaterial() {
        // Se não estiver usando noCompress 'filamat', troque por readAssetAsByteBuffer(...)
        ByteBuffer buf = readUncompressedAsset("materials/lit.filamat");
        material = new Material.Builder()
                .payload(buf, buf.remaining())
                .build(engine);
    }

    private void setupMaterial() {
        materialInstance = material.createInstance();
        materialInstance.setParameter("baseColor", Colors.RgbType.SRGB, 1.0f, 0.85f, 0.57f);
        materialInstance.setParameter("metallic", 0.0f);
        materialInstance.setParameter("roughness", 0.3f);
    }

    private void createMesh() {
        final int floatSize = 4;
        final int shortSize = 2;

        // XYZ (3 floats) + TANGENTS (quat, 4 floats)
        final int vertexSize = 3 * floatSize + 4 * floatSize;

        // 6 faces * 4 vértices/face
        final int vertexCount = 6 * 4;

        // tangent frames (um por face)
        float[] tfPX = new float[4];
        float[] tfNX = new float[4];
        float[] tfPY = new float[4];
        float[] tfNY = new float[4];
        float[] tfPZ = new float[4];
        float[] tfNZ = new float[4];

        // nx,ny,nz,  tx,ty,tz,  bx,by,bz,  outQuat[4]
        MathUtils.packTangentFrame( 0.0f,  1.0f, 0.0f, 0.0f, 0.0f, -1.0f,  1.0f,  0.0f,  0.0f, tfPX);
        MathUtils.packTangentFrame( 0.0f,  1.0f, 0.0f, 0.0f, 0.0f, -1.0f, -1.0f,  0.0f,  0.0f, tfNX);
        MathUtils.packTangentFrame(-1.0f,  0.0f, 0.0f, 0.0f, 0.0f, -1.0f,  0.0f,  1.0f,  0.0f, tfPY);
        MathUtils.packTangentFrame(-1.0f,  0.0f, 0.0f, 0.0f, 0.0f,  1.0f,  0.0f, -1.0f,  0.0f, tfNY);
        MathUtils.packTangentFrame( 0.0f,  1.0f, 0.0f, 1.0f, 0.0f,  0.0f,  0.0f,  0.0f,  1.0f, tfPZ);
        MathUtils.packTangentFrame( 0.0f, -1.0f, 0.0f, 1.0f, 0.0f,  0.0f,  0.0f,  0.0f, -1.0f, tfNZ);

        ByteBuffer vertexData = ByteBuffer.allocateDirect(vertexCount * vertexSize)
                .order(ByteOrder.nativeOrder());

        // Face -Z
        putVertex(vertexData, -1, -1, -1, tfNZ);
        putVertex(vertexData, -1,  1, -1, tfNZ);
        putVertex(vertexData,  1,  1, -1, tfNZ);
        putVertex(vertexData,  1, -1, -1, tfNZ);
        // Face +X
        putVertex(vertexData,  1, -1, -1, tfPX);
        putVertex(vertexData,  1,  1, -1, tfPX);
        putVertex(vertexData,  1,  1,  1, tfPX);
        putVertex(vertexData,  1, -1,  1, tfPX);
        // Face +Z
        putVertex(vertexData, -1, -1,  1, tfPZ);
        putVertex(vertexData,  1, -1,  1, tfPZ);
        putVertex(vertexData,  1,  1,  1, tfPZ);
        putVertex(vertexData, -1,  1,  1, tfPZ);
        // Face -X
        putVertex(vertexData, -1, -1,  1, tfNX);
        putVertex(vertexData, -1,  1,  1, tfNX);
        putVertex(vertexData, -1,  1, -1, tfNX);
        putVertex(vertexData, -1, -1, -1, tfNX);
        // Face -Y
        putVertex(vertexData, -1, -1,  1, tfNY);
        putVertex(vertexData, -1, -1, -1, tfNY);
        putVertex(vertexData,  1, -1, -1, tfNY);
        putVertex(vertexData,  1, -1,  1, tfNY);
        // Face +Y
        putVertex(vertexData, -1,  1, -1, tfPY);
        putVertex(vertexData, -1,  1,  1, tfPY);
        putVertex(vertexData,  1,  1,  1, tfPY);
        putVertex(vertexData,  1,  1, -1, tfPY);

        vertexData.flip();

        vertexBuffer = new VertexBuffer.Builder()
                .bufferCount(1)
                .vertexCount(vertexCount)
                .attribute(VertexAttribute.POSITION, 0, AttributeType.FLOAT3, 0,             vertexSize)
                .attribute(VertexAttribute.TANGENTS, 0, AttributeType.FLOAT4, 3 * floatSize, vertexSize)
                .build(engine);
        vertexBuffer.setBufferAt(engine, 0, vertexData);

        // índices: 6 faces * 2 triângulos/face * 3 índices = 36
        final int indexCount = 6 * 2 * 3;
        ByteBuffer indexData = ByteBuffer.allocateDirect(indexCount * shortSize)
                .order(ByteOrder.nativeOrder());
        for (int f = 0; f < 6; f++) {
            short i = (short) (f * 4);
            indexData.putShort(i).putShort((short)(i + 1)).putShort((short)(i + 2));
            indexData.putShort(i).putShort((short)(i + 2)).putShort((short)(i + 3));
        }
        indexData.flip();

        indexBuffer = new IndexBuffer.Builder()
                .indexCount(indexCount)
                .bufferType(IndexBuffer.Builder.IndexType.USHORT)
                .build(engine);
        indexBuffer.setBuffer(engine, indexData);
    }

    private static void putVertex(ByteBuffer dst, float x, float y, float z, float[] quat4) {
        dst.putFloat(x);
        dst.putFloat(y);
        dst.putFloat(z);
        dst.putFloat(quat4[0]);
        dst.putFloat(quat4[1]);
        dst.putFloat(quat4[2]);
        dst.putFloat(quat4[3]);
    }

    private void startAnimation() {
        animator.setInterpolator(new LinearInterpolator());
        animator.setDuration(6000);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(animation -> {
            float[] m = new float[16];
            Matrix.setRotateM(m, 0, (Float) animation.getAnimatedValue(), 0f, 1f, 0f);
            TransformManager tcm = engine.getTransformManager();
            //TransformManager tcm = engine.getTransformManager();

// Em bindings mais antigos/algumas versões Maven:
            int ti = tcm.getInstance(renderable);   // às vezes é long
            tcm.setTransform(ti, m);
        });
        animator.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        choreographer.postFrameCallback(frameScheduler);
        animator.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        choreographer.removeFrameCallback(frameScheduler);
        animator.cancel();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        choreographer.removeFrameCallback(frameScheduler);
        animator.cancel();

        uiHelper.detach();

        engine.destroyEntity(light);
        engine.destroyEntity(renderable);
        engine.destroyRenderer(renderer);
        engine.destroyVertexBuffer(vertexBuffer);
        engine.destroyIndexBuffer(indexBuffer);
        engine.destroyMaterialInstance(materialInstance);
        engine.destroyMaterial(material);
        engine.destroyView(view);
        engine.destroyScene(scene);
        engine.destroyCameraComponent(camera.getEntity());

        EntityManager em = EntityManager.get();
        em.destroy(light);
        em.destroy(renderable);
        em.destroy(camera.getEntity());

        engine.destroy();
    }

    private class FrameCallback implements Choreographer.FrameCallback {
        @Override
        public void doFrame(long frameTimeNanos) {
            choreographer.postFrameCallback(this);
            if (uiHelper.isReadyToRender()) {
                if (renderer.beginFrame(swapChain, frameTimeNanos)) {
                    renderer.render(view);
                    renderer.endFrame();
                }
            }
        }
    }

    private class SurfaceCallback implements UiHelper.RendererCallback {
        @Override
        public void onNativeWindowChanged(Surface surface) {
            if (swapChain != null) {
                engine.destroySwapChain(swapChain);
            }
            swapChain = engine.createSwapChain(surface);
            displayHelper.attach(renderer, surfaceView.getDisplay());
        }

        @Override
        public void onDetachedFromSurface() {
            displayHelper.detach();
            if (swapChain != null) {
                engine.destroySwapChain(swapChain);
                engine.flushAndWait();
                swapChain = null;
            }
        }

        @Override
        public void onResized(int width, int height) {
            double aspect = (double) width / (double) height;
            camera.setProjection(45.0, aspect, 0.1, 20.0, Camera.Fov.VERTICAL);
            view.setViewport(new Viewport(0, 0, width, height));
            FilamentHelper.synchronizePendingFrames(engine);
        }
    }

    /** Lê asset NÃO comprimido (requer noCompress 'filamat' no build). */
    private ByteBuffer readUncompressedAsset(String assetPath) {
        try {
            AssetFileDescriptor afd = getAssets().openFd(assetPath);
            long length = afd.getLength();
            FileInputStream input = afd.createInputStream();
            ByteBuffer dst = ByteBuffer.allocateDirect((int) length).order(ByteOrder.nativeOrder());
            Channels.newChannel(input).read(dst);
            input.close();
            afd.close();
            dst.rewind();
            return dst;
        } catch (Exception e) {
            throw new RuntimeException("Falha ao ler asset (provável compressão AAPT): " + assetPath, e);
        }
    }

    /** Alternativa: ler como stream (funciona mesmo comprimido). Use esta se não usar noCompress. */
    @SuppressWarnings("unused")
    private ByteBuffer readAssetAsByteBuffer(String assetPath) {
        try (java.io.InputStream in = getAssets().open(assetPath);
             java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(16 * 1024)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) baos.write(buf, 0, n);
            byte[] bytes = baos.toByteArray();
            ByteBuffer direct = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
            direct.put(bytes);
            direct.flip();
            return direct;
        } catch (Exception e) {
            throw new RuntimeException("Falha ao ler asset: " + assetPath, e);
        }
    }
}
