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
 * Layout do vértice: POSITION(float3) + TANGENTS(quat float4) + COLOR(UBYTE4 normalizado).
 */
public class DynamicTriangleMesh {

    // --- layout ---
    private static final int FLOAT_SIZE = 4;
    // 3*4 (pos) + 4*4 (quat) + 4*1 (rgba) = 32 bytes
    private static final int STRIDE = (3 + 4) * FLOAT_SIZE + 4;

    // --- engine & buffers ---

    private final int maxTriangles;
    private final int maxVertices;
    private final int maxIndices;

    private  VertexBuffer vb;
    private  IndexBuffer ib;

    // CPU-side shadows
    private final ByteBuffer vertexShadow;
    private final ByteBuffer indexShadow; // USHORT

    // contadores atuais (prefixo válido)
    private int triCount = 0;
    private int vertexCount = 0;
    private int indexCount = 0;

    // AABB acumulado do conteúdo
    private float minX = +Float.MAX_VALUE, minY = +Float.MAX_VALUE, minZ = +Float.MAX_VALUE;
    private float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

    // cor atual (aplicada aos próximos vértices/triângulos) - UBYTE [0..255]
    private byte cr = (byte)255, cg = (byte)255, cb = (byte)255, ca = (byte)255;

    public DynamicTriangleMesh( int maxTriangles) {
        if (maxTriangles <= 0) throw new IllegalArgumentException("maxTriangles must be > 0");

        this.maxTriangles = maxTriangles;
        this.maxVertices  = maxTriangles * 3;
        this.maxIndices   = maxTriangles * 3;

        if (maxVertices > 65535) {
            throw new IllegalArgumentException("maxTriangles*3 > 65535. Use IndexType.UINT se precisar de mais.");
        }

        vertexShadow = ByteBuffer.allocateDirect(maxVertices * STRIDE).order(ByteOrder.nativeOrder());
        indexShadow  = ByteBuffer.allocateDirect(maxIndices * 2).order(ByteOrder.nativeOrder());


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
                .bufferType(IndexBuffer.Builder.IndexType.USHORT)
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
     * Gera tangente/bitangente a partir da normal da face.
     */
    public void addTriangles(List<double[]> tris) {
        if (tris == null || tris.isEmpty()) return;
        if (triCount + tris.size() > maxTriangles) {
            throw new IllegalStateException("Capacidade excedida: " + (triCount + tris.size()) + " > " + maxTriangles);
        }

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

            // 3 vértices com mesma cor atual
            putV(x0,y0,z0,q);
            putV(x1,y1,z1,q);
            putV(x2,y2,z2,q);

            // índices
            short base = (short) vertexCount;
            indexShadow.putShort(base);
            indexShadow.putShort((short)(base+1));
            indexShadow.putShort((short)(base+2));

            vertexCount += 3;
            indexCount  += 3;
            triCount    += 1;

            // AABB
            minX = Math.min(minX, Math.min(x0, Math.min(x1, x2)));
            minY = Math.min(minY, Math.min(y0, Math.min(y1, y2)));
            minZ = Math.min(minZ, Math.min(z0, Math.min(z1, z2)));
            maxX = Math.max(maxX, Math.max(x0, Math.max(x1, x2)));
            maxY = Math.max(maxY, Math.max(y0, Math.max(y1, y2)));
            maxZ = Math.max(maxZ, Math.max(z0, Math.max(z1, z2)));
        }
    }

    /** Sobe o prefixo válido para GPU (VB e IB). */
    public void upload(Engine engine) {
        ByteBuffer v = vertexShadow.duplicate().order(ByteOrder.nativeOrder());
        v.position(0); v.limit(vertexCount * STRIDE);
        vb.setBufferAt(engine, 0, v.slice().order(ByteOrder.nativeOrder()));

        ByteBuffer i = indexShadow.duplicate().order(ByteOrder.nativeOrder());
        i.position(0); i.limit(indexCount * 2);
        ib.setBuffer(engine, i.slice().order(ByteOrder.nativeOrder()));
    }

    /**
     * Atualiza a geometria de um renderable existente usando reflexão para setGeometryAt.
     * Requer uma AAR do Filament que exponha esse método — caso contrário, lança UnsupportedOperationException.
     */
    public void applyToRenderable(RenderableManager rm, int renderableEntity)
            throws UnsupportedOperationException {
        int inst = rm.getInstance(renderableEntity);
        try {
            Method m = rm.getClass().getMethod(
                    "setGeometryAt",
                    int.class, int.class,
                    RenderableManager.PrimitiveType.class,
                    com.google.android.filament.VertexBuffer.class,
                    com.google.android.filament.IndexBuffer.class,
                    int.class, int.class
            );
            m.invoke(rm, inst, 0,
                    RenderableManager.PrimitiveType.TRIANGLES,
                    this.vb, this.ib, 0, this.indexCount);
        } catch (Throwable t) {
            throw new UnsupportedOperationException("setGeometryAt indisponível nesta versão.", t);
        }
    }

    /**
     * Atualiza o AABB do renderable (tenta várias assinaturas via reflexão).
     * Se nada existir, desliga culling como fallback.
     */
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

        // fallback: sem API para AABB -> desliga culling do objeto
        try {
            Method cull = rm.getClass().getMethod("setCulling", int.class, boolean.class);
            cull.invoke(rm, inst, false);
        } catch (Throwable ignored) {}
    }

    // --- helpers privados ---

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

    private void putV(float x,float y,float z,float[] q) {
        vertexShadow.putFloat(x);
        vertexShadow.putFloat(y);
        vertexShadow.putFloat(z);
        vertexShadow.putFloat(q[0]);
        vertexShadow.putFloat(q[1]);
        vertexShadow.putFloat(q[2]);
        vertexShadow.putFloat(q[3]);
        // COLOR (UBYTE4)
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
