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
    public static List<double[]> makeUnitCubeTris(double fator, double dx, double dy, double dz) {
        double[] p000 = {-1,-1,-1}, p001 = {-1,-1, 1};
        double[] p010 = {-1, 1,-1}, p011 = {-1, 1, 1};
        double[] p100 = { 1,-1,-1}, p101 = { 1,-1, 1};
        double[] p110 = { 1, 1,-1}, p111 = { 1, 1, 1};

        aplicaFator(p000, fator, dx, dy, dz);
        aplicaFator(p010, fator, dx, dy, dz);
        aplicaFator(p100, fator, dx, dy, dz);
        aplicaFator(p110, fator, dx, dy, dz);
        aplicaFator(p001, fator, dx, dy, dz);
        aplicaFator(p011, fator, dx, dy, dz);
        aplicaFator(p101, fator, dx, dy, dz);
        aplicaFator(p111, fator, dx, dy, dz);

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

    private static void aplicaFator(double[] p, double fator, double dx, double dy, double dz) {
        p[0] = p[0]*fator + dx;
        p[1] = p[1]*fator + dy;
        p[2] = p[2]*fator + dz;
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

    // ===========================
//  ESFERA (UV-SPHERE)
// ===========================

    /**
     * Gera triângulos de uma esfera (UV sphere).
     *
     * @param center centro {cx, cy, cz}
     * @param radius raio (> 0)
     * @param stacks quantas divisões em latitude (mínimo 2)
     * @param slices quantas divisões em longitude (mínimo 3)
     * @return lista de triângulos double[9] (A,B,C em CCW, normais para fora)
     */
    public static List<double[]> makeUvSphereTris(double[] center, double radius, int stacks, int slices) {
        if (center == null || center.length < 3) throw new IllegalArgumentException("center inválido");
        if (radius <= 0) throw new IllegalArgumentException("radius deve ser > 0");
        if (stacks < 2) stacks = 2;
        if (slices < 3) slices = 3;

        final double cx = center[0], cy = center[1], cz = center[2];

        // Polos
        final double[] southPole = new double[]{ cx, cy - radius, cz }; // φ = -π/2
        final double[] northPole = new double[]{ cx, cy + radius, cz }; // φ = +π/2

        List<double[]> out = new ArrayList<>(stacks * slices * 2);

        // Para cada faixa de latitude
        for (int i = 0; i < stacks; i++) {
            double phi0 = -Math.PI / 2.0 + Math.PI * (double) i / (double) stacks;       // [-π/2 .. +π/2)
            double phi1 = -Math.PI / 2.0 + Math.PI * (double) (i + 1) / (double) stacks; // (-π/2 .. +π/2]

            // Pré-calcula seno/cosseno
            double c0 = Math.cos(phi0), s0 = Math.sin(phi0); // y = s, raio do paralelo = c
            double c1 = Math.cos(phi1), s1 = Math.sin(phi1);

            // Para cada fatia de longitude
            for (int j = 0; j < slices; j++) {
                double theta0 = 2.0 * Math.PI * (double) j / (double) slices;
                double theta1 = 2.0 * Math.PI * (double) (j + 1) / (double) slices;

                // Pontos do “quad” da faixa
                double[] A = sphPoint(cx, cy, cz, radius, c0, s0, theta0); // (phi0, theta0)
                double[] D = sphPoint(cx, cy, cz, radius, c0, s0, theta1); // (phi0, theta1)
                double[] B = sphPoint(cx, cy, cz, radius, c1, s1, theta0); // (phi1, theta0)
                double[] C = sphPoint(cx, cy, cz, radius, c1, s1, theta1); // (phi1, theta1)

                if (i == 0) {
                    // Sul (cap inferior): fan a partir do polo sul
                    // CCW para fora: (southPole, B, C)
                    out.add(tri(southPole, B, C));
                } else if (i == stacks - 1) {
                    // Norte (cap superior): fan para o polo norte
                    // CCW para fora: (A, D, northPole)
                    out.add(tri(A, D, northPole));
                } else {
                    // Faixas intermediárias: 2 triângulos por quad
                    // CCW para fora: (A,B,C) e (A,C,D)
                    out.add(tri(A, B, C));
                    out.add(tri(A, C, D));
                }
            }
        }
        return out;
    }

    /** Conversão paramétrica (φ,θ) para XYZ. Usa:
     * x = cx + r * cosφ * cosθ
     * y = cy + r * sinφ
     * z = cz + r * cosφ * sinθ
     *
     * Aqui passamos cosφ e sinφ já calculados (c = cosφ, s = sinφ) para eficiência.
     */
    private static double[] sphPoint(double cx, double cy, double cz,
                                     double r, double c, double s, double theta) {
        double ct = Math.cos(theta), st = Math.sin(theta);
        double x = cx + r * c * ct;
        double y = cy + r * s;
        double z = cz + r * c * st;
        return new double[]{ x, y, z };
    }

    // ===========================
//  CILINDRO (UV-CYLINDER)
// ===========================

    /**
     * Gera triângulos de um cilindro entre baseCenter (C0) e topCenter (C1).
     *
     * @param baseCenter centro da base {x,y,z}
     * @param topCenter  centro do topo {x,y,z}
     * @param radius     raio (>0)
     * @param slices     divisões angulares (>=3)
     * @param stacks     divisões ao longo do eixo (>=1)
     * @param withCaps   true para adicionar tampas (discos) nas extremidades
     * @return lista de triângulos double[9] (A,B,C em CCW, normais para fora)
     */
    public static List<double[]> makeCylinderTris(
            double[] baseCenter, double[] topCenter,
            double radius, int slices, int stacks, boolean withCaps) {

        if (baseCenter == null || baseCenter.length < 3) throw new IllegalArgumentException("baseCenter inválido");
        if (topCenter == null  || topCenter.length  < 3) throw new IllegalArgumentException("topCenter inválido");
        if (radius <= 0) throw new IllegalArgumentException("radius deve ser > 0");
        if (slices < 3)  slices = 3;
        if (stacks < 1)  stacks = 1;

        // eixo do cilindro
        double[] axis = sub(topCenter, baseCenter);
        double  axisLen = Math.sqrt(dot(axis, axis));
        //if (axisLen == 0) throw new IllegalArgumentException("C0 e C1 coincidem");
        double[] N = mul(axis, 1.0 / axisLen); // normalizado

        // base ortonormal (U,V,N) com U×V = N
        double[] U, V;
        {
            double[] ref = (Math.abs(N[1]) < 0.999) ? new double[]{0,1,0} : new double[]{1,0,0};
            U = normalize(cross(N, ref));
            V = cross(N, U); // já normal
        }

        // pré-aloca
        List<double[]> tris = new ArrayList<>( (stacks * slices * 2) + (withCaps ? 2*slices : 0) );

        // anéis ao longo do eixo
        for (int i = 0; i < stacks; i++) {
            double t0 = (double) i / (double) stacks;
            double t1 = (double) (i + 1) / (double) stacks;

            double[] C0 = add(baseCenter, mul(axis, t0));
            double[] C1 = add(baseCenter, mul(axis, t1));

            for (int j = 0; j < slices; j++) {
                double th0 = 2.0 * Math.PI * j / slices;
                double th1 = 2.0 * Math.PI * (j + 1) / slices;

                double[] r0 = add(mul(U, Math.cos(th0) * radius), mul(V, Math.sin(th0) * radius));
                double[] r1 = add(mul(U, Math.cos(th1) * radius), mul(V, Math.sin(th1) * radius));

                double[] A = add(C0, r0);
                double[] B = add(C1, r0);
                double[] C = add(C1, r1);
                double[] D = add(C0, r1);

                // duas faces por "quad" – CCW para fora
                tris.add(tri(A, C, B));
                tris.add(tri(A, D, C));
            }
        }

        if (withCaps) {
            // tampa inferior (base) — normal para fora é -N
            // CCW vista de fora: (rim_j, C_base, rim_j1)
            double[] Cb = baseCenter;
            for (int j = 0; j < slices; j++) {
                double th0 = 2.0 * Math.PI * j / slices;
                double th1 = 2.0 * Math.PI * (j + 1) / slices;
                double[] r0 = add(mul(U, Math.cos(th0) * radius), mul(V, Math.sin(th0) * radius));
                double[] r1 = add(mul(U, Math.cos(th1) * radius), mul(V, Math.sin(th1) * radius));
                double[] P0 = add(Cb, r0);
                double[] P1 = add(Cb, r1);
                tris.add(tri(P0, Cb, P1)); // ordem dá normal ~ -N
            }

            // tampa superior (topo) — normal para fora é +N
            // CCW vista de fora: (rim_j, rim_j1, C_top)
            double[] Ct = topCenter;
            for (int j = 0; j < slices; j++) {
                double th0 = 2.0 * Math.PI * j / slices;
                double th1 = 2.0 * Math.PI * (j + 1) / slices;
                double[] r0 = add(mul(U, Math.cos(th0) * radius), mul(V, Math.sin(th0) * radius));
                double[] r1 = add(mul(U, Math.cos(th1) * radius), mul(V, Math.sin(th1) * radius));
                double[] P0 = add(Ct, r0);
                double[] P1 = add(Ct, r1);
                tris.add(tri(P0, P1, Ct)); // ordem dá normal ~ +N
            }
        }

        return tris;
    }

    /**
     * Overload: altura escalar ao longo de +Y (eixo vertical).
     */
    public static List<double[]> makeCylinderTris(
            double[] baseCenter, double heightY,
            double radius, int slices, int stacks, boolean withCaps) {
        double[] top = new double[]{ baseCenter[0], baseCenter[1] + heightY, baseCenter[2] };
        return makeCylinderTris(baseCenter, top, radius, slices, stacks, withCaps);
    }

    // ---- helpers vetoriais (reutiliza padrão da classe) ----
    private static double[] add(double[] a, double[] b) {
        return new double[]{ a[0]+b[0], a[1]+b[1], a[2]+b[2] };
    }

    private static double[] mul(double[] a, double s) {
        return new double[]{ a[0]*s, a[1]*s, a[2]*s };
    }


    private static double[] normalize(double[] v) {
        double len = Math.sqrt(dot(v,v));
        if (len == 0) return new double[]{0,0,0};
        return new double[]{ v[0]/len, v[1]/len, v[2]/len };
    }


}
