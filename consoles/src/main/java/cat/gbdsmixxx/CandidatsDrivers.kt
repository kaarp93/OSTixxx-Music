package cat.gbdsmixxx

import java.io.File

/**
 * Drivers candidats de «Prova compatibilitat» (l'autor, 2026-11-22): «Auto (sense validar)». Lògica pura (sense Android).
 * - Cada candidat és un paquet `DriversBin` d'una sola definició a `files/drivers/candidats/<CRC>.bin` (escriptura segura amb `.bak`, com `importats.bin`). Res en TXT.
 * - No es queden per sempre: caduquen als `CADUCITAT_MS` (data del fitxer) i n'hi ha com a molt `MAX` (es treuen els més vells). Es netegen en carregar-los i en desar-ne un.
 * - En memòria només n'hi ha l'índex d'un paquet fet amb tots (`paquet`), que es dona al registre (`RegistreDrivers.posaCandidats`); cada driver es construeix a demanda.
 * - «Confirma el driver» (`confirma`): la definició passa al paquet d'importats (el mateix de «Importa drivers») com a driver permanent, es treu el candidat i es desa
 *   l'entrada al catàleg definitiu. L'id del driver (`auto:CRC.format:rev`) no canvia: les marques i els noms de pista de l'àlbum es conserven.
 */
object CandidatsDrivers {
    const val CADUCITAT_MS = 7L * 24 * 3600 * 1000
    const val MAX = 5

    fun carpeta(drivers: File) = File(drivers, "candidats")
    private fun fitxer(dir: File, crc: Long) = File(dir, "%08X.bin".format(crc and 0xFFFFFFFFL))
    private fun crcDeNom(f: File): Long? = if (f.name.length == 12 && f.name.endsWith(".bin")) f.name.substring(0, 8).toLongOrNull(16) else null
    private fun esborra(dir: File, crc: Long) { val f = fitxer(dir, crc); for (x in listOf(f, File(f.path + ".bak"), File(f.path + ".tmp"))) x.delete() }

    /** Treu els caducats i els que passen de `MAX` (els més vells); també els `.bak`/`.tmp` que s'han quedat sols. Torna els CRC trets. */
    fun neteja(dir: File, ara: Long = System.currentTimeMillis()): List<Long> {
        val fs = dir.listFiles()?.toList() ?: return emptyList()
        val tretes = ArrayList<Long>()
        val bons = fs.filter { crcDeNom(it) != null }.sortedByDescending { it.lastModified() }
        for ((i, f) in bons.withIndex()) if (i >= MAX || ara - f.lastModified() > CADUCITAT_MS || ara < f.lastModified() - 86_400_000L) { val c = crcDeNom(f)!!; esborra(dir, c); tretes.add(c) }
        val vius = bons.map { it.name }.toSet() - tretes.map { "%08X.bin".format(it) }.toSet()
        for (f in dir.listFiles() ?: emptyArray()) if (crcDeNom(f) == null && vius.none { f.name.startsWith(it) }) f.delete()
        return tretes
    }

    /** Desa (o substitueix) el candidat d'una ROM i neteja els vells. */
    fun desa(dir: File, d: DefDriver) {
        DriversBin.desa(fitxer(dir, d.crc), DriversBin.escriu(listOf(d)))
        neteja(dir)
    }

    /** Definició del candidat d'una ROM (null si no n'hi ha o és corrupte). */
    fun def(dir: File, crc: Long): DefDriver? = DriversBin.obreFitxer(fitxer(dir, crc))?.tot()?.firstOrNull { it.crc == (crc and 0xFFFFFFFFL) }

    /** Tots els candidats vius en un sol paquet en memòria (només n'hi ha uns quants i són petits). Null si no n'hi ha cap. */
    fun paquet(dir: File, ara: Long = System.currentTimeMillis()): DriversBin.Pack? {
        neteja(dir, ara)
        val defs = (dir.listFiles() ?: emptyArray()).filter { crcDeNom(it) != null }.mapNotNull { f -> def(dir, crcDeNom(f)!!) }
        return if (defs.isEmpty()) null else DriversBin.obre(DriversBin.FontBytes(DriversBin.escriu(defs)))
    }

    /** «Descarta»: treu el candidat. */
    fun descarta(dir: File, crc: Long) = esborra(dir, crc)

    /**
     * «Confirma el driver»: la definició passa al paquet d'importats (`fImportats`, escriptura segura amb `.bak`), es treu el candidat i es desa al catàleg definitiu.
     * `importats` = el paquet d'importats actual. Torna el paquet d'importats nou (llegit del fitxer) o null si no hi havia candidat.
     */
    fun confirma(dir: File, fImportats: File, importats: DriversBin.Pack?, crc: Long, mida: Long, nom: String, titols: Map<Int, TitolCataleg>, ara: Long = System.currentTimeMillis()): DriversBin.Pack? {
        val d = def(dir, crc) ?: return null
        DriversBin.desa(fImportats, DriversBin.fusiona(importats, listOf(d)))
        val nou = DriversBin.obreFitxer(fImportats) ?: throw java.io.IOException("importats.bin no es pot tornar a llegir")
        esborra(dir, crc)
        // Només el driver (l'autor, 2026-11-23: «confirmar driver no ha de confirmar tot»): el catàleg definitiu (etiquetes, noms i ordre) és «Valida del tot», a part
        return nou
    }
}
