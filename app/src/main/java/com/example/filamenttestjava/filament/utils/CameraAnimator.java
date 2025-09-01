package com.example.filamenttestjava.filament.utils;

import android.animation.ValueAnimator;
import android.os.Handler;
import android.view.animation.LinearInterpolator;

import com.example.filamenttestjava.filament.app.eventos.NovaPosicaoCameraAtualizada;

import io.reactivex.rxjava3.subjects.PublishSubject;

public class CameraAnimator {

    // NOVO: anima eye E center
    public static void startEngineAnimator(ValueAnimator engineAnimator,  Handler calculoHandler,
                                           double[] fromEye, double[] toEye,
                                           double[] fromCenter, double[] toCenter,
                                           float durMs,
                                           PublishSubject<NovaPosicaoCameraAtualizada> publisherNovaPosicao) {


            engineAnimator.setDuration((long) durMs);
            engineAnimator.setInterpolator(new LinearInterpolator());
        engineAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationStart(android.animation.Animator animation) {
                System.out.println("animação start ");
            }
            @Override public void onAnimationEnd(android.animation.Animator animation)   {
                System.out.println("animação end ");
            }
        });
            engineAnimator.addUpdateListener(a -> {
                float t = (float) a.getAnimatedValue();

                double ex = lerp(fromEye[0],    toEye[0],    t);
                double ey = lerp(fromEye[1],    toEye[1],    t);
                double ez = lerp(fromEye[2],    toEye[2],    t);

                double cx = lerp(fromCenter[0], toCenter[0], t);
                double cy = lerp(fromCenter[1], toCenter[1], t);
                double cz = lerp(fromCenter[2], toCenter[2], t);

                NovaPosicaoCameraAtualizada nova = new NovaPosicaoCameraAtualizada(ex, ey, ez, cx, cy, cz, 0, 0, 1);
                publisherNovaPosicao.onNext(nova);


            });
        // inicia NA thread do engine
        calculoHandler.post(engineAnimator::start);
    }



    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
