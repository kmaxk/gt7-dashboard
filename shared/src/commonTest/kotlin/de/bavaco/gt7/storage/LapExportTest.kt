package de.bavaco.gt7.storage

import de.bavaco.gt7.session.LapSample
import kotlin.test.Test
import kotlin.test.assertEquals

class LapExportTest {
    private val samples = listOf(
        LapSample(0, 0f, 50f, 1f, 0f, 4, 7000f, 1.5f, -2f, 0.8f),
        LapSample(
            100, 5.25f, 10f, 0f, 0.5f, 2, 3500.5f, 3f, -4f, 0f,
            steeringRad = 0.5f, yawRate = -0.25f, latAccel = 9.81f, longAccel = -4.905f, slipFront = 0.012f, slipRear = 0.2f,
        ),
    )

    @Test
    fun csvHasHeaderAndOneLinePerSample() {
        assertEquals(
            "time_ms,distance_m,speed_kmh,throttle_pct,brake_pct,gear,rpm,x,z,boost_bar,steering_deg,yaw_deg_s,lat_g,long_g,slip_front_pct,slip_rear_pct\n" +
                "0,0.00,180.0,100.0,0.0,4,7000,1.50,-2.00,0.80,,,,,,\n" +
                "100,5.25,36.0,0.0,50.0,2,3501,3.00,-4.00,0.00,28.6,-14.3,1.00,-0.50,1.2,20.0\n",
            lapCsv(samples),
        )
    }

    @Test
    fun jsonCarriesMetaDataAndEscapesText() {
        val meta = LapMeta(track = "Sugo \"IRC\"", car = null, driver = "Max\\M", sessionStartedAtMs = 0L, lapNumber = 3, timeMs = 85_797, valid = true)
        assertEquals(
            "{\"track\":\"Sugo \\\"IRC\\\"\",\"car\":null,\"driver\":\"Max\\\\M\",\"session_started_ms\":0,\"lap\":3,\"time_ms\":85797,\"valid\":true," +
                "\"samples\":[" +
                "{\"time_ms\":0,\"distance_m\":0.00,\"speed_kmh\":180.0,\"throttle_pct\":100.0,\"brake_pct\":0.0,\"gear\":4,\"rpm\":7000,\"x\":1.50,\"z\":-2.00,\"boost_bar\":0.80," +
                "\"steering_deg\":null,\"yaw_deg_s\":null,\"lat_g\":null,\"long_g\":null,\"slip_front_pct\":null,\"slip_rear_pct\":null}," +
                "{\"time_ms\":100,\"distance_m\":5.25,\"speed_kmh\":36.0,\"throttle_pct\":0.0,\"brake_pct\":50.0,\"gear\":2,\"rpm\":3501,\"x\":3.00,\"z\":-4.00,\"boost_bar\":0.00," +
                "\"steering_deg\":28.6,\"yaw_deg_s\":-14.3,\"lat_g\":1.00,\"long_g\":-0.50,\"slip_front_pct\":1.2,\"slip_rear_pct\":20.0}" +
                "]}",
            lapJson(meta, samples),
        )
    }

    @Test
    fun fileNamesAreSafe() {
        assertEquals("Sugo-IRC_Runde-3_1-25-797.csv", lapFileName("Sugo IRC", 3, 85_797, "csv"))
        assertEquals("Dragon-Trail-Gaerten_Runde-12_1-05-004.json", lapFileName("Dragon Trail / Gärten", 12, 65_004, "json"))
        assertEquals("Runde-1_0-59-999_Analyse.png", lapFileName(null, 1, 59_999, "png", suffix = "Analyse"))
    }
}
