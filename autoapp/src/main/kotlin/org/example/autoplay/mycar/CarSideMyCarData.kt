package org.example.autoplay.mycar

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.example.autoplay.car.CarServer

/**
 * "Mi auto" on the radio: the car of the phone in line is shown and edited here, but it lives on the phone,
 * which is also where the cars are chosen and added.
 */
class CarSideMyCarData(private val server: CarServer) : MyCarData {
    override val garage: Flow<Garage?> = server.state.map { state -> state.myCar?.let { Garage(listOf(it), it.id) } }.distinctUntilChanged()

    override fun snapshot(): Garage? = server.state.value.myCar?.let { Garage(listOf(it), it.id) }

    override val canManageGarage = false

    override fun edit(transform: (MyCar) -> MyCar?): Boolean = server.editMyCar(transform)

    override fun selectCar(id: String) = Unit
    override fun addCar(name: String) = Unit
    override fun removeCar(id: String) = Unit
}
