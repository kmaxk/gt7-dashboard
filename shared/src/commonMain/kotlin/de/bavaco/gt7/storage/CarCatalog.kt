package de.bavaco.gt7.storage

/** A car as shown: [name] is the own name if [custom], else [listName], else "Auto #code". */
data class CarInfo(val code: Int, val name: String, val listName: String?, val maker: String?, val custom: Boolean)

private data class ListedCar(val name: String, val maker: String?)

/**
 * Maps GT7 car codes to names: own names first, then the community list cars.csv
 * (header must contain "ID" and "ShortName"; "Maker" resolves via maker.csv "ID" and "Name").
 */
class CarCatalog private constructor(private val listed: Map<Int, ListedCar>, private val custom: Map<Int, String>) {
    constructor(csv: String, makersCsv: String = "", custom: Map<Int, String> = emptyMap()) : this(parse(csv, makersCsv), custom)

    fun name(carCode: Int): String = custom[carCode] ?: listed[carCode]?.name ?: "Auto #$carCode"

    fun info(carCode: Int): CarInfo {
        val car = listed[carCode]
        return CarInfo(carCode, name(carCode), car?.name, car?.maker, carCode in custom)
    }

    /** Listed and own cars whose name, list name, maker or code contains [query], ignoring case. */
    fun search(query: String): List<CarInfo> {
        val q = query.trim()
        return (listed.keys + custom.keys).map(::info).filter { c ->
            q.isEmpty() || listOfNotNull(c.name, c.listName, c.maker, c.code.toString()).any { it.contains(q, ignoreCase = true) }
        }
    }

    /** The same list with [custom] as own names. */
    fun withCustom(custom: Map<Int, String>) = CarCatalog(listed, custom)

    private companion object {
        fun parse(csv: String, makersCsv: String): Map<Int, ListedCar> {
            val makers = columns(makersCsv, "ID", "Name").toMap()
            return columns(csv, "ID", "ShortName", "Maker").associate { (id, name, maker) ->
                id to ListedCar(name!!, maker?.toIntOrNull()?.let { makers[it] })
            }
        }

        /** Rows of [csv] with an integer [idCol] and a non-empty [nameCol]; [extra] columns may be missing. */
        fun columns(csv: String, idCol: String, nameCol: String, extra: String? = null): List<Triple<Int, String?, String?>> {
            val lines = csv.lineSequence().map { it.trimEnd('\r') }.filter { it.isNotBlank() }.toList()
            val header = lines.firstOrNull()?.let(::splitCsv)?.map { it.trim() }.orEmpty()
            val id = header.indexOf(idCol)
            val name = header.indexOf(nameCol)
            val more = extra?.let { header.indexOf(it) } ?: -1
            if (id < 0 || name < 0) return emptyList()
            return lines.drop(1).mapNotNull { line ->
                val cols = splitCsv(line)
                val code = cols.getOrNull(id)?.trim()?.toIntOrNull() ?: return@mapNotNull null
                val text = cols.getOrNull(name)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                Triple(code, text, cols.getOrNull(more)?.trim())
            }
        }

        fun List<Triple<Int, String?, String?>>.toMap(): Map<Int, String> = associate { it.first to it.second!! }

        /** Splits a CSV line; double-quoted fields may contain commas and "" for a quote. */
        fun splitCsv(line: String): List<String> {
            val out = mutableListOf<String>()
            val field = StringBuilder()
            var quoted = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    quoted && c == '"' && line.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                    c == '"' -> quoted = !quoted
                    c == ',' && !quoted -> { out += field.toString(); field.clear() }
                    else -> field.append(c)
                }
                i++
            }
            out += field.toString()
            return out
        }
    }
}
