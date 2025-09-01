package com.example.filamenttestjava;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;

import com.example.filamenttestjava.filament.utils.AssetLinePublisher;
import com.example.filamenttestjava.filament.utils.CalculoDistancias;
import com.example.filamenttestjava.filament.utils.CorUtil;
import com.example.filamenttestjava.filament.utils.IgcParser;
import com.example.filamenttestjava.filament.app.FilamentApp;
import com.example.filamenttestjava.filament.app.Geometry;
import com.google.android.filament.android.DisplayHelper;
import com.google.android.filament.android.UiHelper;

import java.text.DecimalFormat;
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

    private PublishSubject<Boolean> volumeAjustado = PublishSubject.create();

    private final AssetLinePublisher publisher = new AssetLinePublisher();

    private final PublishSubject<double[]> latLonSubject = PublishSubject.create();
    private final PublishSubject<double[]> pixelModeloSubject = PublishSubject.create();

    private final PublishSubject<double[]> altitudeGpsPublisher = PublishSubject.create();

    public static final long sleep = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        volumeAjustado.serialize();

        //Looper looper = Looper.myLooper();      // null se a thread não tiver Looper
        //Handler currentHandler = new Handler(looper);

        HandlerThread  ht= new HandlerThread("MarioFilamentThread");
        ht.start();
        Handler currentHandler = new Handler(ht.getLooper());

        app = new FilamentApp(this , 300000, volumeAjustado);

        publisher.lines()
                .filter(l -> l != null && l.startsWith("B"))
                .subscribeOn(Schedulers.io())
                .subscribe(
                line -> {
                    // cada linha do arquivo
                    // ex.: log/atualiza UI

                    double[] latLon = IgcParser.parseBRecordLatLon(line);
                    //System.out.println("latitude e longitude = " + new DecimalFormat("0.00000").format(latLon[0]) + " lon " + new DecimalFormat("0.00000").format(latLon[1]));
                    Thread.sleep(sleep);
                    int altitudeGpsMetros = IgcParser.parseBRecordGpsAltitudeMeters(line);
                    latLonSubject.onNext(new double[] {latLon[0], latLon[1], (double) altitudeGpsMetros});
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
                        pixelModeloSubject.onNext(new double[] {0, 0, latLon[2]});
                    } else {
                        double[] modelo = CalculoDistancias.getPixelModelo(latLonInicial.get()[0],
                                latLonInicial.get()[1],
                                latLon[0],
                                latLon[1]);
                        pixelModeloSubject.onNext(new double[] {modelo[0], modelo[1], latLon[2]});
                    }
                });

        currentHandler.postDelayed(() -> {

            pixelModeloSubject
                    .subscribeOn(Schedulers.io())
                    .buffer(2, 1)
                    .filter(list -> list.size() == 2)
                    .subscribe( listaModelo -> {
                        double[] modelo = listaModelo.get(1);
                        //app.atualizaNovaPosicaoCamera(modelo);
                        float[] eyeTo = new float[] {(float) modelo[0], (float) modelo[0], 100};
                        float[] centerTo = new float[] {(float) modelo[0], (float) modelo[0], (float) modelo[0]};

                    });
        }, 0);

        currentHandler.postDelayed(() -> {
            pixelModeloSubject
                    .subscribeOn(Schedulers.io())
                    .buffer(2, 1)
                    .filter(list -> list.size() == 2)
                    .subscribe( listaModelo -> {

                        double maxVarioCor = 5;
                        double vario = listaModelo.get(1)[2] - listaModelo.get(0)[2];
                        double[] corVario2 = CorUtil.getCorVarioErico(false, 255, vario, -3d, maxVarioCor, 0d, maxVarioCor / 3f, (maxVarioCor * 2f) / 3f);

                        final List<double[]> cube2 = Geometry.makeCylinderTris(listaModelo.get(0), listaModelo.get(1), 4, 8, 8, true);
                        System.out.println("vai adicionar triangulo: ");
                        app.addTriangles(cube2, 1, corVario2);
                        System.out.println("concluiu vai adicionar triangulo: ");


                        app.atualizaNovaPosicaoCamera(listaModelo.get(0), listaModelo.get(1));


                    });
        }, 0);



        surfaceView = new SurfaceView(this);
        setContentView(surfaceView);

        choreographer = Choreographer.getInstance();
        displayHelper = new DisplayHelper(this);



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
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        app.start(); // animação etc.
        choreographer.postFrameCallback(frameCallback);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            // Usuário apertou volume +
            Toast.makeText(this, "Volume +", Toast.LENGTH_SHORT).show();
            volumeAjustado.onNext(true);
            return true; // consome o evento (não passa pro sistema)
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            // Usuário apertou volume -
            Toast.makeText(this, "Volume -", Toast.LENGTH_SHORT).show();
            volumeAjustado.onNext(false);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
