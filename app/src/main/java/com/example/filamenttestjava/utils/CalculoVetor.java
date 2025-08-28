package com.example.filamenttestjava.utils;

public class CalculoVetor {

    public static double[] advanceAlong(double[] p, double[] unitDir, double meters) {
        if (p == null || unitDir == null || p.length != unitDir.length) {
            throw new IllegalArgumentException("p e unitDir devem ter mesmo tamanho.");
        }
        double[] out = new double[p.length];
        for (int i = 0; i < p.length; i++) {
            out[i] = p[i] + unitDir[i] * meters;
        }
        return out;
    }

    public static double[] raiseXYUnitVector(double[] uXYUnit, double degrees) {
        if (uXYUnit == null || uXYUnit.length < 3) {
            throw new IllegalArgumentException("Vetor deve ter ao menos 3 componentes.");
        }
        // Garante que está normalizado no plano XY
        double lenXY = Math.hypot(uXYUnit[0], uXYUnit[1]);
        //if (lenXY == 0.0) throw new IllegalArgumentException("Vetor no plano XY é nulo.");

        double ux = uXYUnit[0] / lenXY;
        double uy = uXYUnit[1] / lenXY;

        double rad = Math.toRadians(degrees);
        double c = Math.cos(rad);
        double s = Math.sin(rad);

        return new double[] { ux * c, uy * c, s };
    }
    public static double distance3D(double[] a, double[] b) {
        if (a == null || b == null || a.length < 3 || b.length < 3) {
            throw new IllegalArgumentException("a e b devem ter pelo menos 3 componentes.");
        }
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        double dz = a[2] - b[2];
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    public static double distance2D(double[] a, double[] b) {
        if (a == null || b == null || a.length < 3 || b.length < 3) {
            throw new IllegalArgumentException("a e b devem ter pelo menos 3 componentes.");
        }
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        double dz = a[2] - b[2];
        return Math.sqrt(dx*dx + dy*dy);
    }
    public  static double[] unitDirection2D(double[] a, double[] b) {
        double[] aClone = a.clone();
        double[] bClone = b.clone();
        aClone[2] = 0;
        bClone[2] = 0;
        return unitDirection(aClone, bClone);

    }
    public static double[] unitDirection(double[] a, double[] b) {
        if (a == null || b == null || a.length != 3 || b.length != 3) {
            throw new IllegalArgumentException("a e b devem ser vetores de 3 elementos.");
        }
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        double dz = b[2] - a[2];

        double len = Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (len == 0.0) {
            // Pontos iguais: direção indefinida; devolve zero.
            return new double[]{0.0, 0.0, 0.0};
        }
        return new double[]{ dx/len, dy/len, dz/len };
    }

    public static double[] unitDirectionInvertido(double[] a, double[] b) {
        double[] output = unitDirection(a, b);
        output[0] *=-1;
        output[1] *=-1;
        output[2] *=-1;
        return output;
    }


}
