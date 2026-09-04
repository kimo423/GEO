package com.geo.ledger.ui.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geo.ledger.data.LedgerRepository
import com.geo.ledger.data.local.PersonOptionEntity
import com.geo.ledger.ui.options.OptionMutationController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ManagePersonsViewModel(
    repository: LedgerRepository,
) : ViewModel() {
    val activePersons: StateFlow<List<PersonOptionEntity>> = repository.activePersons.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val mutations = OptionMutationController(
        scope = viewModelScope,
        currentActive = { activePersons.value.map { it.id to it.name } },
        addOption = repository::addPerson,
        renameOption = repository::renamePerson,
        deactivateOption = repository::deactivatePerson,
    )

    val isBusy = mutations.isBusy
    val events = mutations.events

    fun add(name: String) = mutations.add(name)

    fun rename(id: Long, name: String) = mutations.rename(id, name)

    fun delete(id: Long) = mutations.delete(id)
}
