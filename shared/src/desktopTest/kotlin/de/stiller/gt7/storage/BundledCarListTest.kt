package de.stiller.gt7.storage

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The bundled community lists (ddm999/gt7info) parse with the catalog's reader. */
class BundledCarListTest {
    private fun resource(name: String) = File("src/commonMain/composeResources/files/$name").readText()

    private val catalog = CarCatalog(resource("cars.csv"), resource("maker.csv"))

    @Test fun knownCarHasNameAndMaker() =
        assertEquals(CarInfo(24, "180SX Type X '96", "180SX Type X '96", "Nissan", custom = false), catalog.info(24))

    @Test fun everyListedCarHasAMaker() {
        val all = catalog.search("")
        assertTrue(all.size > 500, "only ${all.size} cars")
        assertEquals(emptyList(), all.filter { it.maker == null }.map { it.code })
    }
}
