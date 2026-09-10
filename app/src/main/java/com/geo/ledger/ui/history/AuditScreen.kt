package com.geo.ledger.ui.history
import com.geo.ledger.data.local.peopleLabel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geo.ledger.GeoApplication
import com.geo.ledger.data.local.AuditEventEntity
import com.geo.ledger.data.transfer.TransactionSnapshot
import com.geo.ledger.ui.attachments.AttachmentList
import com.geo.ledger.ui.theme.*
import com.geo.ledger.util.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun AuditScreen() {
    val repo=(LocalContext.current.applicationContext as GeoApplication).repository
    val events by repo.auditHistory.collectAsStateWithLifecycle(emptyList())
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {
            Text("保留每次变更的前后内容与历史附件",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(events.isEmpty()) item { Text("暂无修改记录\n编辑或删除账单后，将在这里保留历史。",Modifier.padding(vertical=32.dp)) }
        items(events,key={it.eventUuid}) { event -> AuditCard(event) }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun AuditCard(event: AuditEventEntity) {
    val before=remember(event.beforeSnapshotJson) { runCatching { TransactionSnapshot.decode(event.beforeSnapshotJson) }.getOrNull() }
    val after=remember(event.afterSnapshotJson) { event.afterSnapshotJson?.let { runCatching { TransactionSnapshot.decode(it) }.getOrNull() } }
    var expanded by remember(event.eventUuid) { mutableStateOf(false) }
    Card(colors=CardDefaults.cardColors(containerColor=GeoCard),shape=MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text((if(event.eventType=="DELETE") "删除账单" else "修改账单") + if(event.source=="IMPORT") " · 导入替换" else "",style=MaterialTheme.typography.titleMedium)
            Text(remember(event.occurredAtMillis) { DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").format(Instant.ofEpochMilli(event.occurredAtMillis).atZone(ZoneId.systemDefault())) },style=MaterialTheme.typography.labelSmall)
            if(before==null) Text("历史内容无法读取，请保留备份并反馈") else {
                if(after!=null) FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    before.changes(after).forEach { label ->
                        Surface(shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.secondaryContainer) {
                            Text(label,Modifier.padding(horizontal=8.dp,vertical=4.dp),style=MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if(after!=null && before.attachments!=after.attachments) {
                    val oldIds=before.attachments.map { it.attachmentUuid }.toSet()
                    val newIds=after.attachments.map { it.attachmentUuid }.toSet()
                    val added=after.attachments.filter { it.attachmentUuid !in oldIds }.map { it.originalFileName }
                    val removed=before.attachments.filter { it.attachmentUuid !in newIds }.map { it.originalFileName }
                    Text("附件：新增 ${added.size} · 移除 ${removed.size} · 保留 ${oldIds.intersect(newIds).size}",style=MaterialTheme.typography.bodySmall)
                    if(expanded) {
                        if(added.isNotEmpty()) Text("新增：${added.joinToString()}",style=MaterialTheme.typography.bodySmall,color=GeoIncome)
                        if(removed.isNotEmpty()) Text("移除：${removed.joinToString()}",style=MaterialTheme.typography.bodySmall,color=GeoExpense)
                    }
                }
                SnapshotBlock(if(event.eventType=="DELETE") "删除前" else "修改前",before,expanded)
                if(after!=null) {
                    Text("↓",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary)
                    SnapshotBlock("修改后",after,expanded)
                } else Text("该账单已删除",color=GeoExpense,style=MaterialTheme.typography.labelMedium)
                TextButton(onClick={expanded=!expanded}) { Text(if(expanded) "收起详细信息与附件" else "展开详细信息与附件") }
            }
        }
    }
}
@Composable private fun SnapshotBlock(label: String,snapshot: TransactionSnapshot,expanded: Boolean) {
    val tx=snapshot.transaction
    Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,shape=MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(label,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
            Text(MoneyFormatter.transaction(tx.type,tx.amountCents),style=MaterialTheme.typography.titleLarge,
                color=if(tx.type==com.geo.ledger.data.local.TransactionType.INCOME) GeoIncome else GeoExpense)
            Text(GeoDates.formatRecordedAt(tx.transactionDate,tx.createdAtMillis),style=MaterialTheme.typography.bodySmall)
            Text(listOfNotNull(tx.peopleLabel(),tx.expenseCategorySnapshot,tx.incomeSource).joinToString(" · ").ifBlank { "未指定使用人、分类或来源" },style=MaterialTheme.typography.bodySmall)
            tx.note?.let { Text(it,style=MaterialTheme.typography.bodySmall,maxLines=if(expanded) Int.MAX_VALUE else 2) }
            if(tx.isDeleted) Text("已删除状态",color=GeoExpense)
            if(snapshot.attachments.isNotEmpty()) Text("附件 ${snapshot.attachments.size} 个",style=MaterialTheme.typography.labelSmall)
            if(expanded) AttachmentList(snapshot.attachments)
        }
    }
}
