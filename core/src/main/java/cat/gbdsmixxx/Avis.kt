package cat.gbdsmixxx

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Avís del primer inici (l'autor, 2026-11-11): no totes les ROMs ni tots els arxius funcionaran. Surt una sola vegada (`avis_inicial` a les preferències), a OSTixxx i a OSTixxx Music. */
@Composable
fun AvisInicial(color: Color = Col.verd) {
    var obert by remember { mutableStateOf(App.env.int("avis_inicial", 0) == 0) }
    if (!obert) return
    Box(Modifier.fillMaxSize().background(Color(0xF00B0B0E)).clickable { }, contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp).widthIn(max = 420.dp).clip(RoundedCornerShape(14.dp)).background(Col.panell).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(t("Avís"), color = Col.text, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(t("No totes les ROMs ni tots els arxius funcionaran. Cada joc porta el seu propi motor de so i només se'n reconeixen alguns: pot fallar, sonar malament o no aparèixer cap cançó."), color = Col.text, fontSize = 14.sp)
            Btn(Modifier.fillMaxWidth().height(44.dp), color, false, false, { App.env.putInt("avis_inicial", 1); obert = false }) { Tx(t("ENTÈS"), Col.text, 14, true, centre = true) }
        }
    }
}

/** Informe de l'última importació (OSTixxx): una línia per fitxer, amb el motiu quan no s'ha pogut importar. Es tanca tocant el botó. */
@Composable
fun InformeImportacio(color: Color = Col.verd) {
    val l = Informe.linies
    if (l.isEmpty()) return
    Box(Modifier.fillMaxSize().background(Color(0xF00B0B0E)).clickable { }, contentAlignment = Alignment.Center) {
        Column(Modifier.padding(20.dp).widthIn(max = 560.dp).clip(RoundedCornerShape(14.dp)).background(Col.panell).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("Informe d'importació"), color = Col.text, fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(l.size) { i -> val (ok, txt) = l[i]; Text((if (ok) "✓ " else "✗ ") + txt, color = if (ok) Col.verd else Col.vermell, fontSize = 12.sp) }
            }
            Btn(Modifier.fillMaxWidth().height(42.dp), color, false, false, { Informe.linies = emptyList() }) { Tx(t("TANCA"), Col.text, 14, true, centre = true) }
        }
    }
}
