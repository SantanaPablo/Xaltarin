package com.perromono.saltarin

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.perromono.saltarin.ui.theme.*
import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.core.ConfigParser
import de.blinkt.openvpn.core.ProfileManager
import kotlinx.coroutines.delay

data class AppInfo(val name: String, val packageName: String)

class MainActivity : ComponentActivity() {

    private val VPN_REQUEST_CODE = 100
    private var pendingPackageName = ""
    private var pendingSeconds = 0L
    private var pendingUri: Uri? = null
    private var pendingUser = ""
    private var pendingPass = ""

    private lateinit var prefs: SharedPreferences

    private var appPendiente = ""
    private var segundosPendientes = 0L
    private var vpnYaConectada = false

    private var isConnecting = mutableStateOf(false)

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
                        isConnecting.value = false
                        toast("VPN CONECTADA")
                        abrirAplicacion(appPendiente)
                        iniciarTemporizador(segundosPendientes)
                    }
                }
                de.blinkt.openvpn.core.ConnectionStatus.LEVEL_AUTH_FAILED -> {
                    runOnUiThread {
                        isConnecting.value = false
                        toast("Error de autenticación VPN. Revisá usuario/clave.")
                    }
                }
                de.blinkt.openvpn.core.ConnectionStatus.LEVEL_NOTCONNECTED -> {
                    if (state != "NOPROCESS") {
                        runOnUiThread {
                            isConnecting.value = false
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
                                        "Xaltarin",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = White,
                                        fontSize = 20.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Surface1
                            )
                        )
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        PantallaPrincipal()
                    }
                }
            }
        }
    }

    // ── Helpers de estilo reutilizables ──────────────────────────────────────

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
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BlueVibrant)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = White
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    @Composable
    private fun xTextFieldColors() = OutlinedTextFieldDefaults.colors(
        focusedBorderColor      = BlueVibrant,
        unfocusedBorderColor    = Outline,
        focusedLabelColor       = BlueVibrant,
        unfocusedLabelColor     = TextMuted,
        cursorColor             = BlueVibrant,
        focusedTextColor        = White,
        unfocusedTextColor      = White,
        focusedContainerColor   = Surface2,
        unfocusedContainerColor = Surface2
    )

    // ─────────────────────────────────────────────────────────────────────────

    @Composable
    fun PantallaPrincipal() {
        var selectedFileUri by remember {
            mutableStateOf(prefs.getString("last_ovpn_uri", null)?.let { Uri.parse(it) })
        }
        var selectedFileName by remember {
            mutableStateOf(prefs.getString("last_ovpn_name", null) ?: "Ningún archivo seleccionado")
        }
        var vpnUser by remember { mutableStateOf(prefs.getString("last_vpn_user", "") ?: "") }
        var vpnPass by remember { mutableStateOf(prefs.getString("last_vpn_pass", "") ?: "") }
        var selectedApp by remember {
            mutableStateOf(
                prefs.getString("last_app_package", null)?.let { pkg ->
                    AppInfo(prefs.getString("last_app_name", pkg) ?: pkg, pkg)
                }
            )
        }
        var seconds by remember { mutableStateOf(prefs.getString("last_seconds", "30") ?: "30") }
        var showAppPicker by remember { mutableStateOf(false) }

        val focusOvpn      = remember { FocusRequester() }
        val focusUser      = remember { FocusRequester() }
        val focusPass      = remember { FocusRequester() }
        val focusAppPicker = remember { FocusRequester() }
        val focusSeconds   = remember { FocusRequester() }
        val focusBoton     = remember { FocusRequester() }

        val filePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            selectedFileUri = uri
            selectedFileName = if (uri != null) obtenerNombreArchivo(uri) else "Ningún archivo seleccionado"
        }

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
                    android.view.KeyEvent.KEYCODE_DPAD_UP   -> { onUp?.invoke(); onUp != null }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> { onDown?.invoke(); onDown != null }
                    else -> false
                }
            }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 20.dp)
        ) {
            // ── TARJETA 1: PERFIL VPN ──────────────────────────────────────
            item {
                XCard {
                    SectionTitle("Perfil VPN")

                    var ovpnFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusOvpn)
                            .onFocusChanged { ovpnFocused = it.isFocused }
                            .tvNav(onDown = { try { focusUser.requestFocus() } catch (_: Exception) {} }),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (ovpnFocused) BlueVibrant else BlueDeep
                        )
                    ) {
                        Text(
                            "Seleccionar archivo .ovpn",
                            color = White,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = selectedFileName,
                        fontSize = 12.sp,
                        color = if (selectedFileUri != null) BlueLuminous else TextMuted,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = vpnUser,
                        onValueChange = { vpnUser = it },
                        label = { Text("Usuario VPN (Opcional)") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = TextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = {
                            try { focusPass.requestFocus() } catch (_: Exception) {}
                        }),
                        colors = xTextFieldColors(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .focusRequester(focusUser)
                            .tvNav(
                                onUp   = { try { focusOvpn.requestFocus() } catch (_: Exception) {} },
                                onDown = { try { focusPass.requestFocus() } catch (_: Exception) {} }
                            )
                    )

                    OutlinedTextField(
                        value = vpnPass,
                        onValueChange = { vpnPass = it },
                        label = { Text("Contraseña VPN (Opcional)") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TextMuted) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(onNext = {
                            try { focusAppPicker.requestFocus() } catch (_: Exception) {}
                        }),
                        colors = xTextFieldColors(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusPass)
                            .tvNav(
                                onUp   = { try { focusUser.requestFocus() } catch (_: Exception) {} },
                                onDown = { try { focusAppPicker.requestFocus() } catch (_: Exception) {} }
                            )
                    )
                }
            }

            // ── TARJETA 2: APP Y TIEMPO ────────────────────────────────────
            item {
                XCard {
                    SectionTitle("App y tiempo")

                    var appFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (appFocused)
                                    Brush.linearGradient(
                                        listOf(BlueDeep, BlueVibrant.copy(alpha = 0.4f))
                                    )
                                else
                                    Brush.linearGradient(listOf(Surface2, Surface2))
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
                                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                        try { focusPass.requestFocus() } catch (_: Exception) {}; true
                                    }
                                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        try { focusSeconds.requestFocus() } catch (_: Exception) {}; true
                                    }
                                    else -> false
                                }
                            }
                            .clickable { showAppPicker = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedApp?.name ?: "Elegir aplicación",
                                    color = if (selectedApp != null) White else TextMuted,
                                    fontWeight = if (selectedApp != null) FontWeight.Medium else FontWeight.Normal
                                )
                                if (selectedApp != null) {
                                    Text(
                                        text = selectedApp!!.packageName,
                                        fontSize = 12.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = if (appFocused) BlueVibrant else TextMuted
                            )
                        }
                    }

                    OutlinedTextField(
                        value = seconds,
                        onValueChange = { seconds = it },
                        label = { Text("Tiempo activo") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            try { focusBoton.requestFocus() } catch (_: Exception) {}
                        }),
                        suffix = { Text("seg", color = TextMuted) },
                        singleLine = true,
                        colors = xTextFieldColors(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusSeconds)
                            .tvNav(
                                onUp   = { try { focusAppPicker.requestFocus() } catch (_: Exception) {} },
                                onDown = { try { focusBoton.requestFocus() } catch (_: Exception) {} }
                            )
                    )
                }
            }

            // ── BOTÓN CONECTAR ─────────────────────────────────────────────
            item {
                var botonFocused by remember { mutableStateOf(false) }
                val canConnect = selectedFileUri != null && selectedApp != null && !isConnecting.value

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (canConnect)
                                Brush.linearGradient(
                                    if (botonFocused)
                                        listOf(BlueVibrant, BlueLuminous)
                                    else
                                        listOf(BlueDeep, BlueVibrant)
                                )
                            else
                                Brush.linearGradient(listOf(Surface2, Surface2))
                        )
                        .focusRequester(focusBoton)
                        .onFocusChanged { botonFocused = it.isFocused }
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (event.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                            when (event.nativeKeyEvent.keyCode) {
                                android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                    try { focusSeconds.requestFocus() } catch (_: Exception) {}; true
                                }
                                else -> false
                            }
                        }
                        .clickable(enabled = canConnect) {
                            val secs = seconds.toLongOrNull()
                            when {
                                selectedFileUri == null    -> toast("Seleccioná un archivo .ovpn primero")
                                selectedApp == null        -> toast("Elegí una aplicación")
                                secs == null || secs <= 0 -> toast("Ingresá un tiempo válido")
                                else -> {
                                    isConnecting.value = true
                                    prefs.edit()
                                        .putString("last_ovpn_uri",     selectedFileUri.toString())
                                        .putString("last_ovpn_name",    selectedFileName)
                                        .putString("last_vpn_user",     vpnUser)
                                        .putString("last_vpn_pass",     vpnPass)
                                        .putString("last_app_package",  selectedApp!!.packageName)
                                        .putString("last_app_name",     selectedApp!!.name)
                                        .putString("last_seconds",      seconds)
                                        .apply()
                                    prepararConexion(selectedFileUri!!, selectedApp!!.packageName, secs, vpnUser, vpnPass)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isConnecting.value) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Conectando...", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = White)
                        }
                    } else {
                        Text(
                            text = if (selectedApp != null) "Conectar y abrir ${selectedApp!!.name}" else "Conectar",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (canConnect) White else TextMuted
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // ── Diálogo selector de apps ─────────────────────────────────────────────

    @Composable
    fun AppPickerDialog(onDismiss: () -> Unit, onAppSelected: (AppInfo) -> Unit) {
        var searchQuery by remember { mutableStateOf("") }
        val searchFocus    = remember { FocusRequester() }
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
            else allApps.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }

        LaunchedEffect(Unit) { delay(150); searchFocus.requestFocus() }

        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Surface1,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(BlueVibrant)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Elegir aplicación",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = White
                        )
                    }
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Buscar app...", color = TextMuted) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            try { firstItemFocus.requestFocus() } catch (_: Exception) {}
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor      = BlueVibrant,
                            unfocusedBorderColor    = Outline,
                            focusedLabelColor       = BlueVibrant,
                            unfocusedLabelColor     = TextMuted,
                            cursorColor             = BlueVibrant,
                            focusedTextColor        = White,
                            unfocusedTextColor      = White,
                            focusedContainerColor   = Surface2,
                            unfocusedContainerColor = Surface2
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .focusRequester(searchFocus)
                            .onPreviewKeyEvent { event ->
                                if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                    event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                                    try { firstItemFocus.requestFocus() } catch (_: Exception) {}
                                    true
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
                                    .then(
                                        if (index == 0) Modifier.focusRequester(firstItemFocus)
                                        else Modifier
                                    )
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .focusRequester(itemFocus)
                                    .focusable()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isFocused)
                                            Brush.linearGradient(
                                                listOf(BlueDeep, BlueVibrant.copy(alpha = 0.35f))
                                            )
                                        else
                                            Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                    )
                                    .onPreviewKeyEvent { event ->
                                        if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                            when (event.nativeKeyEvent.keyCode) {
                                                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                                                android.view.KeyEvent.KEYCODE_ENTER,
                                                android.view.KeyEvent.KEYCODE_BUTTON_A -> { onAppSelected(app); true }
                                                android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                                    if (index == 0) {
                                                        try { searchFocus.requestFocus() } catch (_: Exception) {}
                                                        true
                                                    } else false
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
                                    Text(
                                        text = app.name,
                                        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isFocused) BlueLuminous else White
                                    )
                                    Text(
                                        text = app.packageName,
                                        fontSize = 12.sp,
                                        color = TextMuted
                                    )
                                }
                            }

                            if (index < filteredApps.size - 1) {
                                HorizontalDivider(
                                    color = Outline,
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Lógica VPN ──────────────────────────────────────────────────────────

    private fun prepararConexion(uri: Uri, pkg: String, secs: Long, user: String, pass: String) {
        pendingUri = uri
        pendingPackageName = pkg
        pendingSeconds = secs
        pendingUser = user
        pendingPass = pass

        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            startActivityForResult(vpnIntent, VPN_REQUEST_CODE)
        } else {
            iniciarProceso(uri, pkg, secs, user, pass)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            pendingUri?.let { uri ->
                iniciarProceso(uri, pendingPackageName, pendingSeconds, pendingUser, pendingPass)
            }
        } else {
            isConnecting.value = false
        }
    }

    private fun iniciarProceso(uri: Uri, packageName: String, seconds: Long, user: String, pass: String) {
        vpnYaConectada = false
        appPendiente = packageName
        segundosPendientes = seconds

        val conexionExitosa = conectarVPN(uri, user, pass)
        if (!conexionExitosa) { isConnecting.value = false; return }

        try { de.blinkt.openvpn.core.VpnStatus.removeStateListener(vpnListener) } catch (_: Exception) {}
        de.blinkt.openvpn.core.VpnStatus.addStateListener(vpnListener)
        toast("Iniciando VPN...")
    }

    private fun conectarVPN(uri: Uri, user: String, pass: String): Boolean {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            var ovpnConfig = inputStream?.bufferedReader()?.use { it.readText() } ?: ""

            ovpnConfig = ovpnConfig.replace(Regex("^dev\\s+tun\\d+", RegexOption.MULTILINE), "dev tun")
            ovpnConfig = ovpnConfig.replace(Regex("^auth-user-pass\\s+.*$", RegexOption.MULTILINE), "auth-user-pass")

            val configParser = de.blinkt.openvpn.core.ConfigParser()
            configParser.parseConfig(java.io.StringReader(ovpnConfig))
            val profile: VpnProfile = configParser.convertProfile()

            if (user.isNotBlank() || pass.isNotBlank()) {
                profile.mUsername = user
                profile.mPassword = pass
            }

            val pm = de.blinkt.openvpn.core.ProfileManager.getInstance(this)
            pm.addProfile(profile)
            pm.saveProfileList(this)
            de.blinkt.openvpn.core.ProfileManager.saveProfile(this, profile)

            de.blinkt.openvpn.core.GlobalPreferences.setInstance(false, false, false)
            de.blinkt.openvpn.core.VPNLaunchHelper.startOpenVpn(profile, this, "", false)

            true
        } catch (e: Exception) {
            android.util.Log.e("SALTARIN_VPN", "Error en VPN: ${e.message}", e)
            toast("Error: ${e.message}")
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
        startForegroundService(intent)
    }

    private fun obtenerNombreArchivo(uri: Uri): String {
        var nombre = "Archivo seleccionado"
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) nombre = it.getString(index)
            }
        }
        return nombre
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        try { de.blinkt.openvpn.core.VpnStatus.removeStateListener(vpnListener) } catch (_: Exception) {}
        super.onDestroy()
    }
}