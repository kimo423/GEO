package com.geo.ledger.data

import com.geo.ledger.data.transfer.*
import com.geo.ledger.data.local.*
import com.geo.ledger.data.attachments.AttachmentPolicy
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Assert.*
import java.io.File
import java.util.UUID
import java.util.zip.*

class ArchiveSecurityTest {
    @get:Rule val tmp=TemporaryFolder()
    private fun tx()=TransactionEntity(type=TransactionType.INCOME,amountCents=100,transactionDate=20000,createdAtMillis=1,updatedAtMillis=1)
    private fun mutate(input: File, output: File, transform: (MutableMap<String,ByteArray>)->Unit) {
        val entries=ZipFile(input).use { zip -> zip.entries().asSequence().associate { it.name to zip.getInputStream(it).use { s->s.readBytes() } }.toMutableMap() }
        transform(entries)
        ZipOutputStream(output.outputStream()).use { zip -> entries.forEach { (name,bytes)-> zip.putNextEntry(ZipEntry(name));zip.write(bytes);zip.closeEntry() } }
    }
    @Suppress("UNCHECKED_CAST")
    @Test fun corruptAndMaliciousPackagesRejected() {
        val file=tmp.newFile("blob").apply { writeText("invoice") }
        val entity=tx()
        val ref=AttachmentRef(UUID.randomUUID().toString(),UUID.randomUUID().toString(),"发票.pdf","application/pdf",file.length(),DataArchive.hash(file.readBytes()),0)
        val snapshot=TransactionSnapshot(entity,listOf(ref))
        val event=AuditEventEntity(UUID.randomUUID().toString(),entity.transactionUuid,"EDIT","USER",2,
            beforeSnapshotJson=snapshot.copy(transaction=entity.copy(amountCents=50)).encode(),afterSnapshotJson=snapshot.encode())
        val source=tmp.newFile("good.geodata")
        DataArchive.write(ArchiveDataset(listOf(snapshot),listOf(event)),source,{file},"1.2.0",6)
        source.inputStream().use { DataArchive.read(it,tmp.root).close() }
        val attacks=listOf<Pair<String,(MutableMap<String,ByteArray>)->Unit>>(
            "missing manifest" to { it.remove("manifest.json");Unit },
            "broken manifest" to { it["manifest.json"]="{".toByteArray() },
            "broken transactions" to { it["transactions.json"]="{".toByteArray() },
            "broken audit" to { it["audit_logs.json"]="[".toByteArray() },
            "missing attachment" to { map->map.keys.first { it.startsWith("attachments/") }.let { map.remove(it) };Unit },
            "wrong bytes" to { map->map[map.keys.first { it.startsWith("attachments/") }]="changed".toByteArray() },
            "zip slip" to { it["../evil"]=byteArrayOf(1) },
            "absolute path" to { it["/evil"]=byteArrayOf(1) },
            "drive path" to { it["C:/evil"]=byteArrayOf(1) },
            "partial directory prefix" to { it["attach/"]=byteArrayOf() },
            "root file as directory" to { it["manifest.json/"]=byteArrayOf() },
            "duplicate root case" to { it["MANIFEST.JSON"]=it.getValue("manifest.json") },
            "unknown version" to { map -> val m=StrictJson.parse(map.getValue("manifest.json").toString(Charsets.UTF_8)).jsonObject().toMutableMap();m["formatVersion"]=99;map["manifest.json"]=StrictJson.stringify(m).toByteArray() },
            "duplicate attachment UUID" to { map ->
                val m=StrictJson.parse(map.getValue("manifest.json").toString(Charsets.UTF_8)).jsonObject().toMutableMap()
                m["attachments"]=m.array("attachments")+m.array("attachments");map["manifest.json"]=StrictJson.stringify(m).toByteArray()
            },
            "wrong size" to { map ->
                val m=StrictJson.parse(map.getValue("manifest.json").toString(Charsets.UTF_8)).jsonObject().toMutableMap()
                m["attachments"]=m.array("attachments").map { a->a.jsonObject()+mapOf("sizeBytes" to 999L) }
                map["manifest.json"]=StrictJson.stringify(m).toByteArray()
            },
            "wrong hash" to { map ->
                val m=StrictJson.parse(map.getValue("manifest.json").toString(Charsets.UTF_8)).jsonObject().toMutableMap()
                m["attachments"]=m.array("attachments").map { a->a.jsonObject()+mapOf("sha256" to "0".repeat(64)) }
                map["manifest.json"]=StrictJson.stringify(m).toByteArray()
            }
        )
        attacks.forEachIndexed { i,(label,attack) ->
            val bad=tmp.newFile("bad$i.geodata");mutate(source,bad,attack)
            assertThrows(label,Exception::class.java) { bad.inputStream().use { DataArchive.read(it,tmp.root).close() } }
        }
    }
    @Test fun uuidConflictsAndCurrentHistoricalIndependentLimits() {
        val entity=tx(); val snapshot=TransactionSnapshot(entity,emptyList())
        assertThrows(RuntimeException::class.java) { DataArchive.validateDataset(ArchiveDataset(listOf(snapshot,snapshot),emptyList())) }
        val event=AuditEventEntity(UUID.randomUUID().toString(),entity.transactionUuid,"DELETE","USER",2,beforeSnapshotJson=snapshot.encode(),afterSnapshotJson=null)
        assertThrows(RuntimeException::class.java) { DataArchive.validateDataset(ArchiveDataset(listOf(snapshot),listOf(event,event))) }
        assertThrows(RuntimeException::class.java) { TransactionSnapshot.parse(snapshot.json()+mapOf("amountCents" to 0L)) }
        assertThrows(RuntimeException::class.java) { TransactionSnapshot.parse(snapshot.json()+mapOf("transactionDate" to Long.MAX_VALUE)) }
    }
    @Test fun compressibleAttachmentOwnExportRoundTripsAndFolderNamesAreSafe() {
        val zero=tmp.newFile().apply { outputStream().use { it.write(ByteArray(10*1024*1024)) } }
        val entity=tx().copy(incomeSource="中文📎\\/:*?\"<>|"+"文".repeat(120))
        val safe=entity.copy(incomeSource=entity.incomeSource!!.take(120))
        val ref=AttachmentRef(UUID.randomUUID().toString(),UUID.randomUUID().toString(),"CON.pdf","application/pdf",zero.length(),zero.inputStream().use { AttachmentPolicy.copy(it,object:java.io.OutputStream(){override fun write(b:Int){};override fun write(b:ByteArray,off:Int,len:Int){}}).sha256 },0)
        val source=tmp.newFile()
        DataArchive.write(ArchiveDataset(listOf(TransactionSnapshot(safe,listOf(ref))),emptyList()),source,{zero},"1.2.0",6)
        source.inputStream().use { DataArchive.read(it,tmp.root).use { pack->assertEquals(zero.length(),pack.blobFiles.getValue(ref.blobUuid).length()) } }
        val folder=DataArchive.folder(safe);assertTrue(folder.endsWith(safe.transactionUuid.take(8)))
        assertFalse(folder.any { it in "\\/:*?\"<>|" })
        assertTrue(folder.length<=100)
    }
}
