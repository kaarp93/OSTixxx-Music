package cat.gbdsmixxx

import android.content.Context

/** Preferències de l'app (nombres i textos desats). */
class Env(val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("gbdsmixxx", Context.MODE_PRIVATE)
    fun int(k: String, d: Int = 0) = prefs.getInt(k, d)
    fun putInt(k: String, v: Int) = prefs.edit().putInt(k, v).apply()
    fun str(k: String, d: String = "") = prefs.getString(k, d) ?: d
    fun putStr(k: String, v: String) = prefs.edit().putString(k, v).apply()
    /** Desa diversos enters d'un cop (una sola escriptura). */
    fun putInts(m: Map<String, Int>) { val e = prefs.edit(); for ((k, v) in m) e.putInt(k, v); e.apply() }
    /** Esborra aquestes claus. */
    fun esborra(vararg k: String) { val e = prefs.edit(); for (x in k) e.remove(x); e.apply() }
    /** Esborra totes les claus que comencen per algun dels prefixos. */
    fun esborraPrefixos(vararg pf: String) { val e = prefs.edit(); for (k in prefs.all.keys) if (pf.any { k.startsWith(it) }) e.remove(k); e.apply() }
}
