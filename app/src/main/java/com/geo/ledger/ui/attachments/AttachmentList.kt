package com.geo.ledger.ui.attachments

import android.content.Intent
import android.content.ClipData
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.geo.ledger.GeoApplication
import com.geo.ledger.data.transfer.*
import kotlinx.coroutines.*
import androidx.compose.ui.window.Dialog

val LocalAttachmentCounts=staticCompositionLocalOf<Map<String,Int>> { emptyMap() }

@Composable fun TransactionAttachmentBadge(uuid: String) = AttachmentBadge(LocalAttachmentCounts.current[uuid] ?: 0)

@Composable fun DetailAttachments(transactionId: Long) {
    val repo=(LocalContext.current.applicationContext as GeoApplication).repository
    val refs by produceState<List<AttachmentRef>>(emptyList(),transactionId) {
        val transaction=repo.getTransaction(transactionId)
        if(transaction!=null) repo.observeAttachments(transaction.transactionUuid).collect { value=it.map(AttachmentRef::from) }
    }
    if(refs.isNotEmpty()) {
        Text("附件 · ${refs.size}",style=MaterialTheme.typography.titleSmall)
        AttachmentList(refs)
    }
}

@Composable
fun AttachmentBadge(count: Int) {
    if(count<=0) return
    Row(Modifier.padding(top=4.dp).semantics(mergeDescendants=true) { contentDescription="有${count}个附件" },horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Default.AttachFile,null,Modifier.size(16.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        if(count>1) Text(count.toString(),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AttachmentList(refs: List<AttachmentRef>, keys: Map<String,String> = emptyMap(),
    enabled: Boolean = true, onRemove: ((String)->Unit)? = null) {
    val context=LocalContext.current
    val repo=(context.applicationContext as GeoApplication).repository
    val scope=rememberCoroutineScope()
    var pendingSave by rememberSaveable { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    suspend fun key(ref: AttachmentRef): String = keys[ref.attachmentUuid] ?: requireNotNull(repo.historyDao.blob(ref.blobUuid)) { "附件不存在" }.internalStorageKey
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val ref=pendingSave?.let { AttachmentRef.parse(StrictJson.parse(it)) }; pendingSave=null
        if(uri!=null && ref!=null) {
            busy=true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val storageKey=key(ref); val store=requireNotNull(repo.blobStore)
                        store.verify(storageKey,ref.sizeBytes,ref.sha256)
                        requireNotNull(context.contentResolver.openOutputStream(uri,"wt")) { "无法写入目标文件" }.use { store.copyTo(storageKey,it) }
                    }
                    message="已保存到设备"
                } catch(e: Exception) { if(e is CancellationException) throw e; message="保存失败：${e.message ?: "无法写入"}" }
                finally { busy=false }
            }
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        refs.forEach { ref ->
            Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AttachFile,null,Modifier.size(20.dp))
                        Text(ref.originalFileName,Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
                    }
                    Text("${ref.mimeType} · ${sizeLabel(ref.sizeBytes)}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                        TextButton(enabled=enabled && !busy,onClick={
                            busy=true
                            scope.launch {
                                try {
                                    val file=withContext(Dispatchers.IO) {
                                        val storageKey=key(ref); val store=requireNotNull(repo.blobStore)
                                        store.verify(storageKey,ref.sizeBytes,ref.sha256); store.file(storageKey)
                                    }
                                    val decoded=if(ref.mimeType in setOf("image/jpeg","image/png","image/webp")) withContext(Dispatchers.IO) {
                                        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                                        BitmapFactory.decodeFile(file.path,bounds)
                                        var sample=1
                                        while(bounds.outWidth/sample>2048 || bounds.outHeight/sample>2048 ||
                                            (bounds.outWidth.toLong()/sample)*(bounds.outHeight/sample)>4_000_000) sample*=2
                                        BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply { inSampleSize=sample })
                                    } else null
                                    if(decoded!=null) bitmap=decoded else {
                                        val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
                                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,ref.mimeType)
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData=ClipData.newRawUri(ref.originalFileName,uri) })
                                    }
                                } catch(e: android.content.ActivityNotFoundException) { message="没有可打开此附件的应用，可先保存到设备" }
                                catch(e: Exception) { if(e is CancellationException) throw e; message="附件打开失败：${e.message}" }
                                finally { busy=false }
                            }
                        }) { Text("查看") }
                        TextButton(enabled=enabled && !busy,onClick={ pendingSave=StrictJson.stringify(ref.json()); save.launch(com.geo.ledger.data.attachments.AttachmentPolicy.safeName(ref.originalFileName,120)) }) { Text("保存到设备") }
                        if(onRemove!=null) TextButton(enabled=enabled && !busy,onClick={ onRemove(ref.attachmentUuid) }) { Text("移除") }
                    }
                }
            }
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    message?.let { text -> AlertDialog(onDismissRequest={message=null},title={Text("附件")},text={Text(text)},confirmButton={TextButton(onClick={message=null}){Text("知道了")}}) }
    bitmap?.let { image ->
        Dialog(onDismissRequest={bitmap=null}) {
            Surface(shape=MaterialTheme.shapes.large) {
                Column(Modifier.padding(12.dp)) {
                    Image(image.asImageBitmap(),"附件预览",Modifier.fillMaxWidth().heightIn(max=520.dp))
                    TextButton(onClick={bitmap=null}) { Text("关闭") }
                }
            }
        }
    }
}

fun sizeLabel(bytes: Long): String = if(bytes>=1024*1024) "${bytes*10/(1024*1024)/10}.${bytes*10/(1024*1024)%10} MiB"
    else if(bytes>=1024) "${bytes/1024} KiB" else "$bytes B"
