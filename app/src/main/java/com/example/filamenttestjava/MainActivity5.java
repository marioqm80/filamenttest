package com.example.filamenttestjava;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;
import android.view.Surface;
import android.view.SurfaceView;

import com.example.filamenttestjava.utils.AssetLinePublisher;
import com.example.filamenttestjava.utils.CalculoDistancias;
import com.example.filamenttestjava.utils.IgcParser;
import com.example.filamenttestjava.vulkanapp.FilamentApp;
import com.example.filamenttestjava.vulkanapp.Geometry;
import com.google.android.filament.android.DisplayHelper;
import com.google.android.filament.android.UiHelper;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;

public class MainActivity5 extends Activity {

    static {
        try {
            System.loadLibrary("filament-jni");
        } catch (UnsatisfiedLinkError e) {
            throw new RuntimeException("Falha ao carregar filament-jni", e);
        }
    }

    /*public static final float dxInicial = -3.6491666667f;
    public static final float dyInicial = 37.7518666667f;*/

    public static final float dxInicial = 0f;
    public static final float dyInicial = 0f;
    private SurfaceView surfaceView;
    private UiHelper uiHelper;
    private DisplayHelper displayHelper;
    private Choreographer choreographer;
    private final FrameCallback frameCallback = new FrameCallback();

    private FilamentApp app;

    private final AssetLinePublisher publisher = new AssetLinePublisher();

    private final PublishSubject<double[]> latLonSubject = PublishSubject.create();
    private final PublishSubject<double[]> pixelModeloSubject = PublishSubject.create();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        publisher.lines()
                .filter(l -> l != null && l.startsWith("B"))
                .subscribeOn(Schedulers.io())
                .subscribe(
                line -> {
                    // cada linha do arquivo
                    // ex.: log/atualiza UI

                    double[] latLon = IgcParser.parseBRecordLatLon(line);
                    //System.out.println("latitude e longitude = " + new DecimalFormat("0.00000").format(latLon[0]) + " lon " + new DecimalFormat("0.00000").format(latLon[1]));
                    Thread.sleep(10);
                    latLonSubject.onNext(latLon);
                },
                throwable -> {
                    // tratar erro de leitura
                },
                () -> {
                    // terminou de ler
                }
        );



        publisher.read(this, "test.igc");

        latLonSubject
                .subscribeOn(Schedulers.io())
                .subscribe( latLon -> {

                });
        final AtomicReference<double[]> latLonInicial = new AtomicReference<>(null);
        latLonSubject
                .subscribeOn(Schedulers.io())
                .subscribe( latLon -> {
                    System.out.println("latitude e longitude = " + new DecimalFormat("0.0000000000").format(latLon[0]) + " lon " + new DecimalFormat("0.0000000000").format(latLon[1]));
                    if (latLonInicial.get() == null) {
                        double[] tmp = new double[] {latLon[0], latLon[1]};
                        latLonInicial.set(tmp);
                        pixelModeloSubject.onNext(new double[] {0, 0});
                    } else {
                        pixelModeloSubject.onNext(CalculoDistancias.getPixelModelo(latLonInicial.get()[0],
                                latLonInicial.get()[1],
                                latLon[0],
                                latLon[1]));
                    }
                });



        surfaceView = new SurfaceView(this);
        setContentView(surfaceView);

        choreographer = Choreographer.getInstance();
        displayHelper = new DisplayHelper(this);

        app = new FilamentApp(this /*context*/);

        uiHelper = new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK);
        uiHelper.setRenderCallback(new UiHelper.RendererCallback() {
            @Override
            public void onNativeWindowChanged(Surface surface) {
                app.onSurfaceAvailable(surface);
                // opcional: anexar DisplayHelper para lidar com rotação/refresh-rate
                displayHelper.attach(app.getRenderer(), surfaceView.getDisplay());


            }

            @Override
            public void onDetachedFromSurface() {
                displayHelper.detach();
                app.onSurfaceDestroyed();
            }

            @Override
            public void onResized(int width, int height) {
                app.onResized(width, height);
            }
        });
        uiHelper.attachTo(surfaceView);

        Looper looper = Looper.myLooper();      // null se a thread não tiver Looper
        Handler currentHandler = new Handler(looper);
        // <= AQUI: popular a malha dinâmica antes de renderizar
        List<double[]> cube = Geometry.makeUnitCubeTris(0.2, 0.3 + MainActivity5.dxInicial, 0 + MainActivity5.dyInicial, 0);
        long delay = 1;
        int cont = 1;
        for (double[] triangulo: cube) {
            List<double[]> tmp = Arrays.asList(triangulo);
            currentHandler.postDelayed(() -> {
                app.addTriangles(tmp, 0); // usa o seu método que faz dyn.addTriangles+upload+apply+AABB

            }, cont * delay);
            cont++;

        }

        cube = Geometry.makeUnitCubeTris(0.1, 0, 0, 0);
         delay = 1000;
         cont = 1;
        for (double[] triangulo: cube) {
            List<double[]> tmp = Arrays.asList(triangulo);
            currentHandler.postDelayed(() -> {
                app.addTriangles(tmp, 0); // usa o seu método que faz dyn.addTriangles+upload+apply+AABB

            }, cont * delay);
            cont++;

        }

        double dist = 50.8d;
        double z = -10;
        List<double[]> plano = Geometry.makeQuadDuplaFace(new double[] {-dist + dxInicial, -dist + dyInicial, z}, new double[] {-dist + dxInicial, dist + dyInicial, z}, new double[] {dist + dxInicial, dist + dyInicial, z}, new double[] {dist + dxInicial, -dist + dyInicial, z});
        currentHandler.postDelayed(() ->{
            //app.addTriangles(plano, 1);
        }, 0);

        pixelModeloSubject
                .subscribeOn(Schedulers.io())
                .subscribe( modelo -> {
                    System.out.println("pixel modelo x y= " + new DecimalFormat("0.0000000000").format(modelo[0]) + " lon " + new DecimalFormat("0.0000000000").format(modelo[1]));
                    final List<double[]> cube2 = Geometry.makeUnitCubeTris(5, modelo[0], modelo[1], 0);
                    app.addTriangles(cube2, 1);
                });


    }

    @Override
    protected void onResume() {
        super.onResume();
        app.start(); // animação etc.
        choreographer.postFrameCallback(frameCallback);
    }

    @Override
    protected void onPause() {
        super.onPause();
        choreographer.removeFrameCallback(frameCallback);
        app.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiHelper.detach();
        app.destroy();
    }

    private class FrameCallback implements Choreographer.FrameCallback {
        @Override public void doFrame(long frameTimeNanos) {
            choreographer.postFrameCallback(this);
            app.render(frameTimeNanos);
        }
    }
}
