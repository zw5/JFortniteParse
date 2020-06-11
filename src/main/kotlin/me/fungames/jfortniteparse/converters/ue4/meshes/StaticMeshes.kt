@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_UNSIGNED_LITERALS")

package me.fungames.jfortniteparse.converters.ue4.meshes

import glm_.vec3.Vec3
import glm_.vec4.Vec4
import me.fungames.jfortniteparse.exceptions.ParserException
import me.fungames.jfortniteparse.ue4.UClass
import me.fungames.jfortniteparse.ue4.assets.exports.UObject
import me.fungames.jfortniteparse.ue4.assets.exports.UStaticMesh
import me.fungames.jfortniteparse.ue4.assets.objects.FBox
import me.fungames.jfortniteparse.ue4.assets.objects.FColor
import me.fungames.jfortniteparse.ue4.assets.objects.FPackedNormal
import me.fungames.jfortniteparse.ue4.assets.objects.FSphere


class CStaticMesh(val originalMesh : UObject, val boundingBox : FBox, val boundingSphere : FSphere, val lods : Array<CStaticMeshLod>)

class CStaticMeshLod : CBaseMeshLod() {
    lateinit var verts : Array<CStaticMeshVertex>

    fun allocateVerts(count : Int) {
        verts = Array(count) { CStaticMeshVertex(Vec4(), CPackedNormal(), CPackedNormal(), CMeshUVFloat()) }
        numVerts = count
        allocateUVBuffers()
    }
}

class CStaticMeshVertex(position: Vec4, normal: CPackedNormal, tangent: CPackedNormal, uv: CMeshUVFloat) :
    CMeshVertex(position, normal, tangent, uv)

fun UStaticMesh.convertMesh() {

    // convert bounds
    val boundingSphere = FSphere(0f, 0f, 0f, bounds.sphereRadius / 2) //?? UE3 meshes has radius 2 times larger than mesh itself; verify for UE4
    val boundingBox = FBox(bounds.origin - bounds.boxExtent, bounds.origin + bounds.boxExtent)

    // convert lods
    val lods = mutableListOf<CStaticMeshLod>()
    for (lodIndex in this.lods.indices) {
        val srcLod = this.lods[lodIndex]

        val numTexCoords = srcLod.vertexBuffer.numTexCoords
        val numVerts = srcLod.positionVertexBuffer.verts.size

        if (numVerts == 0 && numTexCoords == 0 && lodIndex < this.lods.size - 1) {
            UClass.logger.debug { "Lod $lodIndex is stripped, skipping..." }
            continue
        }

        if (numTexCoords > MAX_MESH_UV_SETS)
            throw ParserException("Static mesh has too many UV sets ($numTexCoords)")

        val lod = CStaticMeshLod()
        lod.numTexCoords = numTexCoords
        lod.hasNormals = true
        lod.hasTangents = true

        // sections
        val sections = mutableListOf<CMeshSection>()
        for (src in srcLod.sections) {
            val material = materials.getOrNull(src.materialIndex)
            sections.add(CMeshSection(material, src.firstIndex, src.numTriangles))
        }
        lod.sections = sections.toTypedArray()

        // vertices
        lod.allocateVerts(numVerts)
        if (srcLod.colorVertexBuffer.numVertices != 0)
            lod.allocateVertexColorBuffer()
        for (i in 0 until numVerts) {
            val suv = srcLod.vertexBuffer.uv[i]
            val v = lod.verts[i]

            v.position = srcLod.positionVertexBuffer.verts[i].toVec4()
            unpackNormals(suv.normal, v)
            // copy UV
            v.uv = CMeshUVFloat(suv.uv[0])
            for (texCoordIndex in 1 until numTexCoords) {
                lod.extraUV[texCoordIndex - 1][i].u = suv.uv[texCoordIndex].u
                lod.extraUV[texCoordIndex - 1][i].v = suv.uv[texCoordIndex].v
            }
            if (srcLod.colorVertexBuffer.numVertices != 0)
                lod.vertexColors[i] = srcLod.colorVertexBuffer.data[i]
        }

        // indices
        lod.indices = CIndexBuffer(srcLod.indexBuffer.indices16, srcLod.indexBuffer.indices32)
    }

    TODO("Finalize Mesh")

}

