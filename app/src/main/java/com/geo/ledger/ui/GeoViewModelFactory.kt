package com.geo.ledger.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.geo.ledger.data.LedgerRepository
import com.geo.ledger.ui.addtransaction.AddTransactionViewModel
import com.geo.ledger.ui.bills.BillsViewModel
import com.geo.ledger.ui.category.ManageCategoriesViewModel
import com.geo.ledger.ui.home.HomeViewModel
import com.geo.ledger.ui.person.ManagePersonsViewModel
import com.geo.ledger.ui.transactiondetail.TransactionDetailViewModel

class GeoViewModelFactory(
    private val repository: LedgerRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val handle = extras.createSavedStateHandle()
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                HomeViewModel(repository) as T
            modelClass.isAssignableFrom(BillsViewModel::class.java) ->
                BillsViewModel(repository, handle) as T
            modelClass.isAssignableFrom(AddTransactionViewModel::class.java) ->
                AddTransactionViewModel(repository, handle) as T
            modelClass.isAssignableFrom(TransactionDetailViewModel::class.java) ->
                TransactionDetailViewModel(repository, handle) as T
            modelClass.isAssignableFrom(ManagePersonsViewModel::class.java) ->
                ManagePersonsViewModel(repository) as T
            modelClass.isAssignableFrom(ManageCategoriesViewModel::class.java) ->
                ManageCategoriesViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel ${modelClass.name}")
        }
    }
}
