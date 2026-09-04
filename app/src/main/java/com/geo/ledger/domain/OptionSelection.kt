package com.geo.ledger.domain

data class NamedOption(
    val id: Long,
    val name: String,
    val isActive: Boolean,
)

data class OptionChipModel(
    val id: Long,
    val label: String,
    val historical: Boolean,
    val selected: Boolean,
)

object OptionSnapshotPolicy {
    fun snapshotForSave(
        selectedId: Long?,
        existingId: Long?,
        existingSnapshot: String?,
        selectionEdited: Boolean,
        currentOption: NamedOption?,
        missingMessage: String,
        inactiveMessage: String,
    ): String? {
        if (selectedId == null) return null
        if (!selectionEdited && existingId == selectedId) return existingSnapshot
        val option = requireNotNull(currentOption) { missingMessage }
        require(option.isActive) { inactiveMessage }
        return option.name
    }

    fun chips(
        active: List<Pair<Long, String>>,
        selectedId: Long?,
        historicalSnapshot: String?,
    ): List<OptionChipModel> {
        if (selectedId == null) {
            return active.map { (id, name) ->
                OptionChipModel(id = id, label = name, historical = false, selected = false)
            }
        }
        val matchingActive = active.firstOrNull { it.first == selectedId }
        val snapshot = historicalSnapshot?.takeIf { it.isNotBlank() }
        val showHistorical = snapshot != null && (matchingActive == null || matchingActive.second != snapshot)
        val chips = mutableListOf<OptionChipModel>()
        if (showHistorical) {
            chips += OptionChipModel(
                id = selectedId,
                label = snapshot!!,
                historical = true,
                selected = true,
            )
        }
        active.forEach { (id, name) ->
            chips += OptionChipModel(
                id = id,
                label = name,
                historical = false,
                selected = !showHistorical && id == selectedId,
            )
        }
        return chips
    }
}
