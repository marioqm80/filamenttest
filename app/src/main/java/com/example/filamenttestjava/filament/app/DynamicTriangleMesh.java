package com.example.filamenttestjava.filament.app;

import android.os.Handler;

import com.example.filamenttestjava.filament.utils.Concorrencia;
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
 * Malha dinâmica de triângulos (Filament "lit") com cor por vértice (RGBA).
 * Vértice: POSITION(float3) + TANGENTS(quat float4) + COLOR(UBYTE4 normalizado) = 32 bytes.
 * Índices de 32 bits (UINT). IB é estático (0..N-1). Os vértices são gravados em
 * slots fixos com ring buffer: quando atinge a capacidade, os novos triângulos
 * sobrescrevem os mais antigos. AABB é fixo (aplique no renderable uma vez).
 */
public class DynamicTriangleMesh {

    // --- layout ---
    private static final int FLOAT_SIZE = 4;
    private static final int STRIDE = (3 + 4) * FLOAT_SIZE + 4; // 32 bytes

    // --- capacidade ---
    private final int maxTriangles;
    private final int maxVertices;
    private final int maxIndices;

    // GPU
    private VertexBuffer vb;
    private IndexBuffer ib;

    // CPU shadows
    private final ByteBuffer vertexShadow; // interleaved
    private final ByteBuffer indexShadow;  // UINT (4 bytes)

    // contadores lógicos
    private int triCount = 0;
    private int vertexCount = 0;
    private int indexCount = 0;

    // ring buffer
    private int writeTri = 0;     // próximo slot [0..maxTriangles-1]
    private int filledTris = 0;   // quantos slots já preenchidos
    private boolean wrapped = false;

    // cor atual (UBYTE)
    private byte cr = (byte)255, cg = (byte)255, cb = (byte)255, ca = (byte)255;

    // AABB fixo (defina fora desta classe, 1x, no renderable)
    private final float fixedCx, fixedCy, fixedCz;
    private final float fixedHx, fixedHy, fixedHz;
    private volatile Engine engine;

    private volatile Handler engineHandler;

    public DynamicTriangleMesh(int maxTriangles) {
        this(maxTriangles, 0f,0f,0f,
                autoHalfExtent(maxTriangles),
                autoHalfExtent(maxTriangles),
                autoHalfExtent(maxTriangles));
    }

    public DynamicTriangleMesh(int maxTriangles,
                               float cx, float cy, float cz,
                               float hx, float hy, float hz) {
        this.engine = null;
        this.engineHandler = null;
        if (maxTriangles <= 0) throw new IllegalArgumentException("maxTriangles must be > 0");
        this.maxTriangles = maxTriangles;
        this.maxVertices  = maxTriangles * 3;
        this.maxIndices   = maxTriangles * 3;

        this.fixedCx = cx; this.fixedCy = cy; this.fixedCz = cz;
        this.fixedHx = Math.max(1e-4f, hx);
        this.fixedHy = Math.max(1e-4f, hy);
        this.fixedHz = Math.max(1e-4f, hz);

        vertexShadow = ByteBuffer.allocateDirect(maxVertices * STRIDE).order(ByteOrder.nativeOrder());
        indexShadow  = ByteBuffer.allocateDirect(maxIndices * 4).order(ByteOrder.nativeOrder());
    }

    private static float autoHalfExtent(int maxTriangles) {
        float e = (float)Math.cbrt(Math.max(1, maxTriangles)) * 4f;
        return Math.max(128f, e);
    }

    public void setEngineHandler(Engine engine, Handler engineHandler) {
        this.engine = engine;
        this.engineHandler = engineHandler;
    }

    public void inicializaBuffers(Engine engine, Handler engineHandler) {
        setEngineHandler(engine, engineHandler);
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

        // IB estático: 0,1,2,3,4,5...
        indexShadow.clear();
        for (int i = 0; i < maxIndices; i++) indexShadow.putInt(i);
        indexShadow.flip();
        Concorrencia.postAndWait(engineHandler, () -> {
            ib.setBuffer(engine, indexShadow.duplicate());
        });
    }

    // getters
    public VertexBuffer getVertexBuffer() { return vb; }
    public IndexBuffer  getIndexBuffer()  { return ib; }
    public int getIndexCount()            { return indexCount; }
    public int getTriangleCount()         { return triCount; }

    // cor atual
    public void setCurrentColorSrgb(float r, float g, float b, float a) {
        cr = toU8(r); cg = toU8(g); cb = toU8(b); ca = toU8(a);
    }
    public void setCurrentColorU8(int r, int g, int b, int a) {
        cr = (byte)Math.max(0, Math.min(255, r));
        cg = (byte)Math.max(0, Math.min(255, g));
        cb = (byte)Math.max(0, Math.min(255, b));
        ca = (byte)Math.max(0, Math.min(255, a));
    }

    /**
     * Ring buffer real: cada novo triângulo vai para o slot (writeTri*3..+2).
     * Quando enche, sobrescreve os mais antigos e mantém o draw sempre em maxTriangles.
     */
    public void addTriangles(List<double[]> tris) {
        if (tris == null || tris.isEmpty()) return;

        for (double[] t : tris) {
            if (t == null || t.length != 9)
                throw new IllegalArgumentException("Cada triângulo deve ter 9 doubles (x0..z2).");

            float x0=(float)t[0], y0=(float)t[1], z0=(float)t[2];
            float x1=(float)t[3], y1=(float)t[4], z1=(float)t[5];
            float x2=(float)t[6], y2=(float)t[7], z2=(float)t[8];

            // normal CCW
            float ux=x1-x0, uy=y1-y0, uz=z1-z0;
            float vx=x2-x0, vy=y2-y0, vz=z2-z0;
            float nx = uy*vz - uz*vy;
            float ny = uz*vx - ux*vz;
            float nz = ux*vy - uy*vx;
            float inv = invLength(nx,ny,nz);
            if (inv == 0f) continue;
            nx*=inv; ny*=inv; nz*=inv;

            // base T,B e quat
            float[] T = new float[3], B = new float[3];
            makeTangentBasis(nx,ny,nz, T,B);
            float[] q = new float[4];
            MathUtils.packTangentFrame(T[0],T[1],T[2], B[0],B[1],B[2], nx,ny,nz, q);

            // slot do ring
            int triSlot = writeTri;          // [0..maxTriangles-1]
            int baseV   = triSlot * 3;

            writeVertexAt(baseV,     x0,y0,z0, q);
            writeVertexAt(baseV + 1, x1,y1,z1, q);
            writeVertexAt(baseV + 2, x2,y2,z2, q);

            writeTri = (writeTri + 1) % maxTriangles;

            if (filledTris < maxTriangles) {
                filledTris++;
            } else {
                wrapped = true; // já estamos sobrescrevendo
            }
        }

        triCount    = filledTris;
        vertexCount = triCount * 3;
        indexCount  = triCount * 3;
        if (wrapped) {
            // Cheio: desenhe sempre TODOS os índices
            vertexCount = maxVertices;
            indexCount  = maxIndices;
        }

        if (engine != null) {
            upload(engine);
        }

        
    }

    /** Envia VB; quando ‘wrapped’, enviamos o buffer todo (índices podem apontar para slots altos). */
    public void upload(Engine engine) {
        ByteBuffer v = vertexShadow.duplicate().order(ByteOrder.nativeOrder());
        if (!wrapped) {
            v.position(0);
            v.limit(Math.max(0, vertexCount) * STRIDE);
        } else {
            v.position(0);
            v.limit(maxVertices * STRIDE);
        }
        Concorrencia.postAndWait(engineHandler, () -> {

            vb.setBufferAt(engine, 0, v.slice().order(ByteOrder.nativeOrder()));
        });
    }

    /** Atualiza a geometria do renderable (count varia conforme triCount/indexCount). */
    public void applyToRenderable(RenderableManager rm, int renderableEntity)
            throws UnsupportedOperationException {
        Concorrencia.postAndWait(engineHandler, () -> {

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
        });
    }

    // -------------------- helpers --------------------

    private void writeVertexAt(int vertexIndex, float x, float y, float z, float[] q) {
        final int off = vertexIndex * STRIDE;
        vertexShadow.putFloat(off      , x);
        vertexShadow.putFloat(off +  4 , y);
        vertexShadow.putFloat(off +  8 , z);
        vertexShadow.putFloat(off + 12 , q[0]);
        vertexShadow.putFloat(off + 16 , q[1]);
        vertexShadow.putFloat(off + 20 , q[2]);
        vertexShadow.putFloat(off + 24 , q[3]);
        vertexShadow.put(     off + 28 , cr);
        vertexShadow.put(     off + 29 , cg);
        vertexShadow.put(     off + 30 , cb);
        vertexShadow.put(     off + 31 , ca);
    }

    private static float invLength(float x,float y,float z) {
        double len = Math.sqrt(x*x + y*y + z*z);
        return len > 0 ? (float)(1.0/len) : 0f;
    }

    private static void makeTangentBasis(float nx,float ny,float nz, float[] T, float[] B) {
        float ux, uy, uz = 0f;
        if (Math.abs(ny) < 0.999f) { ux = 0; uy = 1; } else { ux = 1; uy = 0; }
        float tx = uy*nz - uz*ny;
        float ty = uz*nx - ux*nz;
        float tz = ux*ny - uy*nx;
        float inv = invLength(tx,ty,tz);
        if (inv == 0) { tx=1; ty=0; tz=0; inv=1; }
        tx*=inv; ty*=inv; tz*=inv;
        float bx = ny*tz - nz*ty;
        float by = nz*tx - nx*tz;
        float bz = nx*ty - ny*tx;
        T[0]=tx; T[1]=ty; T[2]=tz;
        B[0]=bx; B[1]=by; B[2]=bz;
    }

    private static byte toU8(float v01) {
        int v = Math.round(Math.max(0f, Math.min(1f, v01)) * 255f);
        return (byte) v;
    }

    // --------- (opcional) aplicar AABB fixo no renderable 1x fora desta classe ---------

    public void applyFixedAabb(RenderableManager rm, int entity) {
        int inst = rm.getInstance(entity);
        if (trySetAabbBox(rm, "setAxisAlignedBoundingBox", inst, fixedCx,fixedCy,fixedCz, fixedHx,fixedHy,fixedHz)) return;
        if (trySetAabbBox(rm, "setBoundingBox",            inst, fixedCx,fixedCy,fixedCz, fixedHx,fixedHy,fixedHz)) return;
        if (trySetAabbFloats(rm, "setAxisAlignedBoundingBox", inst, fixedCx,fixedCy,fixedCz, fixedHx,fixedHy,fixedHz)) return;
        if (trySetAabbFloats(rm, "setBoundingBox",            inst, fixedCx,fixedCy,fixedCz, fixedHx,fixedHy,fixedHz)) return;
        try {
            Method cull = rm.getClass().getMethod("setCulling", int.class, boolean.class);
            cull.invoke(rm, inst, false);
        } catch (Throwable ignored) {}
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


}
