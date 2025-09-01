package com.example.filamenttestjava.filament.utils;



import android.content.Context;
import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;

public class AssetLinePublisher {

    private final PublishSubject<String> subject = PublishSubject.create();
    private final CompositeDisposable disposables = new CompositeDisposable();

    /** Assine isto para receber as linhas. */
    public Observable<String> lines() {
        // hide() evita expor os métodos de Subject para quem consome
        return subject.hide();
    }

    /** Lê um arquivo de assets e emite cada linha como String. */
    public void read(Context ctx, String assetPath) {
        Disposable d = Observable.<String>create(emitter -> {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(
                                    ctx.getAssets().open(assetPath),
                                    StandardCharsets.UTF_8))) {

                        String line;
                        while ((line = br.readLine()) != null && !emitter.isDisposed()) {
                            emitter.onNext(line);
                        }
                        if (!emitter.isDisposed()) emitter.onComplete();
                    } catch (Exception e) {
                        if (!emitter.isDisposed()) emitter.onError(e);
                    }
                })
                .subscribeOn(Schedulers.io())               // I/O thread para leitura
                .observeOn(Schedulers.io())  // entrega na UI (se quiser)
                .subscribe(
                        subject::onNext,
                        subject::onError,
                        subject::onComplete
                );

        disposables.add(d);
    }

    /** Cancela leituras/assinaturas. Chame em onDestroy. */
    public void dispose() {
        disposables.dispose();
    }
}

