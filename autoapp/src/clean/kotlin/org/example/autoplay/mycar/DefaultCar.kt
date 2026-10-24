package org.example.autoplay.mycar

import java.time.LocalDate

/** The "clean" build carries no data of anyone: it starts with one empty car, filled in from its data sheet. */
internal fun initialMyCar(): MyCar = emptyCar("Mi auto", LocalDate.now(), id = DEFAULT_CAR_ID)
