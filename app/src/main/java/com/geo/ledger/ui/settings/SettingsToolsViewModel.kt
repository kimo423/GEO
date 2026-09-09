package com.geo.ledger.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geo.ledger.BuildConfig
import com.geo.ledger.GeoApplication
import com.geo.ledger.data.transfer.*
import com.geo.ledger.data.attachments.AttachmentPolicy
import com.geo.ledger.update.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class SettingsToolsViewModel(application: Application): AndroidViewModel(application) {
    private val context=application
    private val repo=(application as GeoApplication).repository
    val busy=MutableStateFlow<String?>(null)
    val message=MutableStateFlow<String?>(null)
    val config=MutableStateFlow<ConfigPackage?>(null)
    val archive=MutableStateFlow<ValidatedArchive?>(null)
    val release=MutableStateFlow<GithubRelease?>(null)
    val selectedName=MutableStateFlow("")
    private val staging=File(context.filesDir,"staging").apply { mkdirs() }
    private fun operation(label: String, block: suspend ()->Unit) {
        if(busy.value!=null) return
        busy.value=label
        viewModelScope.launch {
            try { block() }
            catch(e: Exception) { if(e is CancellationException) throw e; message.value=e.message ?: "操作失败，请重试" }
            finally { busy.value=null }
        }
    }
    fun clearPreview() {
        if(busy.value!=null) return
        config.value=null
        val old=archive.value; archive.value=null
        viewModelScope.launch(Dispatchers.IO) { old?.close() }
    }
    fun inspect(uri: Uri, isConfig: Boolean) = operation("正在校验文件…") {
        var pending: ValidatedArchive?=null
        try {
        withContext(Dispatchers.IO) {
            val parsedName=context.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
                if(it.moveToFirst()) it.getString(0) else "GEO 数据包"
            } ?: "GEO 数据包"
            requireNotNull(context.contentResolver.openInputStream(uri)) { "无法读取文件" }.use { input ->
                if(isConfig) {
                    val bytes=ByteArrayOutputStream().use { AttachmentPolicy.copy(input,it,2L*1024*1024); it.toByteArray() }
                    val parsed=ConfigPackage.parse(StrictJson.decodeUtf8(bytes))
                    withContext(Dispatchers.Main) {
                        val old=archive.value; archive.value=null; config.value=parsed; selectedName.value=parsedName
                        withContext(NonCancellable+Dispatchers.IO) { old?.close() }
                    }
                } else {
                    pending=DataArchive.read(input,staging)
                    withContext(Dispatchers.Main) {
                        val old=archive.value; archive.value=pending; pending=null; config.value=null; selectedName.value=parsedName
                        withContext(NonCancellable+Dispatchers.IO) { old?.close() }
                    }
                }
            }
        }
        } finally { withContext(NonCancellable+Dispatchers.IO) { pending?.close() } }
    }
    fun applyImport(mode: ImportMode) = operation("正在应用导入，请勿退出…") {
        val cfg=config.value; val pack=archive.value
        if(cfg!=null) { ConfigTransfer(repo).apply(cfg,mode); config.value=null; message.value="配置导入成功" }
        else if(pack!=null) {
            val count=DataTransfer(repo).apply(pack,mode)
            archive.value=null
            withContext(NonCancellable+Dispatchers.IO) { pack.close() }
            message.value="数据导入成功，已处理 $count 笔账单"
        }
    }
    fun export(uri: Uri,isConfig: Boolean) = operation("正在导出，请稍候…") {
        withContext(Dispatchers.IO) {
            val file=File(staging,"export-${UUID.randomUUID()}")
            try {
                if(isConfig) {
                    val json=ConfigTransfer(repo).export().encode(BuildConfig.VERSION_NAME,BuildConfig.VERSION_CODE)
                    FileOutputStream(file).use { it.write(json.toByteArray(Charsets.UTF_8));it.fd.sync() }
                } else DataTransfer(repo).export(file,BuildConfig.VERSION_NAME,BuildConfig.VERSION_CODE)
                try {
                    requireNotNull(context.contentResolver.openOutputStream(uri,"wt")) { "无法写入导出文件" }.use { output -> file.inputStream().use { it.copyTo(output,64*1024) } }
                } catch(e: Exception) {
                    runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver,uri) }
                    throw e
                }
            } finally { file.delete() }
        }
        message.value="导出成功，请妥善保存备份文件"
    }
    fun checkUpdate() = operation("正在检查更新…") {
        val found=withContext(Dispatchers.IO) {
            val connection=URL(GithubReleasePolicy.API).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout=8000;connection.readTimeout=8000
                connection.instanceFollowRedirects=false;connection.useCaches=false
                connection.setRequestProperty("Accept","application/vnd.github+json")
                connection.setRequestProperty("User-Agent","GEO/${BuildConfig.VERSION_NAME}")
                val status=connection.responseCode
                require(status==200) { when(status) {
                    403,429 -> "GitHub 请求额度暂时受限，请稍后重试"
                    404 -> "暂未找到正式发布版本"
                    else -> "检查更新失败（HTTP $status）"
                } }
                val body=connection.inputStream.use { input -> ByteArrayOutputStream().use { output ->
                    AttachmentPolicy.copy(input,output,GithubReleasePolicy.MAX_RESPONSE.toLong());output.toString("UTF-8")
                } }
                GithubReleasePolicy.parse(body)
            } catch(e: java.net.SocketTimeoutException) { throw IOException("检查更新超时，请稍后重试",e) }
            catch(e: java.net.UnknownHostException) { throw IOException("无法连接 GitHub，请检查网络",e) }
            catch(e: java.net.ConnectException) { throw IOException("无法连接 GitHub，请检查网络",e) }
            catch(e: javax.net.ssl.SSLException) { throw IOException("无法建立安全连接，请检查网络和设备时间",e) }
            finally { connection.disconnect() }
        }
        if(found.version>requireNotNull(ReleaseVersion.parse(BuildConfig.VERSION_NAME))) release.value=found
        else message.value="当前已是最新版本\nGEO ${BuildConfig.VERSION_NAME}"
    }
    override fun onCleared() {
        val old=archive.value
        CoroutineScope(Dispatchers.IO).launch { old?.close() }
        super.onCleared()
    }
}
