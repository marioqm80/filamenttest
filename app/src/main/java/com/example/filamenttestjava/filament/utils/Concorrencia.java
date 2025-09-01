package com.example.filamenttestjava.filament.utils;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Concorrencia {
    public static void postAndWait(Handler h, Runnable r)  {
        if (Looper.myLooper() == h.getLooper()) { r.run();  }
        CountDownLatch latch = new CountDownLatch(1);
        h.post(() -> { try { r.run(); } finally { latch.countDown(); } });

        try {
            latch.await(0, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            //throw new RuntimeException(e);
        }
    }
}
