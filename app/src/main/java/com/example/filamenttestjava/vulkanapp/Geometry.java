package com.example.filamenttestjava.vulkanapp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Geometry {
    private Geometry() {}

    private static double[] tri(double[] a, double[] b, double[] c) {
        return new double[]{ a[0],a[1],a[2], b[0],b[1],b[2], c[0],c[1],c[2] };
    }

    /** Cubo de lado 2 centrado na origem. */
    public static List<double[]> makeUnitCubeTris(double fator, double dx) {
        double[] p000 = {-1,-1,-1}, p001 = {-1,-1, 1};
        double[] p010 = {-1, 1,-1}, p011 = {-1, 1, 1};
        double[] p100 = { 1,-1,-1}, p101 = { 1,-1, 1};
        double[] p110 = { 1, 1,-1}, p111 = { 1, 1, 1};

        aplicaFator(p000, fator, dx);
        aplicaFator(p010, fator, dx);
        aplicaFator(p100, fator, dx);
        aplicaFator(p110, fator, dx);
        aplicaFator(p001, fator, dx);
        aplicaFator(p011, fator, dx);
        aplicaFator(p101, fator, dx);
        aplicaFator(p111, fator, dx);

        List<double[]> tris = new ArrayList<>(12);
        // -Z
        tris.add(tri(p000, p010, p110)); tris.add(tri(p000, p110, p100));
        // +Z
        tris.add(tri(p001, p101, p111)); tris.add(tri(p001, p111, p011));
        // +X
        tris.add(tri(p100, p110, p111)); tris.add(tri(p100, p111, p101));
        // -X
        tris.add(tri(p001, p011, p010)); tris.add(tri(p001, p010, p000));
        // -Y
        tris.add(tri(p000, p100, p101)); tris.add(tri(p000, p101, p001));
        // +Y
        tris.add(tri(p010, p011, p111)); tris.add(tri(p010, p111, p110));
        return tris;
    }

    private static void aplicaFator(double[] p, double fator, double dx) {
        p[0] = p[0]*fator + dx;
        p[1] = p[1]*fator;
        p[2] = p[2]*fator;
    }

    // ===========================
    //  QUAD / PLANO COM 4 PONTOS
    // ===========================

    /** Duas faces usando a diagonal A–C: (A,B,C) e (A,C,D). */
    public static List<double[]> makeQuadTrisAC(double[] A, double[] B, double[] C, double[] D) {
        return Arrays.asList(
                tri(A,B,C),
                tri(A,C,D)
        );
    }
    public static List<double[]> makeQuadDuplaFace(double[] A, double[] B, double[] C, double[] D) {
        return Arrays.asList(
                tri(A,B,C),
                tri(A,C,D)
                ,
                tri(C,B,A),
                tri(D,C,A)
        );
    }

    /** Duas faces usando a diagonal B–D: (B,C,D) e (B,D,A). */
    public static List<double[]> makeQuadTrisBD(double[] A, double[] B, double[] C, double[] D) {
        return Arrays.asList(
                tri(B,C,D),
                tri(B,D,A)
        );
    }

    /**
     * Escolhe automaticamente a diagonal (AC ou BD) pela maior soma de áreas
     * e garante que as duas faces tenham normais consistentes (mesmo lado).
     * Funciona bem para quads convexos; se for côncavo/não plano, ainda triangula.
     */
    public static List<double[]> makeQuadTrisAuto(double[] A, double[] B, double[] C, double[] D) {
        double areaAC = triArea(A,B,C) + triArea(A,C,D);
        double areaBD = triArea(B,C,D) + triArea(B,D,A);

        List<double[]> tris = (areaAC >= areaBD)
                ? new ArrayList<>(makeQuadTrisAC(A,B,C,D))
                : new ArrayList<>(makeQuadTrisBD(A,B,C,D));

        // Tornar normais consistentes (se necessário inverte o 2º triângulo)
        double[] n0 = triNormal(tris.get(0));
        double[] n1 = triNormal(tris.get(1));
        if (dot(n0, n1) < 0.0) {
            // inverte ordem do 2º triângulo
            double[] t = tris.get(1);
            double[] a = {t[0],t[1],t[2]};
            double[] b = {t[3],t[4],t[5]};
            double[] c = {t[6],t[7],t[8]};
            tris.set(1, tri(a,c,b));
        }
        return tris;
    }

    // ===========================
    //  HELPERS GEOMÉTRICOS
    // ===========================

    private static double triArea(double[] A, double[] B, double[] C) {
        double[] u = sub(B, A);
        double[] v = sub(C, A);
        double[] c = cross(u, v);
        return 0.5 * Math.sqrt(dot(c, c));
    }

    /** Normal não normalizada do triângulo (A,B,C). */
    private static double[] triNormal(double[] tri) {
        double[] A = {tri[0],tri[1],tri[2]};
        double[] B = {tri[3],tri[4],tri[5]};
        double[] C = {tri[6],tri[7],tri[8]};
        return cross(sub(B,A), sub(C,A));
    }

    private static double[] sub(double[] p, double[] q) {
        return new double[]{ p[0]-q[0], p[1]-q[1], p[2]-q[2] };
    }
    private static double[] cross(double[] u, double[] v) {
        return new double[]{
                u[1]*v[2] - u[2]*v[1],
                u[2]*v[0] - u[0]*v[2],
                u[0]*v[1] - u[1]*v[0]
        };
    }
    private static double dot(double[] u, double[] v) {
        return u[0]*v[0] + u[1]*v[1] + u[2]*v[2];
    }
}
