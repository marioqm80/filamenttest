package com.example.filamenttestjava.vulkanapp;

import android.content.Context;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Surface;
import android.view.animation.LinearInterpolator;
import android.animation.ValueAnimator;

import com.example.filamenttestjava.MainActivity5;

import com.example.filamenttestjava.utils.CalculoVetor;
import com.example.filamenttestjava.utils.CameraAnimator;
import com.google.android.filament.Box;
import com.google.android.filament.Camera;
import com.google.android.filament.Colors;
import com.google.android.filament.Engine;
import com.google.android.filament.Entity;
import com.google.android.filament.EntityManager;
import com.google.android.filament.RenderableManager;
import com.google.android.filament.Renderer;
import com.google.android.filament.Scene;
import com.google.android.filament.Skybox;
import com.google.android.filament.SwapChain;
import com.google.android.filament.View;
import com.google.android.filament.Viewport;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;

public class FilamentApp {

    private final Context context;

    private Engine engine;
    private Renderer renderer;
    private Scene scene;
    private View view;
    private Camera camera;


    private SwapChain swapChain;

    // Material
    private com.google.android.filament.Material material;
    private com.google.android.filament.MaterialInstance materialInstance;

    // Malha dinâmica
    private final int maxTriangles;


    private DynamicTriangleMesh dyn;

    private DynamicTriangleMesh dyn2;

    @Entity private int renderable = 0;
    @Entity private int renderablePlano = 1;
    @Entity private int sun = 0;

    @Entity private int camEntity = 0;

    HandlerThread filamentThread;

    private final ValueAnimator animator = ValueAnimator.ofFloat(0f, 360f);
    private final ValueAnimator animatorPlano = ValueAnimator.ofFloat(0f, 360f);

    /** Construtor padrão (capacidade “ok” para crescer). */
    public FilamentApp(Context ctx) { this(ctx, 300000); }

    /** Construtor com capacidade máxima de triângulos. */
    public FilamentApp(Context ctx, int maxTriangles) {
        pedeAtualizarTela.toFlowable(BackpressureStrategy.LATEST)
                .observeOn(Schedulers.io(), false, 1)
                .subscribe(t -> {
                    List<double[]> antesDepois = (List<double[]>) t;
                    executaAtualizaNovaPosicaoCamera(antesDepois.get(0), antesDepois.get(1));
                });

        filamentThread = new HandlerThread("FilamentThread");
        filamentThread.start();
        engineHandler = new Handler(filamentThread.getLooper());

        this.context = ctx.getApplicationContext();
        this.maxTriangles = maxTriangles;

        runOnEngine(() -> {
            initEngine();
            initView();
            initScene();




        });
    }


    private final android.os.Handler engineHandler;
    private static final Object monitor = new Object();

    public void runOnEngine(Runnable r) {
        if (Looper.myLooper() == filamentThread.getLooper()) r.run();
        else engineHandler.post(r);
    }



    private void initEngine() {


            engine = Engine.create(Engine.Backend.VULKAN); // troque para OPENGL se quiser





            if (engine == null) throw new RuntimeException("Vulkan não suportado");

            renderer = engine.createRenderer();
            scene = engine.createScene();
            view = engine.createView();
            camEntity = EntityManager.get().create();
            camera = engine.createCamera(camEntity)
            ;


    }

    private void initView() {
        //scene.setSkybox(new Skybox.Builder().color(0.035f, 0.035f, 0.035f, 1.0f).build(engine));
        //scene.setSkybox(new Skybox.Builder().color(1f, 0f, 1f, 1f).build(engine));
        scene.setSkybox(new Skybox.Builder().color(1f, 1f, 1f, 0.3f).build(engine));

        view.setCamera(camera);
        view.setScene(scene);
    }

    private void initScene() {
        // material
        material = MaterialProvider.loadFromAssets(engine, context.getAssets(), "materials/lit2.filamat");
        materialInstance = material.createInstance();
        //materialInstance.setParameter("baseColor", Colors.RgbType.SRGB, 1.0f, 0.85f, 0.57f);
        materialInstance.setParameter("metallic", 0.5f);
        materialInstance.setParameter("roughness", 0.1f);

        dyn2 = new DynamicTriangleMesh(maxTriangles);
        dyn2.inicializaBuffers(engine, engineHandler);
        //dyn2.upload(engine);






        // malha dinâmica (começa vazia)
        dyn = new DynamicTriangleMesh( maxTriangles);
        dyn.inicializaBuffers(engine, engineHandler);
        //dyn.upload(engine);



        renderable = EntityManager.get().create();

        new RenderableManager.Builder(1)
                .boundingBox(new Box(0f, 0f, 0f, 1e-3f, 1e-3f, 1e-3f))
                .geometry(0, RenderableManager.PrimitiveType.TRIANGLES,
                        dyn.getVertexBuffer(), dyn.getIndexBuffer(), 0, dyn.getIndexCount())
                .material(0, materialInstance)
                .build(engine, renderable);
        scene.addEntity(renderable);

        // renderable inicial — count=0, AABB não-vazio minúsculo para evitar crash
        renderablePlano = EntityManager.get().create();
        new RenderableManager.Builder(1)
                .boundingBox(new Box(0f, 0f, 0f, 1000000f, 1000000f, 1000000f))
                .geometry(0, RenderableManager.PrimitiveType.TRIANGLES,
                        dyn2.getVertexBuffer(), dyn2.getIndexBuffer(), 0, dyn2.getIndexCount())
                .material(0, materialInstance)
                .build(engine, renderablePlano);
        scene.addEntity(renderablePlano);




        // sol
        sun = LightFactory.createSun(engine, scene);

        // exposição e câmera
        camera.setExposure(16.0f, 1.0f / 125.0f, 100.0f);
        //camera.lookAt(0.0, 3.0, 4.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0);
        camera.lookAt(0.0 + MainActivity5.dxInicial, 0.0 + MainActivity5.dyInicial, 8000.1, 0.0 + MainActivity5.dxInicial, 0.0 + MainActivity5.dyInicial, -10.0, 0.0, 1.0, 0.0);

        // animação de rotação
        animator.setInterpolator(new LinearInterpolator());
        animator.setDuration(2000);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(a -> {
            float[] m = new float[16];
            Matrix.setRotateM(m, 0, (Float) a.getAnimatedValue(), 0.3f, 0.8f, 0.2f);
            TransformCompat.set(engine, renderable, m);
        });

        animatorPlano.setInterpolator(new LinearInterpolator());
        animatorPlano.setDuration(2000);
        animatorPlano.setRepeatMode(ValueAnimator.RESTART);
        animatorPlano.setRepeatCount(ValueAnimator.INFINITE);
        animatorPlano.addUpdateListener(a -> {
            float[] m = new float[16];
            Matrix.setRotateM(m, 0, (Float) a.getAnimatedValue(), 0.0f, 0.0f, 0.2f);
            //TransformCompat.set(engine, renderablePlano, m);
        });
    }

    // ——— ciclo de vida de surface / render ———
    private volatile ValueAnimator cameraAnimator = null;

    private double[] ultimoEye = null;
    private double[] ultimoCenter = null;

    private PublishSubject pedeAtualizarTela = PublishSubject.create();


    public synchronized void atualizaNovaPosicaoCamera(double[] posXYant, double[] posXYatual) {

        pedeAtualizarTela.onNext(Arrays.asList(posXYant, posXYatual));

    }
    public synchronized void executaAtualizaNovaPosicaoCamera(double[] posXYant, double[] posXYatual) {
        System.out.println("vai atualizar nova posicao");
        if (cameraAnimator != null && cameraAnimator.isRunning()) {

            return;
        }
        double z = 250;

        double distancia = CalculoVetor.distance2D(posXYant, posXYatual);

        double[] proxEyetmp = posXYant.clone();
        proxEyetmp[2] = proxEyetmp[2] + distancia;


        double[] normal = CalculoVetor.unitDirection2D(posXYatual, proxEyetmp);

        normal = CalculoVetor.raiseXYUnitVector(normal, 45);

        double[] proxEye = CalculoVetor.advanceAlong(posXYatual, normal, z);

        if (ultimoEye == null) {
            ultimoEye = proxEye;
            ultimoCenter = posXYatual.clone();
            return;
        }



        double[] eyeAnt = ultimoEye.clone();
        double[] eyeDepois = proxEye.clone();

        double[] centerAnt = ultimoCenter.clone();
        double[] centerDepois = posXYatual.clone();




        if (cameraAnimator == null || !cameraAnimator.isRunning()) {

            this.cameraAnimator = ValueAnimator.ofFloat(0f, 1f);
            CameraAnimator.startEngineAnimator(
                    cameraAnimator,
                    engineHandler,
                    eyeAnt,
                    eyeDepois,
                    centerAnt,
                    centerDepois,
                    (float) (MainActivity5.sleep) *0.98f,

                    camera);
            this.ultimoCenter = centerDepois.clone();
            this.ultimoEye = eyeDepois.clone();
        }
        while (cameraAnimator.isRunning()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {

            }
        }

        System.out.println("concluiu vai atualizar nova posicao");

    }

    public void onSurfaceAvailable(Surface surface) {
        runOnEngine(() -> {

            if (swapChain != null) {
                engine.destroySwapChain(swapChain);
            }
            swapChain = engine.createSwapChain(surface);
        });
    }

    public void onSurfaceDestroyed() {
        runOnEngine(() -> {

            if (swapChain != null) {
                engine.destroySwapChain(swapChain);
                engine.flushAndWait();
                swapChain = null;
            }
        });
    }

    public void onResized(int width, int height) {
        runOnEngine(() -> {

            double aspect = (double) width / (double) height;

            double vfov = 55.0;             // mais “GoPro”, mais cenário, mais distorção
            double near = 0.2;
            double far  = 300.0;
            camera.setProjection(vfov, aspect, near, far, Camera.Fov.VERTICAL);

            view.setViewport(new Viewport(0, 0, width, height));
        });
    }

    public void render(long frameTimeNanos) {
        runOnEngine(() -> {
            if (swapChain == null) return;
            synchronized (monitor) {
                if (renderer.beginFrame(swapChain, frameTimeNanos)) {
                    renderer.render(view);
                    renderer.endFrame();
                }
            }

        });
    }

    public void start() { animator.start(); animatorPlano.start(); }
    public void stop()  { animator.cancel(); animatorPlano.cancel();}

    public Renderer getRenderer() { return renderer; }

    public void destroy() {
        stop();
        onSurfaceDestroyed();

        // remover da cena antes de destruir entidades
        if (renderable != 0) scene.removeEntity(renderable);
        if (sun != 0) scene.removeEntity(sun);

        engine.destroyEntity(sun);
        engine.destroyEntity(renderable);

        // destruir buffers dinâmicos
        if (dyn != null) {
            engine.destroyVertexBuffer(dyn.getVertexBuffer());
            engine.destroyIndexBuffer(dyn.getIndexBuffer());
        }

        engine.destroyMaterialInstance(materialInstance);
        engine.destroyMaterial(material);
        engine.destroyView(view);
        engine.destroyScene(scene);
        engine.destroyCameraComponent(camera.getEntity());

        EntityManager em = EntityManager.get();
        em.destroy(sun);
        em.destroy(renderable);
        em.destroy(camera.getEntity());

        engine.destroy();
    }

    // ——— API dinâmica ———

    public void addTriangles(List<double[]> tris, int dynNumber, double[] cor) {
      //runOnEngine(() -> {
          executeAddTriangles(tris, dynNumber, cor);
      //});



    }

    /**
     * Acrescenta triângulos e atualiza a geometria/AABB.
     * Chame SEMPRE na thread do Engine (geralmente UI) e FORA de beginFrame..endFrame.
     */
    private void executeAddTriangles(List<double[]> tris, int dynNumber, double[] cor) {

        DynamicTriangleMesh dyn = dynNumber   == 0 ? this.dyn : this.dyn2;
        int renderable = dynNumber == 0 ? this.renderable : this.renderablePlano;
        if (tris == null || tris.isEmpty()) return;

        dyn.setCurrentColorSrgb((float)cor[0], (float)cor[1], (float)cor[2], (float)cor[3]);
        // 1) escreve em buffers de CPU

        dyn.addTriangles(tris);
        // 2) sobe prefixo válido para GPU
        //dyn.upload(engine);

        // 3) tenta atualizar o count desenhado sem recriar o renderable
        RenderableManager rm = engine.getRenderableManager();
        try {
            dyn.applyToRenderable(rm, renderable);
        } catch (UnsupportedOperationException ex) {
//            // Fallback (versões antigas sem setGeometryAt): recria o renderable
//            final int old = renderable;
//            scene.removeEntity(old);
//            engine.destroyEntity(old);
//            EntityManager.get().destroy(old);
//
//            renderable = EntityManager.get().create();
//            new RenderableManager.Builder(1)
//                    .boundingBox(new Box(0f, 0f, 0f, 1e-3f, 1e-3f, 1e-3f))
//                    .geometry(0, RenderableManager.PrimitiveType.TRIANGLES,
//                            dyn.getVertexBuffer(), dyn.getIndexBuffer(), 0, dyn.getIndexCount())
//                    .material(0, materialInstance)
//                    .build(engine, renderable);
//            scene.addEntity(renderable);
        }

        // 4) atualiza o AABB de acordo com o conteúdo atual
        //dyn.updateBoundingBox(engine.getRenderableManager(), renderable);
    }
}
