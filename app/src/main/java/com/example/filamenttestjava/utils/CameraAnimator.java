package com.example.filamenttestjava.utils;

import android.animation.ValueAnimator;
import android.os.Handler;
import android.view.animation.LinearInterpolator;

import androidx.dynamicanimation.animation.DynamicAnimation;

import com.google.android.filament.Camera;

public class CameraAnimator {

    // NOVO: anima eye E center
    public static void startEngineAnimator(ValueAnimator engineAnimator, Handler engineHandler,
                                    double[] fromEye,    double[] toEye,
                                           double[] fromCenter, double[] toCenter,
                                    float durMs,
                                    Camera camera) {


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

                // mesma thread do Engine
                //engineHandler.post(() -> {

                    camera.lookAt(ex, ey, ez, cx, cy, cz, 0f, 0f, 1f);
                //});
            });
        // inicia NA thread do engine
        engineHandler.post(engineAnimator::start);
    }



    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
