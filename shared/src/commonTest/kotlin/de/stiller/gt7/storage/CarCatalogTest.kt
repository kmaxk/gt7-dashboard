package de.stiller.gt7.storage

import kotlin.test.Test
import kotlin.test.assertEquals

class CarCatalogTest {
    private val catalog = CarCatalog(
        "ID,ShortName,Maker\n3332,GR Supra,1\nbroken\n12,\"Roadster, S\",2\n",
        "ID,Name,Country\n1,Toyota,1\n2,Mazda,1\n",
    )

    @Test fun knownCarResolvesByHeaderColumns() = assertEquals("GR Supra", catalog.name(3332))

    @Test fun unknownCarFallsBack() = assertEquals("Auto #1", catalog.name(1))

    @Test fun quotedNameKeepsComma() = assertEquals("Roadster, S", catalog.name(12))

    @Test fun makerComesFromMakerList() {
        assertEquals("Toyota", catalog.info(3332).maker)
        assertEquals(null, catalog.info(1).maker)
    }

    @Test fun customNameWinsOverList() {
        val c = catalog.withCustom(mapOf(3332 to "Supra", 7 to "Eigenbau"))
        assertEquals("Supra", c.name(3332))
        assertEquals("Eigenbau", c.name(7))
        assertEquals(CarInfo(3332, "Supra", "GR Supra", "Toyota", custom = true), c.info(3332))
        assertEquals(CarInfo(7, "Eigenbau", null, null, custom = true), c.info(7))
        assertEquals(CarInfo(1, "Auto #1", null, null, custom = false), c.info(1))
        // the original catalog is unchanged
        assertEquals("GR Supra", catalog.name(3332))
    }

    @Test fun searchMatchesNameMakerAndIdIgnoringCase() {
        val c = catalog.withCustom(mapOf(7 to "Eigenbau"))
        assertEquals(listOf(3332), c.search("supra").map { it.code })
        assertEquals(listOf(12), c.search("MAZDA").map { it.code })
        assertEquals(listOf(7), c.search("eigen").map { it.code })
        assertEquals(listOf(3332), c.search("3332").map { it.code })
        assertEquals(listOf(3332, 12, 7).sorted(), c.search(" ").map { it.code }.sorted())
    }
}
