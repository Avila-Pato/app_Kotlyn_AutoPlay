package org.example.autoplay.mycar

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.example.autoplay.DeviceRole
import org.example.autoplay.DeviceRoles
import org.example.autoplay.car.CarServer
import java.time.LocalDate
import javax.inject.Inject

/**
 * One view model for both sides: on the phone it edits the driver's garage, on the radio it edits the car the
 * phone sent (and the change goes back to the phone). The screen does not know the difference.
 */
@HiltViewModel
class MyCarViewModel @Inject constructor(
    store: MyCarStore,
    server: CarServer,
    val images: DocumentImages,
    @ApplicationContext context: Context,
) : ViewModel() {
    private val data: MyCarData = if (DeviceRoles.resolve(context) == DeviceRole.Car) CarSideMyCarData(server) else store

    /** Null on the radio while no phone has sent its car. */
    val garage: StateFlow<Garage?> = data.garage.stateIn(viewModelScope, SharingStarted.Eagerly, data.snapshot())
    val car: StateFlow<MyCar?> = garage.map { it?.selected }.stateIn(viewModelScope, SharingStarted.Eagerly, data.snapshot()?.selected)
    val canManageGarage: Boolean = data.canManageGarage

    fun selectCar(id: String) = data.selectCar(id)
    fun addCar(name: String) = data.addCar(name)
    fun removeCar(id: String) = data.removeCar(id)

    /** False when [km] is lower than a reading already on record, so nothing was saved. */
    fun updateKm(km: Int): Boolean = data.edit { it.withKm(km, LocalDate.now()) }

    fun addOilChange(change: OilChange) {
        data.edit { it.withOilChange(change) }
    }

    fun removeOilChange(change: OilChange) {
        data.edit { it.withoutOilChange(change) }
    }

    fun clearOilHistory() {
        data.edit { it.withoutOilHistory() }
    }

    fun updateProfile(profile: CarProfile) {
        data.edit { it.withProfile(profile) }
    }

    fun addDocument(name: String, expiresOn: LocalDate?, expiresText: String, note: String) {
        data.edit { it.withDocument(name, expiresOn, expiresText, note) }
    }

    fun removeDocument(id: String) {
        data.edit { it.withoutDocument(id) }
    }

    fun updateDocument(id: String, expiresOn: LocalDate?, expiresText: String, note: String) {
        data.edit { it.withDocumentUpdated(id, expiresOn, expiresText, note) }
    }

    /** False when the paper already has the most pictures (or is gone), so the picture is not kept. */
    fun addImage(docId: String, name: String): Boolean = data.edit { it.withImageAdded(docId, name) }

    fun removeImage(docId: String, name: String) {
        data.edit { it.withImageRemoved(docId, name) }
    }

    fun toggleTask(id: String) {
        data.edit { it.withTaskToggled(id) }
    }

    fun addTask(text: String) {
        data.edit { it.withTask(text) }
    }

    fun removeTask(id: String) {
        data.edit { it.withoutTask(id) }
    }
}
