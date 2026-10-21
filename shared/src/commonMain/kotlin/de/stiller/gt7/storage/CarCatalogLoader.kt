package de.stiller.gt7.storage

import gt7dashboard.shared.generated.resources.Res

/** Loads the bundled cars.csv and maker.csv (Res is internal to the shared module). */
suspend fun loadCarCatalog(): CarCatalog =
    CarCatalog(Res.readBytes("files/cars.csv").decodeToString(), Res.readBytes("files/maker.csv").decodeToString())
