package org.example.autoplay.mycar

import java.time.LocalDate

/** The "personal" build starts with the data of the driver's own car already in. */
internal fun initialMyCar() = MyCar(
    id = DEFAULT_CAR_ID,
    profile = CarProfile(
        name = "Hyundai i20 Active 2019",
        brand = "Hyundai",
        model = "i20 Active HB 1.4",
        trim = "i20 Active 1.4 MT L 2AB ABS (mecánico, versión L, 2 airbags, ABS)",
        year = 2019,
        plate = "LJLC.11-2",
        engine = "G4LC (N° G4LCJU995866)",
        vin = "MALBL51CAKM582836",
        color = "Gris oscuro",
        fuel = "Bencina",
        transmission = "Mecánica",
    ),
    documents = listOf(
        CarDocument(
            id = "permit",
            name = "Permiso de circulación",
            expiresOn = LocalDate.of(2027, 3, 31),
            expiresText = "",
            note = "Pagado en Quinta de Tilcoco, \$82.309. Renovar a nombre propio.",
        ),
        CarDocument(
            id = "soap",
            name = "SOAP",
            expiresOn = LocalDate.of(2027, 3, 31),
            expiresText = "",
            note = "Consorcio, póliza 26208328. Renovar a nombre propio.",
        ),
        CarDocument(
            id = "inspection",
            name = "Revisión técnica",
            expiresOn = LocalDate.of(2027, 4, 30),
            expiresText = "Abril 2027",
            note = "Aprobada el 02/09/2026 en Applus San Ramón, sello verde.",
        ),
    ),
    oilChanges = listOf(
        OilChange(LocalDate.of(2026, 5, 27), 77_596, "5W-30 Mannol", 87_596),
        OilChange(LocalDate.of(2025, 9, 11), 67_777, "5W-30 Valvoline", 77_777),
        OilChange(LocalDate.of(2025, 1, 29), 58_664, "5W-30 Mannol", 68_664),
        OilChange(LocalDate.of(2024, 7, 15), 48_594, "10W-40 Hyundai", 58_594),
    ),
    tasks = listOf(
        "Seguir la transferencia en Autofact hasta que quede inscrita en el Registro Civil",
        "Sacar el padrón nuevo a nombre propio y dejarlo en la guantera",
        "Guardar el contrato de compraventa y el comprobante de la transferencia bancaria",
        "Contratar seguro automotriz propio (el SOAP solo cubre lesiones a personas)",
        "Revisión con mecánico: embrague, frenos, suspensión y fugas",
        "Cambiar filtro de aire, filtro de polen, bujías, líquido de frenos y refrigerante (no hay registro)",
        "Comprar aceite 5W-30 (4 L) y filtro de aceite para el próximo cambio",
    ).mapIndexed { i, text -> Task("task-${i + 1}", text, done = false) },
    notes = listOf(
        "Autofact: sin prendas, embargos, multas, encargo por robo ni remates. Puntaje 100/100.",
        "Kilometraje coherente en todos los registros, sin señales de adulteración.",
        "El auto fue comprado nuevo en 2019 por Banco Santander (crédito o leasing). " +
            "Después tuvo dos dueños más antes de esta compra.",
        "El permiso se paga como \"IB 1.4 MT VALUE\", pero la factura dice versión \"L\". " +
            "Si la municipalidad pregunta, mostrar la factura.",
    ),
    km = 83_934,
    kmDate = LocalDate.of(2026, 10, 24),
)
