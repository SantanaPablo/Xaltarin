package com.perromono.xaltarin



import android.content.Intent

import android.content.SharedPreferences

import android.content.pm.PackageManager

import android.net.VpnService

import android.os.Bundle

import android.os.Handler

import android.os.Looper

import android.widget.Toast

import androidx.activity.ComponentActivity

import androidx.activity.compose.setContent

import androidx.compose.foundation.BorderStroke

import androidx.compose.foundation.Image

import androidx.compose.foundation.background

import androidx.compose.foundation.clickable

import androidx.compose.foundation.focusable

import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.text.KeyboardActions

import androidx.compose.foundation.text.KeyboardOptions

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.Search

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.focus.FocusRequester

import androidx.compose.ui.focus.focusRequester

import androidx.compose.ui.focus.onFocusChanged

import androidx.compose.ui.graphics.Brush

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.input.key.onPreviewKeyEvent

import androidx.compose.ui.res.painterResource

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.input.ImeAction

import androidx.compose.ui.text.input.KeyboardType

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp

import androidx.compose.ui.window.Dialog

import androidx.core.content.ContextCompat

import androidx.lifecycle.lifecycleScope

import com.perromono.saltarin.ui.theme.*

import de.blinkt.openvpn.VpnProfile

import de.blinkt.openvpn.core.ConfigParser

import de.blinkt.openvpn.core.ProfileManager

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.delay

import kotlinx.coroutines.launch

import kotlinx.coroutines.withContext

import java.net.URL

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


data class AppInfo(val name: String, val packageName: String)



class MainActivity : ComponentActivity() {



    private val URL_OVPN = "https://raw.githubusercontent.com/SantanaPablo/xaltarin-config/refs/heads/main/perfil.ovpn"

// =====================================================================



    private val VPN_REQUEST_CODE = 100

    private var pendingPackageName = ""

    private var pendingSeconds = 0L

    private var pendingConfigOvpn = ""



    private lateinit var prefs: SharedPreferences



    private var appPendiente = ""

    private var segundosPendientes = 0L

    private var vpnYaConectada = false



    private var estadoAccion = mutableStateOf("IDLE") // IDLE, DESCARGANDO, CONECTANDO



    private val vpnListener = object : de.blinkt.openvpn.core.VpnStatus.StateListener {

        override fun updateState(

            state: String?,

            logmessage: String?,

            localizedResId: Int,

            level: de.blinkt.openvpn.core.ConnectionStatus?,

            intent: Intent?

        ) {

            android.util.Log.d("SALTARIN_VPN", "Estado: $state | Nivel: $level")

            when (level) {

                de.blinkt.openvpn.core.ConnectionStatus.LEVEL_CONNECTED -> {

                    if (vpnYaConectada) return

                    vpnYaConectada = true

                    runOnUiThread {

                        estadoAccion.value = "IDLE"

                        toast("VPN CONECTADA. Abriendo app en breve...")



                        iniciarTemporizador(segundosPendientes)



                        Handler(Looper.getMainLooper()).postDelayed({

                            abrirAplicacion(appPendiente)

                        }, 3000) // Delay de 3 seg para que la TV respire

                    }

                }

                de.blinkt.openvpn.core.ConnectionStatus.LEVEL_AUTH_FAILED -> {

                    runOnUiThread {

                        estadoAccion.value = "IDLE"

                        toast("Error de autenticación VPN. Verificá el perfil en la nube.")

                    }

                }

                de.blinkt.openvpn.core.ConnectionStatus.LEVEL_NOTCONNECTED -> {

                    if (state != "NOPROCESS") {

                        runOnUiThread {

                            estadoAccion.value = "IDLE"

                            toast("VPN desconectada")

                        }

                    }

                }

                else -> {}

            }

        }

        override fun setConnectedVPN(uuid: String?) {}

    }



    @OptIn(ExperimentalMaterial3Api::class)

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("saltarin_prefs", MODE_PRIVATE)



        setContent {

            MaterialTheme(colorScheme = XaltarinColorScheme) {

                Scaffold(

                    topBar = {

                        TopAppBar(

                            title = {

                                Row(verticalAlignment = Alignment.CenterVertically) {

                                    Image(

                                        painter = painterResource(id = R.mipmap.saltarin_launcher),

                                        contentDescription = "Logo Xaltarin",

                                        modifier = Modifier.size(32.dp)

                                    )

                                    Spacer(Modifier.width(8.dp))

                                    Text(

                                        "Xaltarin Cloud",

                                        fontWeight = FontWeight.ExtraBold,

                                        color = White,

                                        fontSize = 20.sp,

                                        letterSpacing = 0.5.sp

                                    )

                                }

                            },

                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface1)

                        )

                    }

                ) { innerPadding ->

                    Surface(

                        modifier = Modifier.fillMaxSize().padding(innerPadding),

                        color = MaterialTheme.colorScheme.background

                    ) {

                        PantallaPrincipal()

                    }

                }

            }

        }

    }



    @Composable

    private fun XCard(content: @Composable ColumnScope.() -> Unit) {

        Card(

            modifier = Modifier.fillMaxWidth(),

            shape = RoundedCornerShape(16.dp),

            colors = CardDefaults.cardColors(containerColor = Surface1),

            border = BorderStroke(1.dp, Outline)

        ) {

            Column(modifier = Modifier.padding(20.dp), content = content)

        }

    }



    @Composable

    private fun SectionTitle(text: String) {

        Row(verticalAlignment = Alignment.CenterVertically) {

            Box(

                modifier = Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(BlueVibrant)

            )

            Spacer(Modifier.width(8.dp))

            Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = White)

        }

        Spacer(Modifier.height(16.dp))

    }



    @Composable

    private fun xTextFieldColors() = OutlinedTextFieldDefaults.colors(

        focusedBorderColor = BlueVibrant, unfocusedBorderColor = Outline,

        focusedLabelColor = BlueVibrant, unfocusedLabelColor = TextMuted,

        cursorColor = BlueVibrant, focusedTextColor = White, unfocusedTextColor = White,

        focusedContainerColor = Surface2, unfocusedContainerColor = Surface2

    )



    @Composable

    fun PantallaPrincipal() {

        var selectedApp by remember {

            mutableStateOf(

                prefs.getString("last_app_package", null)?.let { pkg ->

                    AppInfo(prefs.getString("last_app_name", pkg) ?: pkg, pkg)

                }

            )

        }

        var seconds by remember { mutableStateOf(prefs.getString("last_seconds", "30") ?: "30") }

        var showAppPicker by remember { mutableStateOf(false) }



        val focusAppPicker = remember { FocusRequester() }

        val focusSeconds = remember { FocusRequester() }

        val focusBoton = remember { FocusRequester() }



        if (showAppPicker) {

            AppPickerDialog(

                onDismiss = { showAppPicker = false },

                onAppSelected = { app -> selectedApp = app; showAppPicker = false }

            )

        }



        fun Modifier.tvNav(onUp: (() -> Unit)? = null, onDown: (() -> Unit)? = null): Modifier =

            this.onPreviewKeyEvent { event ->

                if (event.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false

                when (event.nativeKeyEvent.keyCode) {

                    android.view.KeyEvent.KEYCODE_DPAD_UP -> { onUp?.invoke(); onUp != null }

                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> { onDown?.invoke(); onDown != null }

                    else -> false

                }

            }



        LazyColumn(

            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),

            verticalArrangement = Arrangement.spacedBy(16.dp),

            contentPadding = PaddingValues(vertical = 20.dp)

        ) {

            item {

                XCard {

                    SectionTitle("Configuración de Lanzamiento")



                    var appFocused by remember { mutableStateOf(false) }

                    Box(

                        modifier = Modifier

                            .fillMaxWidth()

                            .padding(bottom = 16.dp)

                            .clip(RoundedCornerShape(10.dp))

                            .background(

                                if (appFocused) Brush.linearGradient(listOf(BlueDeep, BlueVibrant.copy(alpha = 0.4f)))

                                else Brush.linearGradient(listOf(Surface2, Surface2))

                            )

                            .focusRequester(focusAppPicker)

                            .onFocusChanged { appFocused = it.isFocused }

                            .focusable()

                            .onPreviewKeyEvent { event ->

                                if (event.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false

                                when (event.nativeKeyEvent.keyCode) {

                                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,

                                    android.view.KeyEvent.KEYCODE_ENTER,

                                    android.view.KeyEvent.KEYCODE_BUTTON_A -> { showAppPicker = true; true }

                                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {

                                        try { focusSeconds.requestFocus() } catch (_: Exception) {}; true

                                    }

                                    else -> false

                                }

                            }

                            .clickable { showAppPicker = true }

                    ) {

                        Row(

                            modifier = Modifier.fillMaxWidth().padding(16.dp),

                            horizontalArrangement = Arrangement.SpaceBetween,

                            verticalAlignment = Alignment.CenterVertically

                        ) {

                            Column(modifier = Modifier.weight(1f)) {

                                Text(

                                    text = selectedApp?.name ?: "Elegir aplicación destino",

                                    color = if (selectedApp != null) White else TextMuted,

                                    fontWeight = if (selectedApp != null) FontWeight.Medium else FontWeight.Normal

                                )

                                if (selectedApp != null) {

                                    Text(text = selectedApp!!.packageName, fontSize = 12.sp, color = TextMuted)

                                }

                            }

                            Icon(Icons.Default.Search, contentDescription = null, tint = if (appFocused) BlueVibrant else TextMuted)

                        }

                    }



                    OutlinedTextField(

                        value = seconds,

                        onValueChange = { seconds = it },

                        label = { Text("Tiempo activo") },

                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),

                        keyboardActions = KeyboardActions(onDone = { try { focusBoton.requestFocus() } catch (_: Exception) {} }),

                        suffix = { Text("seg", color = TextMuted) },

                        singleLine = true,

                        colors = xTextFieldColors(),

                        shape = RoundedCornerShape(10.dp),

                        modifier = Modifier

                            .fillMaxWidth()

                            .focusRequester(focusSeconds)

                            .tvNav(

                                onUp = { try { focusAppPicker.requestFocus() } catch (_: Exception) {} },

                                onDown = { try { focusBoton.requestFocus() } catch (_: Exception) {} }

                            )

                    )

                }

            }



            item {

                var botonFocused by remember { mutableStateOf(false) }

                val canConnect = selectedApp != null && estadoAccion.value == "IDLE"



                Box(

                    modifier = Modifier

                        .fillMaxWidth()

                        .height(56.dp)

                        .clip(RoundedCornerShape(14.dp))

                        .background(

                            if (canConnect)

                                Brush.linearGradient(if (botonFocused) listOf(BlueVibrant, BlueLuminous) else listOf(BlueDeep, BlueVibrant))

                            else

                                Brush.linearGradient(listOf(Surface2, Surface2))

                        )

                        .focusRequester(focusBoton)

                        .onFocusChanged { botonFocused = it.isFocused }

                        .focusable()

                        .onPreviewKeyEvent { event ->

                            if (event.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false

                            if (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {

                                try { focusSeconds.requestFocus() } catch (_: Exception) {}; true

                            } else false

                        }

                        .clickable(enabled = canConnect) {

                            val secs = seconds.toLongOrNull()

                            if (selectedApp == null) {

                                toast("Elegí una aplicación")

                            } else if (secs == null || secs <= 0) {

                                toast("Ingresá un tiempo válido")

                            } else {

// Guardar preferencias

                                prefs.edit()

                                    .putString("last_app_package", selectedApp!!.packageName)

                                    .putString("last_app_name", selectedApp!!.name)

                                    .putString("last_seconds", seconds)

                                    .apply()



// Iniciar proceso de descarga y conexión

                                descargarYConectar(selectedApp!!.packageName, secs)

                            }

                        },

                    contentAlignment = Alignment.Center

                ) {

                    if (estadoAccion.value != "IDLE") {

                        Row(verticalAlignment = Alignment.CenterVertically) {

                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = White, strokeWidth = 2.dp)

                            Spacer(Modifier.width(12.dp))

                            val textoAccion = if (estadoAccion.value == "DESCARGANDO") "Descargando perfil..." else "Conectando..."

                            Text(textoAccion, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = White)

                        }

                    } else {

                        Text(

                            text = if (selectedApp != null) "Conectar y abrir ${selectedApp!!.name}" else "Conectar",

                            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (canConnect) White else TextMuted

                        )

                    }

                }

            }

        }

    }



    @Composable

    fun AppPickerDialog(onDismiss: () -> Unit, onAppSelected: (AppInfo) -> Unit) {

        var searchQuery by remember { mutableStateOf("") }

        val searchFocus = remember { FocusRequester() }

        val firstItemFocus = remember { FocusRequester() }



        val allApps = remember {

            val pm: PackageManager = packageManager

            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }

            pm.queryIntentActivities(intent, 0)

                .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }

                .distinctBy { it.packageName }

                .sortedBy { it.name.lowercase() }

        }



        val filteredApps = remember(searchQuery) {

            if (searchQuery.isBlank()) allApps

            else allApps.filter { it.name.contains(searchQuery, true) || it.packageName.contains(searchQuery, true) }

        }



        LaunchedEffect(Unit) { delay(150); searchFocus.requestFocus() }



        Dialog(onDismissRequest = onDismiss) {

            Surface(

                shape = RoundedCornerShape(20.dp), color = Surface1,

                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)

            ) {

                Column(modifier = Modifier.padding(20.dp)) {

                    Row(verticalAlignment = Alignment.CenterVertically) {

                        Box(modifier = Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(BlueVibrant))

                        Spacer(Modifier.width(8.dp))

                        Text("Elegir aplicación", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = White)

                    }

                    Spacer(Modifier.height(16.dp))



                    OutlinedTextField(

                        value = searchQuery,

                        onValueChange = { searchQuery = it },

                        placeholder = { Text("Buscar app...", color = TextMuted) },

                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },

                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),

                        keyboardActions = KeyboardActions(onSearch = { try { firstItemFocus.requestFocus() } catch (_: Exception) {} }),

                        colors = OutlinedTextFieldDefaults.colors(

                            focusedBorderColor = BlueVibrant, unfocusedBorderColor = Outline,

                            focusedTextColor = White, unfocusedTextColor = White,

                            focusedContainerColor = Surface2, unfocusedContainerColor = Surface2

                        ),

                        shape = RoundedCornerShape(10.dp),

                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).focusRequester(searchFocus)

                            .onPreviewKeyEvent { event ->

                                if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&

                                    event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {

                                    try { firstItemFocus.requestFocus() } catch (_: Exception) {}; true

                                } else false

                            },

                        singleLine = true

                    )



                    androidx.compose.foundation.lazy.LazyColumn {

                        items(filteredApps.size) { index ->

                            val app = filteredApps[index]

                            var isFocused by remember { mutableStateOf(false) }

                            val itemFocus = remember { FocusRequester() }



                            Row(

                                modifier = Modifier

                                    .fillMaxWidth()

                                    .then(if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier)

                                    .onFocusChanged { isFocused = it.isFocused }

                                    .focusRequester(itemFocus)

                                    .focusable()

                                    .clip(RoundedCornerShape(8.dp))

                                    .background(if (isFocused) Brush.linearGradient(listOf(BlueDeep, BlueVibrant.copy(alpha = 0.35f))) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))

                                    .onPreviewKeyEvent { event ->

                                        if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {

                                            when (event.nativeKeyEvent.keyCode) {

                                                android.view.KeyEvent.KEYCODE_DPAD_CENTER,

                                                android.view.KeyEvent.KEYCODE_ENTER,

                                                android.view.KeyEvent.KEYCODE_BUTTON_A -> { onAppSelected(app); true }

                                                android.view.KeyEvent.KEYCODE_DPAD_UP -> {

                                                    if (index == 0) { try { searchFocus.requestFocus() } catch (_: Exception) {}; true } else false

                                                }

                                                else -> false

                                            }

                                        } else false

                                    }

                                    .clickable { onAppSelected(app) }

                                    .padding(horizontal = 12.dp, vertical = 12.dp),

                                verticalAlignment = Alignment.CenterVertically

                            ) {

                                Column(modifier = Modifier.weight(1f)) {

                                    Text(text = app.name, fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium, color = if (isFocused) BlueLuminous else White)

                                    Text(text = app.packageName, fontSize = 12.sp, color = TextMuted)

                                }

                            }

                            if (index < filteredApps.size - 1) {

                                HorizontalDivider(color = Outline, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 12.dp))

                            }

                        }

                    }

                }

            }

        }

    }



// ── LÓGICA DE DESCARGA Y VPN ─────────────────────────────────────────────



    private fun descargarYConectar(pkg: String, secs: Long) {
        estadoAccion.value = "DESCARGANDO"

        lifecycleScope.launch {
            val ovpnText = withContext(Dispatchers.IO) {
                try {
                    // PARCHE PARA SMART TVS VIEJAS:
                    // Obligamos a la app a confiar en el HTTPS de GitHub sin verificar certificados
                    val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                        override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                        override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
                    })

                    val sc = SSLContext.getInstance("SSL")
                    sc.init(null, trustAllCerts, SecureRandom())
                    HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
                    HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }

                    // Ahora sí, hacemos la petición a la URL
                    URL(URL_OVPN).readText()
                } catch (e: Exception) {
                    android.util.Log.e("SALTARIN_CLOUD", "Error descargando .ovpn", e)
                    null
                }
            }

            if (ovpnText.isNullOrBlank()) {
                estadoAccion.value = "IDLE"
                toast("Fallo al descargar el archivo de configuración. Revisá internet o la URL.")
            } else {
                estadoAccion.value = "CONECTANDO"
                prepararConexion(ovpnText, pkg, secs)
            }
        }
    }



    private fun prepararConexion(configText: String, pkg: String, secs: Long) {

        pendingConfigOvpn = configText

        pendingPackageName = pkg

        pendingSeconds = secs



        val vpnIntent = VpnService.prepare(this)

        if (vpnIntent != null) {

            startActivityForResult(vpnIntent, VPN_REQUEST_CODE)

        } else {

            iniciarProceso(configText, pkg, secs)

        }

    }



    @Deprecated("Deprecated in Java")

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {

            iniciarProceso(pendingConfigOvpn, pendingPackageName, pendingSeconds)

        } else {

            estadoAccion.value = "IDLE"

        }

    }



    private fun iniciarProceso(configText: String, packageName: String, seconds: Long) {

        vpnYaConectada = false

        appPendiente = packageName

        segundosPendientes = seconds



        val conexionExitosa = conectarVPN(configText)

        if (!conexionExitosa) { estadoAccion.value = "IDLE"; return }



        try { de.blinkt.openvpn.core.VpnStatus.removeStateListener(vpnListener) } catch (_: Exception) {}

        de.blinkt.openvpn.core.VpnStatus.addStateListener(vpnListener)

    }



    private fun conectarVPN(configText: String): Boolean {

        return try {

            var ovpnConfig = configText



            ovpnConfig = ovpnConfig.replace(Regex("^dev\\s+tun\\d+", RegexOption.MULTILINE), "dev tun")

            ovpnConfig = ovpnConfig.replace(Regex("^auth-user-pass\\s+.*$", RegexOption.MULTILINE), "auth-user-pass")



            val configParser = de.blinkt.openvpn.core.ConfigParser()

            configParser.parseConfig(java.io.StringReader(ovpnConfig))

            val profile: VpnProfile = configParser.convertProfile()



// NOTA: Como pediste sacar el User/Pass, asumimos que no hace falta,

// o que las credenciales están embebidas en el .ovpn

// Si el servidor exige auth, el .ovpn debe tener <auth-user-pass> crudo adentro,

// o se cortará acá pidiendo usuario.

            profile.mUsername = null

            profile.mPassword = null



            val pm = de.blinkt.openvpn.core.ProfileManager.getInstance(this)

            pm.addProfile(profile)

            pm.saveProfileList(this)

            de.blinkt.openvpn.core.ProfileManager.saveProfile(this, profile)



            de.blinkt.openvpn.core.GlobalPreferences.setInstance(false, false, false)

            de.blinkt.openvpn.core.VPNLaunchHelper.startOpenVpn(profile, this, "", false)



            true

        } catch (e: Exception) {

            android.util.Log.e("SALTARIN_VPN", "Error en VPN: ${e.message}", e)

            toast("Error configurando la VPN: ${e.message}")

            false

        }

    }



    private fun abrirAplicacion(packageName: String) {

        try {

            val intent = packageManager.getLaunchIntentForPackage(packageName)

            if (intent != null) {

                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                startActivity(intent)

            } else {

                toast("No se pudo abrir la aplicación")

            }

        } catch (e: Exception) {

            android.util.Log.e("SALTARIN_APP", "Error abriendo app", e)

            toast("Error al abrir la app")

        }

    }



    private fun iniciarTemporizador(segundos: Long) {

        val intent = Intent(this, VpnTimerService::class.java)

        intent.putExtra("TIMER_SECONDS", segundos)

        ContextCompat.startForegroundService(this, intent)

    }



    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()



    override fun onDestroy() {

        try { de.blinkt.openvpn.core.VpnStatus.removeStateListener(vpnListener) } catch (_: Exception) {}

        super.onDestroy()

    }

}