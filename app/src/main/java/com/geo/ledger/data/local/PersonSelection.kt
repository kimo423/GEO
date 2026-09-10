package com.geo.ledger.data.local

import com.geo.ledger.data.transfer.*
import com.geo.ledger.domain.OptionChipModel

/** Each name is an immutable transaction-time snapshot; IDs are local option links only. */
data class PersonSelection(val id: Long?, val name: String) {
    fun json(): Map<String, Any?> = linkedMapOf("id" to id, "name" to name)
}

object PersonSelections {
    fun encode(people: List<PersonSelection>): String = StrictJson.stringify(people.map { it.json() })
    fun decode(raw: String): List<PersonSelection> = parse(StrictJson.parse(raw))
    fun parse(value: Any?): List<PersonSelection> {
        val rows = value as? List<*> ?: error("使用人名单格式无效")
        return rows.map {
            val o = it.jsonObject()
            PersonSelection(o.nullableLong("id"), o.string("name"))
        }.also(::validate)
    }
    fun validate(people: List<PersonSelection>) {
        people.forEach {
            require(it.id == null || it.id > 0) { "使用人 ID 无效" }
            require(it.name.isNotBlank() && it.name.length <= 40) { "使用人姓名无效" }
            StrictJson.stringify(it.name)
        }
        require(people.mapNotNull { it.id }.distinct().size == people.count { it.id != null }) { "使用人重复" }
        // Detached historical people can share a name; never collapse them during import.
    }
    fun chips(people: List<PersonSelection>, active: List<PersonOptionEntity>): List<OptionChipModel> {
        val names = active.associate { it.id to it.name }
        val selected = people.associateBy { it.id }
        return people.mapIndexedNotNull { index, person ->
            if (person.id == null || names[person.id] != person.name)
                OptionChipModel(person.id ?: -(index + 2L), person.name, true, true) else null
        } + active.map { OptionChipModel(it.id, it.name, false, selected[it.id]?.name == it.name) }
    }
}

fun TransactionEntity.selectedPeople(): List<PersonSelection> = expensePeopleJson?.let(PersonSelections::decode)
    ?: expensePersonSnapshot?.takeIf { it.isNotBlank() }?.let { listOf(PersonSelection(expensePersonId, it)) }.orEmpty()

fun TransactionEntity.peopleLabel(): String? = selectedPeople().joinToString("、") { it.name }.ifBlank { null }
