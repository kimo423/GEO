package com.geo.ledger.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.*
import com.geo.ledger.BuildConfig
import com.geo.ledger.data.transfer.ImportMode
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.geo.ledger.R

@Composable
fun SettingsScreen(
    onOpenPersons: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenHistory: () -> Unit = {},
    onNavigationLock: (Boolean)->Unit = {},
    tools: SettingsToolsViewModel = viewModel(),
) {
    val busy by tools.busy.collectAsStateWithLifecycle()
    val message by tools.message.collectAsStateWithLifecycle()
    val config by tools.config.collectAsStateWithLifecycle()
    val archive by tools.archive.collectAsStateWithLifecycle()
    val release by tools.release.collectAsStateWithLifecycle()
    val selectedName by tools.selectedName.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var confirmFull by rememberSaveable { mutableStateOf(false) }
    val exportConfig=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { uri->tools.export(uri,true) } }
    val exportData=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { it?.let { uri->tools.export(uri,false) } }
    val importConfig=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri->tools.inspect(uri,true) } }
    val importData=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri->tools.inspect(uri,false) } }
    fun stamp()=java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
    BackHandler(enabled=busy!=null) {}
    DisposableEffect(busy) { onNavigationLock(busy!=null);onDispose { onNavigationLock(false) } }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = com.geo.ledger.ui.theme.GeoSpacing.Page, vertical = com.geo.ledger.ui.theme.GeoSpacing.Section),
    ) {
        if (com.geo.ledger.ui.theme.isGraphite) com.geo.ledger.ui.theme.GraphiteHeading("设置", "个性外观 / PREFERENCES") else Text(
            text = stringResource(R.string.settings),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(24.dp))
        com.geo.ledger.ui.theme.SkinSelector()
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.ledger_settings),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        SettingsEntry(
            title = stringResource(R.string.manage_persons),
            subtitle = stringResource(R.string.manage_persons_subtitle),
            onClick = onOpenPersons,
        )
        if (!com.geo.ledger.ui.theme.isGraphite) HorizontalDivider()
        SettingsEntry(
            title = stringResource(R.string.manage_categories),
            subtitle = stringResource(R.string.manage_categories_subtitle),
            onClick = onOpenCategories,
        )
        Spacer(Modifier.height(24.dp))
        Text("数据管理",style=MaterialTheme.typography.titleMedium)
        SettingsEntry("修改与删除记录","查看每次修改前后内容和历史附件",onOpenHistory)
        SettingsEntry("导出配置","使用人和支出分类 · .geocfg",{ if(busy==null) exportConfig.launch("GEO-config-${stamp()}.geocfg") })
        SettingsEntry("导入配置","完全替换或仅替换本机同名项目",{ if(busy==null) importConfig.launch(arrayOf("*/*")) })
        SettingsEntry("导出账单数据","完整账本、附件和修改记录 · .geodata",{ if(busy==null) exportData.launch("GEO-data-${stamp()}.geodata") })
        SettingsEntry("导入账单数据","先校验数据包，再确认替换范围",{ if(busy==null) importData.launch(arrayOf("*/*")) })
        Spacer(Modifier.height(24.dp))
        Text("关于 GEO",style=MaterialTheme.typography.titleMedium)
        Text("当前版本 ${BuildConfig.VERSION_NAME}",Modifier.padding(vertical=12.dp),style=MaterialTheme.typography.bodyMedium)
        SettingsEntry("检查更新","仅在你点击时连接 GitHub",{tools.checkUpdate()})
        Text("账本与附件保存在本机。请定期导出备份。",Modifier.padding(vertical=16.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if(config!=null || archive!=null) AlertDialog(
        onDismissRequest={if(busy==null) tools.clearPreview()},title={Text(if(config!=null) "GEO 配置文件" else "GEO 数据包")},
        text={ Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(selectedName,maxLines=2)
            config?.let { Text("导出时间：${it.exportedAt}\n使用人：${it.persons.size}\n分类：${it.categories.size}") }
            archive?.let { pack ->
                val active=pack.dataset.transactions.sumOf { it.attachments.size }
                Text("导出时间：${pack.exportedAt}\n账单：${pack.dataset.transactions.size}\n当前附件：$active\n历史附件：${pack.attachments.size-active}\n修改记录：${pack.dataset.events.size}")
            }
            Text("\n完全替换：${if(config!=null) "当前使用人和分类以文件为准。" else "当前账单、附件和修改记录全部以数据包为准。"}")
            Text("\n${if(config!=null) "同名替换：只替换本机已有同名项目，不新增其他项目。" else "同记录替换：只替换本机已存在且 UUID 相同的账单，新账单不会加入。"}")
        }},confirmButton={Column {
            TextButton(enabled=busy==null,onClick={tools.applyImport(ImportMode.MATCHING_ONLY)}) {Text(if(config!=null) "同名替换" else "同记录替换")}
            TextButton(enabled=busy==null,onClick={confirmFull=true}) {Text("完全替换")}
        }},dismissButton={TextButton(enabled=busy==null,onClick={tools.clearPreview()}) {Text("取消")}})
    if(confirmFull && (config!=null || archive!=null)) AlertDialog(onDismissRequest={confirmFull=false},title={Text("确认完全替换？")},
        text={Text(if(config!=null) "当前使用人和分类将全部以导入文件为准。历史账单快照不变。" else "这将使用导入的数据替换当前 GEO 中的账单、附件和修改记录。本机当前账单数据将不再保留。")},
        confirmButton={TextButton(enabled=busy==null,onClick={confirmFull=false;tools.applyImport(ImportMode.FULL_REPLACE)}){Text("继续替换")}},
        dismissButton={TextButton(onClick={confirmFull=false}){Text("取消")}})
    busy?.let { text-> AlertDialog(onDismissRequest={},title={Text(text)},text={LinearProgressIndicator(Modifier.fillMaxWidth())},confirmButton={}) }
    message?.let { text-> AlertDialog(onDismissRequest={tools.message.value=null},title={Text("GEO")},text={Text(text)},confirmButton={TextButton(onClick={tools.message.value=null}){Text("知道了")}}) }
    release?.let { r -> AlertDialog(onDismissRequest={tools.release.value=null},title={Text("发现新版本 · GEO ${r.version}")},
        text={Text("${r.name}\n发布日期：${r.publishedAt}\n\n${r.notes.ifBlank { "暂无更新说明" }}",Modifier.heightIn(max=350.dp).verticalScroll(rememberScrollState()))},
        confirmButton={TextButton(onClick={
            try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(r.downloadUrl))) }
            catch(e: Exception) { tools.message.value="无法打开浏览器，请稍后重试" }
            tools.release.value=null
        }){Text("前往下载")}},dismissButton={TextButton(onClick={tools.release.value=null}){Text("稍后")}}) }
}

@Composable
private fun SettingsEntry(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
            )
        },
        colors = ListItemDefaults.colors(containerColor = if (com.geo.ledger.ui.theme.isGraphite) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.background),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (com.geo.ledger.ui.theme.isGraphite) Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)) else Modifier)
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick),
    )
}
