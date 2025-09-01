package com.example.filamenttestjava.filament.app;

import com.google.android.filament.Engine;
import com.google.android.filament.IndexBuffer;
import com.google.android.filament.MathUtils;
import com.google.android.filament.VertexBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MeshFactory {

    public static class Mesh {
        public final VertexBuffer vertexBuffer;
        public final IndexBuffer indexBuffer;
        public final int indexCount;
        public Mesh(VertexBuffer vb, IndexBuffer ib, int count) {
            this.vertexBuffer = vb; this.indexBuffer = ib; this.indexCount = count;
        }
    }

    /** Cubo com POSITION + TANGENTS(quat), correto para material "lit". */
    public static Mesh createLitCube(Engine engine) {
        final int floatSize = 4, shortSize = 2;
        final int vertexSize = (3 + 4) * floatSize; // XYZ + quat
        final int vertexCount = 6 * 4; // 6 faces x 4 vértices

        // Tangent frames por face — ORDEM:  T, B, N
        float[] tfPX = new float[4]; // +X
        float[] tfNX = new float[4]; // -X
        float[] tfPY = new float[4]; // +Y
        float[] tfNY = new float[4]; // -Y
        float[] tfPZ = new float[4]; // +Z
        float[] tfNZ = new float[4]; // -Z

        // +X: N=(+1,0,0), T=(0,1,0), B=(0,0,1)  => T x B = (+1,0,0)
        MathUtils.packTangentFrame( 0, 1, 0,    0, 0, 1,    1, 0, 0,  tfPX);
        // -X: N=(-1,0,0), T=(0,1,0), B=(0,0,-1) => T x B = (-1,0,0)
        MathUtils.packTangentFrame( 0, 1, 0,    0, 0,-1,   -1, 0, 0,  tfNX);
        // +Y: N=(0,+1,0), T=(1,0,0), B=(0,0,1)  => T x B = (0,+1,0)
        MathUtils.packTangentFrame( 1, 0, 0,    0, 0, 1,    0, 1, 0,  tfPY);
        // -Y: N=(0,-1,0), T=(1,0,0), B=(0,0,-1) => T x B = (0,-1,0)
        MathUtils.packTangentFrame( 1, 0, 0,    0, 0,-1,    0,-1, 0,  tfNY);
        // +Z: N=(0,0,+1), T=(1,0,0), B=(0,1,0)  => T x B = (0,0,+1)
        MathUtils.packTangentFrame( 1, 0, 0,    0, 1, 0,    0, 0, 1,  tfPZ);
        // -Z: N=(0,0,-1), T=(0,1,0), B=(1,0,0)  => T x B = (0,0,-1)
        MathUtils.packTangentFrame( 0, 1, 0,    1, 0, 0,    0, 0,-1,  tfNZ);

        ByteBuffer vertexData = ByteBuffer.allocateDirect(vertexCount * vertexSize)
                .order(ByteOrder.nativeOrder());

        // Vértices por face (mesmo layout que você já tinha)
        // -Z
        putV(vertexData, -1,-1,-1, tfNZ); putV(vertexData, -1, 1,-1, tfNZ);
        putV(vertexData,  1, 1,-1, tfNZ); putV(vertexData,  1,-1,-1, tfNZ);
        // +X
        putV(vertexData,  1,-1,-1, tfPX); putV(vertexData,  1, 1,-1, tfPX);
        putV(vertexData,  1, 1, 1, tfPX); putV(vertexData,  1,-1, 1, tfPX);
        // +Z
        putV(vertexData, -1,-1, 1, tfPZ); putV(vertexData,  1,-1, 1, tfPZ);
        putV(vertexData,  1, 1, 1, tfPZ); putV(vertexData, -1, 1, 1, tfPZ);
        // -X
        putV(vertexData, -1,-1, 1, tfNX); putV(vertexData, -1, 1, 1, tfNX);
        putV(vertexData, -1, 1,-1, tfNX); putV(vertexData, -1,-1,-1, tfNX);
        // -Y
        putV(vertexData, -1,-1, 1, tfNY); putV(vertexData, -1,-1,-1, tfNY);
        putV(vertexData,  1,-1,-1, tfNY); putV(vertexData,  1,-1, 1, tfNY);
        // +Y
        putV(vertexData, -1, 1,-1, tfPY); putV(vertexData, -1, 1, 1, tfPY);
        putV(vertexData,  1, 1, 1, tfPY); putV(vertexData,  1, 1,-1, tfPY);

        vertexData.flip();

        VertexBuffer vb = new VertexBuffer.Builder()
                .bufferCount(1)
                .vertexCount(vertexCount)
                .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0,                 vertexSize)
                .attribute(VertexBuffer.VertexAttribute.TANGENTS, 0, VertexBuffer.AttributeType.FLOAT4, 3 * floatSize,     vertexSize)
                .build(engine);
        vb.setBufferAt(engine, 0, vertexData);

        // Índices: 6 faces * 2 triângulos/face * 3 índices
        final int indexCount = 6 * 2 * 3;
        ByteBuffer indexData = ByteBuffer.allocateDirect(indexCount * shortSize).order(ByteOrder.nativeOrder());
        for (int f = 0; f < 6; f++) {
            short i = (short) (f * 4);
            indexData.putShort(i).putShort((short)(i+1)).putShort((short)(i+2));
            indexData.putShort(i).putShort((short)(i+2)).putShort((short)(i+3));
        }
        indexData.flip();

        IndexBuffer ib = new IndexBuffer.Builder()
                .indexCount(indexCount)
                .bufferType(IndexBuffer.Builder.IndexType.USHORT)
                .build(engine);
        ib.setBuffer(engine, indexData);

        return new Mesh(vb, ib, indexCount);
    }

    private static void putV(ByteBuffer dst, float x, float y, float z, float[] q) {
        dst.putFloat(x); dst.putFloat(y); dst.putFloat(z);
        dst.putFloat(q[0]); dst.putFloat(q[1]); dst.putFloat(q[2]); dst.putFloat(q[3]);
    }
}
