package com.example.filamenttestjava.vulkanapp;

import com.google.android.filament.Engine;
import com.google.android.filament.IndexBuffer;
import com.google.android.filament.MathUtils;
import com.google.android.filament.RenderableManager;
import com.google.android.filament.VertexBuffer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Malha dinâmica de triângulos para materiais "lit" com cor por vértice (RGBA).
 * Vértice: POSITION(float3) + TANGENTS(quat float4) + COLOR(UBYTE4 normalizado) = 32 bytes.
 * Usa índices de 32 bits (UINT) e ring buffer: quando atinge maxTriangles, sobrescreve os mais antigos.
 */
public class DynamicTriangleMesh {

    // --- layout ---
    private static final int FLOAT_SIZE = 4;
    // 3*4 (pos) + 4*4 (quat) + 4*1 (rgba) = 32 bytes por vértice
    private static final int STRIDE = (3 + 4) * FLOAT_SIZE + 4;

    // --- capacidade ---
    private final int maxTriangles;
    private final int maxVertices;
    private final int maxIndices;

    // GPU buffers
    private VertexBuffer vb;
    private IndexBuffer ib;

    // Sombras CPU
    private final ByteBuffer vertexShadow; // interleaved
    private final ByteBuffer indexShadow;  // UINT (4 bytes por índice)

    // contadores "visíveis"
    private int triCount = 0;
    private int vertexCount = 0;
    private int indexCount = 0;

    // ring buffer / estado
    private int writeTri = 0;        // próximo slot de triângulo [0..maxTriangles-1]
    private int filledTris = 0;      // quantos triângulos válidos (<= maxTriangles)
    private boolean wrapped = false; // já deu a volta pelo menos uma vez?
    private boolean indicesDirty = true;
    private boolean aabbDirty = true;

    // AABB acumulado do conteúdo atual (recomputado quando necessário)
    private float minX = +Float.MAX_VALUE, minY = +Float.MAX_VALUE, minZ = +Float.MAX_VALUE;
    private float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

    // cor atual (UBYTE [0..255])
    private byte cr = (byte)255, cg = (byte)255, cb = (byte)255, ca = (byte)255;

    public DynamicTriangleMesh(int maxTriangles) {
        if (maxTriangles <= 0) throw new IllegalArgumentException("maxTriangles must be > 0");

        this.maxTriangles = maxTriangles;
        this.maxVertices  = maxTriangles * 3;
        this.maxIndices   = maxTriangles * 3;

        vertexShadow = ByteBuffer
                .allocateDirect(maxVertices * STRIDE)
                .order(ByteOrder.nativeOrder());

        // Cada índice ocupa 4 bytes (UINT)
        indexShadow  = ByteBuffer
                .allocateDirect(maxIndices * 4)
                .order(ByteOrder.nativeOrder());
    }

    public void inicializaBuffers(Engine engine) {
        vb = new VertexBuffer.Builder()
                .bufferCount(1)
                .vertexCount(maxVertices)
                .attribute(VertexBuffer.VertexAttribute.POSITION, 0,
                        VertexBuffer.AttributeType.FLOAT3, 0, STRIDE)
                .attribute(VertexBuffer.VertexAttribute.TANGENTS, 0,
                        VertexBuffer.AttributeType.FLOAT4, 3 * FLOAT_SIZE, STRIDE)
                .attribute(VertexBuffer.VertexAttribute.COLOR, 0,
                        VertexBuffer.AttributeType.UBYTE4, 7 * FLOAT_SIZE, STRIDE)
                .normalized(VertexBuffer.VertexAttribute.COLOR)
                .build(engine);

        ib = new IndexBuffer.Builder()
                .indexCount(maxIndices)
                .bufferType(IndexBuffer.Builder.IndexType.UINT)
                .build(engine);
    }

    // --- getters ---
    public VertexBuffer getVertexBuffer() { return vb; }
    public IndexBuffer  getIndexBuffer()  { return ib; }
    public int getIndexCount()            { return indexCount; }
    public int getTriangleCount()         { return triCount; }

    // --- estado/cor atual ---
    /** r,g,b,a em [0..1]. */
    public void setCurrentColorSrgb(float r, float g, float b, float a) {
        cr = toU8(r); cg = toU8(g); cb = toU8(b); ca = toU8(a);
    }
    /** r,g,b,a em [0..255]. */
    public void setCurrentColorU8(int r, int g, int b, int a) {
        cr = (byte) clampU8(r);
        cg = (byte) clampU8(g);
        cb = (byte) clampU8(b);
        ca = (byte) clampU8(a);
    }

    /**
     * Cada item da lista é um triângulo double[9] = x0,y0,z0,x1,y1,z1,x2,y2,z2 (CCW).
     * A cor usada será a "current color" no momento da chamada.
     * Ring buffer: ao atingir maxTriangles, sobrescreve o triângulo mais antigo.
     */
    public void addTriangles(List<double[]> tris) {
        if (tris == null || tris.isEmpty()) return;

        for (double[] t : tris) {
            if (t == null || t.length != 9)
                throw new IllegalArgumentException("Cada triângulo deve ter 9 doubles (x0..z2).");

            float x0=(float)t[0], y0=(float)t[1], z0=(float)t[2];
            float x1=(float)t[3], y1=(float)t[4], z1=(float)t[5];
            float x2=(float)t[6], y2=(float)t[7], z2=(float)t[8];

            // normal da face (CCW)
            float ux=x1-x0, uy=y1-y0, uz=z1-z0;
            float vx=x2-x0, vy=y2-y0, vz=z2-z0;
            float nx = uy*vz - uz*vy;
            float ny = uz*vx - ux*vz;
            float nz = ux*vy - uy*vx;
            float inv = invLength(nx,ny,nz);
            if (inv == 0f) continue; // tri degenerado
            nx*=inv; ny*=inv; nz*=inv;

            // base T,B ortonormal a partir de N
            float[] T = new float[3], B = new float[3];
            makeTangentBasis(nx,ny,nz, T,B);

            // quat(T,B,N)
            float[] q = new float[4];
            MathUtils.packTangentFrame(T[0],T[1],T[2], B[0],B[1],B[2], nx,ny,nz, q);

            // slot de triângulo no ring buffer
            int triSlot = writeTri;            // [0..maxTriangles-1]
            int baseV   = triSlot * 3;         // 3 vértices por tri

            // escreve os 3 vértices nos offsets corretos do VB shadow
            writeVertexAt(baseV,     x0,y0,z0, q);
            writeVertexAt(baseV + 1, x1,y1,z1, q);
            writeVertexAt(baseV + 2, x2,y2,z2, q);

            // avança o ring
            writeTri = (writeTri + 1) % maxTriangles;
            if (filledTris < maxTriangles) {
                filledTris++;
            } else {
                wrapped = true; // começamos a sobrescrever antigos
            }
        }

        // counts lógicos (o VB pode conter dados fora da janela, mas desenhamos só filledTris)
        triCount    = filledTris;
        vertexCount = triCount * 3;
        indexCount  = triCount * 3;

        // índices e AABB precisam ser reconstituídos para refletir a janela atual
        indicesDirty = true;
        aabbDirty    = true;
    }

    /** Sobe o prefixo válido para GPU (VB e IB). Chamar na thread do Engine e fora de begin/endFrame. */
    public void upload(Engine engine) {
        // (1) Índices em ordem cronológica (mais antigo -> mais novo)
        if (indicesDirty) rebuildIndices();

        // (2) AABB (para usar em updateBoundingBox() depois)
        if (aabbDirty) recomputeAabb();

        // (3) Envia VB:
        //  - antes de “wrap”: vértices usados são contíguos [0..vertexCount)
        //  - após “wrap”: os índices podem apontar para slots altos -> envie o buffer inteiro
        ByteBuffer v = vertexShadow.duplicate().order(ByteOrder.nativeOrder());
        if (!wrapped) {
            v.position(0); v.limit(Math.max(0, vertexCount) * STRIDE);
        } else {
            v.position(0); v.limit(maxVertices * STRIDE);
        }
        vb.setBufferAt(engine, 0, v.slice().order(ByteOrder.nativeOrder()));

        // (4) Envia IB (sempre o prefixo vigente; 4 bytes por índice)
        ByteBuffer i = indexShadow.duplicate().order(ByteOrder.nativeOrder());
        i.position(0); i.limit(Math.max(0, indexCount) * 4);
        ib.setBuffer(engine, i.slice().order(ByteOrder.nativeOrder()));
    }

    /**
     * Atualiza a geometria do renderable existente (usa reflexão para setGeometryAt).
     */
    public void applyToRenderable(RenderableManager rm, int renderableEntity)
            throws UnsupportedOperationException {
        int inst = rm.getInstance(renderableEntity);
        try {
            Method m = rm.getClass().getMethod(
                    "setGeometryAt",
                    int.class, int.class,
                    RenderableManager.PrimitiveType.class,
                    VertexBuffer.class,
                    IndexBuffer.class,
                    int.class, int.class
            );
            m.invoke(rm, inst, 0,
                    RenderableManager.PrimitiveType.TRIANGLES,
                    this.vb, this.ib, 0, this.indexCount);
        } catch (Throwable t) {
            throw new UnsupportedOperationException("setGeometryAt indisponível nesta versão.", t);
        }
    }

    /** Atualiza o AABB do renderable (tenta várias assinaturas via reflexão). */
    public void updateBoundingBox(RenderableManager rm, int entity) {
        int inst = rm.getInstance(entity);
        final float eps = 1e-4f;

        float cx,cy,cz,hx,hy,hz;
        if (vertexCount == 0 || minX > maxX) {
            cx=0f; cy=0f; cz=0f; hx=eps; hy=eps; hz=eps;
        } else {
            cx = 0.5f*(minX+maxX);
            cy = 0.5f*(minY+maxY);
            cz = 0.5f*(minZ+maxZ);
            hx = Math.max(eps, 0.5f*(maxX-minX));
            hy = Math.max(eps, 0.5f*(maxY-minY));
            hz = Math.max(eps, 0.5f*(maxZ-minZ));
        }

        if (trySetAabbFloats(rm, "setAxisAlignedBoundingBox", inst, cx,cy,cz,hx,hy,hz)) return;
        if (trySetAabbBox   (rm, "setAxisAlignedBoundingBox", inst, cx,cy,cz,hx,hy,hz)) return;
        if (trySetAabbFloats(rm, "setBoundingBox",            inst, cx,cy,cz,hx,hy,hz)) return;
        if (trySetAabbBox   (rm, "setBoundingBox",            inst, cx,cy,cz,hx,hy,hz)) return;

        try { // fallback: desliga culling
            Method cull = rm.getClass().getMethod("setCulling", int.class, boolean.class);
            cull.invoke(rm, inst, false);
        } catch (Throwable ignored) {}
    }

    // --------------------
    // Helpers privados
    // --------------------

    private void writeVertexAt(int vertexIndex, float x, float y, float z, float[] q) {
        final int posBytes = vertexIndex * STRIDE;
        int oldPos = vertexShadow.position();
        vertexShadow.position(posBytes);
        vertexShadow.putFloat(x);
        vertexShadow.putFloat(y);
        vertexShadow.putFloat(z);
        vertexShadow.putFloat(q[0]);
        vertexShadow.putFloat(q[1]);
        vertexShadow.putFloat(q[2]);
        vertexShadow.putFloat(q[3]);
        vertexShadow.put(cr);
        vertexShadow.put(cg);
        vertexShadow.put(cb);
        vertexShadow.put(ca);
        vertexShadow.position(oldPos);
    }

    private void rebuildIndices() {
        indexShadow.clear();
        if (filledTris == 0) {
            indexCount = 0;
            indicesDirty = false;
            return;
        }
        // ordem cronológica: do mais antigo -> mais novo
        int start = wrapped ? writeTri : 0;
        for (int k = 0; k < filledTris; k++) {
            int tri = (start + k) % maxTriangles;
            int base = tri * 3;
            indexShadow.putInt(base);
            indexShadow.putInt(base + 1);
            indexShadow.putInt(base + 2);
        }
        indexCount = filledTris * 3;
        indexShadow.flip();
        indicesDirty = false;
    }

    // Recalcula AABB varrendo somente a janela ativa do ring buffer
    private void recomputeAabb() {
        final float INF = Float.MAX_VALUE;
        float mnx = +INF, mny = +INF, mnz = +INF;
        float mxx = -INF, mxy = -INF, mxz = -INF;

        if (filledTris == 0) {
            minX = minY = minZ = +INF;
            maxX = maxY = maxZ = -INF;
            aabbDirty = false;
            return;
        }

        int start = wrapped ? writeTri : 0;
        for (int k = 0; k < filledTris; k++) {
            int tri = (start + k) % maxTriangles;
            for (int v = 0; v < 3; v++) {
                int vi = tri * 3 + v;
                int off = vi * STRIDE;
                float x = vertexShadow.getFloat(off);
                float y = vertexShadow.getFloat(off + 4);
                float z = vertexShadow.getFloat(off + 8);
                if (x < mnx) mnx = x; if (y < mny) mny = y; if (z < mnz) mnz = z;
                if (x > mxx) mxx = x; if (y > mxy) mxy = y; if (z > mxz) mxz = z;
            }
        }
        minX = mnx; minY = mny; minZ = mnz;
        maxX = mxx; maxY = mxy; maxZ = mxz;
        aabbDirty = false;
    }

    private static boolean trySetAabbFloats(RenderableManager rm, String method,
                                            int inst, float cx,float cy,float cz,float hx,float hy,float hz) {
        try {
            Method m = rm.getClass().getMethod(method,
                    int.class, float.class,float.class,float.class,float.class,float.class,float.class);
            m.invoke(rm, inst, cx,cy,cz,hx,hy,hz);
            return true;
        } catch (Throwable ignore) { return false; }
    }

    private static boolean trySetAabbBox(RenderableManager rm, String method,
                                         int inst, float cx,float cy,float cz,float hx,float hy,float hz) {
        try {
            Class<?> boxCls = Class.forName("com.google.android.filament.Box");
            Constructor<?> ctor = boxCls.getConstructor(
                    float.class,float.class,float.class,float.class,float.class,float.class);
            Object box = ctor.newInstance(cx,cy,cz,hx,hy,hz);
            Method m = rm.getClass().getMethod(method, int.class, boxCls);
            m.invoke(rm, inst, box);
            return true;
        } catch (Throwable ignore) { return false; }
    }

    private static float invLength(float x,float y,float z) {
        double len = Math.sqrt(x*x + y*y + z*z);
        return len > 0 ? (float)(1.0/len) : 0f;
    }

    /** Gera T e B ortonormais à normal N. */
    private static void makeTangentBasis(float nx,float ny,float nz, float[] T, float[] B) {
        float ux, uy, uz = 0f;
        if (Math.abs(ny) < 0.999f) { ux = 0; uy = 1; } else { ux = 1; uy = 0; }
        // T = normalize(up x N)
        float tx = uy*nz - uz*ny;
        float ty = uz*nx - ux*nz;
        float tz = ux*ny - uy*nx;
        float inv = invLength(tx,ty,tz);
        if (inv == 0) { tx=1; ty=0; tz=0; inv=1; }
        tx*=inv; ty*=inv; tz*=inv;
        // B = N x T
        float bx = ny*tz - nz*ty;
        float by = nz*tx - nx*tz;
        float bz = nx*ty - ny*tx;
        T[0]=tx; T[1]=ty; T[2]=tz;
        B[0]=bx; B[1]=by; B[2]=bz;
    }

    // (não usado na versão ring, mantido por compatibilidade)
    @SuppressWarnings("unused")
    private void putV(float x,float y,float z,float[] q) {
        vertexShadow.putFloat(x);
        vertexShadow.putFloat(y);
        vertexShadow.putFloat(z);
        vertexShadow.putFloat(q[0]);
        vertexShadow.putFloat(q[1]);
        vertexShadow.putFloat(q[2]);
        vertexShadow.putFloat(q[3]);
        vertexShadow.put(cr);
        vertexShadow.put(cg);
        vertexShadow.put(cb);
        vertexShadow.put(ca);
    }

    private static byte toU8(float v01) {
        int v = Math.round(Math.max(0f, Math.min(1f, v01)) * 255f);
        return (byte) v;
    }

    private static int clampU8(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
