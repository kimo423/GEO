package com.geo.ledger.data.transfer

import androidx.room.withTransaction
import com.geo.ledger.data.LedgerRepository
import com.geo.ledger.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import java.text.Normalizer

enum class ImportMode { FULL_REPLACE, MATCHING_ONLY }

fun normalizedOptionName(raw: String): String = Normalizer.normalize(raw.trim(), Normalizer.Form.NFC)
    .map { if (it in 'A'..'Z') it.lowercaseChar() else it }.joinToString("")

data class ConfigOption(val name: String, val sortOrder: Int, val isActive: Boolean, val id: Long? = null) {
    fun json() = linkedMapOf("id" to id,"name" to name,"sortOrder" to sortOrder,"isActive" to isActive)
}
data class ConfigPackage(val exportedAt: String, val persons: List<ConfigOption>, val categories: List<ConfigOption>) {
    fun encode(versionName: String, versionCode: Int) = StrictJson.stringify(linkedMapOf(
        "format" to "GEO_CONFIG", "formatVersion" to 1, "exportedAt" to exportedAt,
        "appVersionName" to versionName, "appVersionCode" to versionCode,
        "persons" to persons.map { it.json() }, "expenseCategories" to categories.map { it.json() }))
    companion object {
        fun parse(raw: String): ConfigPackage {
            require(raw.length <= 2*1024*1024) { "配置文件过大" }
            val o = StrictJson.parse(raw).jsonObject()
            require(o.string("format") == "GEO_CONFIG" && o.long("formatVersion") == 1L) { "不支持的 GEO 配置格式" }
            fun options(key: String): List<ConfigOption> {
                val values = o.array(key); require(values.size <= 10000) { "配置项目过多" }
                val result = values.map { value ->
                    val item = value.jsonObject(); val name = item.string("name").trim()
                    require(name.isNotBlank() && name.length <= 40) { "配置名称为空或超过 40 字" }
                    val sort = item.long("sortOrder"); require(sort in 0..Int.MAX_VALUE)
                    ConfigOption(name,sort.toInt(),item.bool("isActive"),item.nullableLong("id"))
                }
                require(result.map { normalizedOptionName(it.name) }.distinct().size == result.size) { "配置包含重复的同名项目" }
                return result
            }
            return ConfigPackage(o.string("exportedAt"),options("persons"),options("expenseCategories"))
        }
    }
}

class ConfigTransfer(private val repo: LedgerRepository) {
    suspend fun export(): ConfigPackage = withContext(Dispatchers.IO) { repo.mutationMutex.withLock {
        repo.database.withTransaction {
            ConfigPackage(java.time.Instant.now().toString(),
                repo.database.personOptionDao().getAll().filter { it.isActive }.map { ConfigOption(it.name,it.sortOrder,it.isActive,it.id) },
                repo.database.expenseCategoryDao().getAll().filter { it.isActive }.map { ConfigOption(it.name,it.sortOrder,it.isActive,it.id) })
        }
    } }
    suspend fun apply(pack: ConfigPackage, mode: ImportMode) = withContext(Dispatchers.IO) { repo.mutationMutex.withLock {
        // Re-validate even if a caller bypasses file parsing.
        ConfigPackage.parse(pack.encode("1.2.0",6))
        repo.database.withTransaction {
            val personDao = repo.database.personOptionDao(); val categoryDao = repo.database.expenseCategoryDao()
            val persons = personDao.getAll().filter { it.isActive }; val categories = categoryDao.getAll().filter { it.isActive }
            fun <T> unique(rows: List<T>, name: (T)->String): Map<String,T> {
                val groups = rows.groupBy { normalizedOptionName(name(it)) }
                require(groups.values.all { it.size==1 }) { "本机有大小写或 Unicode 同名项目，请先重命名消除歧义" }
                return groups.mapValues { it.value.single() }
            }
            val personMap = unique(persons) { it.name }; val categoryMap = unique(categories) { it.name }
            val now = System.currentTimeMillis()
            if (mode == ImportMode.FULL_REPLACE) {
                persons.forEach { personDao.update(it.copy(isActive=false,activeNameKey=null,updatedAtMillis=now)) }
                categories.forEach { categoryDao.update(it.copy(isActive=false,activeNameKey=null,updatedAtMillis=now)) }
            }
            pack.persons.forEach { item ->
                val old = personMap[normalizedOptionName(item.name)]
                if (old != null) personDao.update(old.copy(name=item.name,sortOrder=item.sortOrder,isActive=item.isActive,
                    activeNameKey=activeOptionNameKey(item.isActive,item.name),updatedAtMillis=now))
                else if (mode==ImportMode.FULL_REPLACE) personDao.insert(PersonOptionEntity(name=item.name,isActive=item.isActive,
                    activeNameKey=activeOptionNameKey(item.isActive,item.name),sortOrder=item.sortOrder,createdAtMillis=now,updatedAtMillis=now))
            }
            pack.categories.forEach { item ->
                val old = categoryMap[normalizedOptionName(item.name)]
                if (old != null) categoryDao.update(old.copy(name=item.name,sortOrder=item.sortOrder,isActive=item.isActive,
                    activeNameKey=activeOptionNameKey(item.isActive,item.name),updatedAtMillis=now))
                else if (mode==ImportMode.FULL_REPLACE) categoryDao.insert(ExpenseCategoryEntity(name=item.name,isActive=item.isActive,
                    activeNameKey=activeOptionNameKey(item.isActive,item.name),sortOrder=item.sortOrder,createdAtMillis=now,updatedAtMillis=now))
            }
        }
    } }
}
