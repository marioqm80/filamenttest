package com.example.filamenttestjava.filament.app.eventos;

public class NovaPosicaoCameraAtualizada {
    private final double ex, ey, ez, cx, cy, cz, upx, upy, upz;

    public NovaPosicaoCameraAtualizada(double ex, double ey, double ez, double cx, double cy, double cz, double upx, double upy, double upz) {
        this.ex = ex;
        this.ey = ey;
        this.ez = ez;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.upx = upx;
        this.upy = upy;
        this.upz = upz;
    }

    public double getEx() {
        return ex;
    }

    public double getEy() {
        return ey;
    }

    public double getEz() {
        return ez;
    }

    public double getCx() {
        return cx;
    }

    public double getCy() {
        return cy;
    }

    public double getCz() {
        return cz;
    }

    public double getUpx() {
        return upx;
    }

    public double getUpy() {
        return upy;
    }

    public double getUpz() {
        return upz;
    }
}
