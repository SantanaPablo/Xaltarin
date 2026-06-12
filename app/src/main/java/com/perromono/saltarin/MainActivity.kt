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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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

    // Variables de estado pendientes
    private var appPendiente = ""
    private var segundosPendientes = 0L
    private var vpnYaConectada = false

    // Estado reactivo para el spinner de carga
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

                    android.util.Log.d("SALTARIN_VPN", "¡CONEXIÓN ESTABLECIDA!")

                    runOnUiThread {
                        isConnecting.value = false // Apaga el spinner
                        toast("VPN CONECTADA")
                        abrirAplicacion(appPendiente)
                        iniciarTemporizador(segundosPendientes)
                    }
                }

                de.blinkt.openvpn.core.ConnectionStatus.LEVEL_AUTH_FAILED -> {
                    runOnUiThread {
                        isConnecting.value = false // Apaga el spinner
                        toast("Error de autenticación VPN. Revisá usuario/clave.")
                    }
                }

                de.blinkt.openvpn.core.ConnectionStatus.LEVEL_NOTCONNECTED -> {
                    if (state != "NOPROCESS") {
                        runOnUiThread {
                            isConnecting.value = false // Apaga el spinner
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

        val lightBlueScheme = lightColorScheme(
            primary = Color(0xFF1976D2),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFBBDEFB),
            onPrimaryContainer = Color(0xFF001E3C),
            background = Color(0xFFF5F9FF),
            surface = Color.White,
            onSurfaceVariant = Color(0xFF5E6A75)
        )

        val darkBlueScheme = darkColorScheme(
            primary = Color(0xFF64B5F6),
            onPrimary = Color(0xFF00325A),
            primaryContainer = Color(0xFF00497D),
            onPrimaryContainer = Color(0xFFD1E4FF),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            onSurfaceVariant = Color(0xFFAFC0C9)
        )

        setContent {
            val useDarkTheme = isSystemInDarkTheme()
            val colors = if (useDarkTheme) darkBlueScheme else lightBlueScheme

            MaterialTheme(colorScheme = colors) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Xaltarin", fontWeight = FontWeight.Bold) },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
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

    @Composable
    fun PantallaPrincipal() {
        val context = LocalContext.current

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

        // FocusRequesters para navegación TV
        val focusOvpn = remember { FocusRequester() }
        val focusUser = remember { FocusRequester() }
        val focusPass = remember { FocusRequester() }
        val focusAppPicker = remember { FocusRequester() }
        val focusSeconds = remember { FocusRequester() }
        val focusBoton = remember { FocusRequester() }

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

        // Helper para que D-PAD arriba/abajo navegue entre campos
        fun Modifier.tvNav(onUp: (() -> Unit)? = null, onDown: (() -> Unit)? = null): Modifier =
            this.onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                        onUp?.invoke(); onUp != null
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        onDown?.invoke(); onDown != null
                    }
                    else -> false
                }
            }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // TARJETA 1: PERFIL VPN
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Perfil VPN",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Botón seleccionar OVPN
                        var ovpnFocused by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = { filePickerLauncher.launch("*/*") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusOvpn)
                                .onFocusChanged { ovpnFocused = it.isFocused }
                                .tvNav(
                                    onDown = { try { focusUser.requestFocus() } catch (_: Exception) {} }
                                ),
                            border = BorderStroke(
                                if (ovpnFocused) 3.dp else 1.dp,
                                if (ovpnFocused) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Text("Seleccionar archivo .ovpn")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedFileName,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.vpnbook.com/")))
                            }) {
                                Text("Conseguir .ovpn", fontSize = 12.sp, textDecoration = TextDecoration.Underline)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Usuario
                        OutlinedTextField(
                            value = vpnUser,
                            onValueChange = { vpnUser = it },
                            label = { Text("Usuario VPN (Opcional)") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = {
                                try { focusPass.requestFocus() } catch (_: Exception) {}
                            }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .focusRequester(focusUser)
                                .tvNav(
                                    onUp = { try { focusOvpn.requestFocus() } catch (_: Exception) {} },
                                    onDown = { try { focusPass.requestFocus() } catch (_: Exception) {} }
                                )
                        )

                        // Contraseña
                        OutlinedTextField(
                            value = vpnPass,
                            onValueChange = { vpnPass = it },
                            label = { Text("Contraseña VPN (Opcional)") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(onNext = {
                                try { focusAppPicker.requestFocus() } catch (_: Exception) {}
                            }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusPass)
                                .tvNav(
                                    onUp = { try { focusUser.requestFocus() } catch (_: Exception) {} },
                                    onDown = { try { focusAppPicker.requestFocus() } catch (_: Exception) {} }
                                )
                        )
                    }
                }
            }

            // TARJETA 2: APP Y TIEMPO
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "App y tiempo",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Selector de app
                        var appFocused by remember { mutableStateOf(false) }
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                                .focusRequester(focusAppPicker)
                                .onFocusChanged { appFocused = it.isFocused }
                                .focusable()
                                .onPreviewKeyEvent { event ->
                                    if (event.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                                    when (event.nativeKeyEvent.keyCode) {
                                        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                                        android.view.KeyEvent.KEYCODE_ENTER,
                                        android.view.KeyEvent.KEYCODE_BUTTON_A -> {
                                            showAppPicker = true; true
                                        }
                                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                            try { focusPass.requestFocus() } catch (_: Exception) {}; true
                                        }
                                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                            try { focusSeconds.requestFocus() } catch (_: Exception) {}; true
                                        }
                                        else -> false
                                    }
                                }
                                .clickable { showAppPicker = true },
                            border = BorderStroke(
                                if (appFocused) 3.dp else 1.dp,
                                if (appFocused) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            ),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (appFocused)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selectedApp?.name ?: "Elegir aplicación",
                                        fontWeight = if (selectedApp != null) FontWeight.Medium else FontWeight.Normal
                                    )
                                    if (selectedApp != null) {
                                        Text(
                                            text = selectedApp!!.packageName,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        // Segundos
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
                            suffix = { Text("segundos") },
                            singleLine = true,
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
            }

            // BOTÓN CONECTAR
            item {
                var botonFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = {
                        val secs = seconds.toLongOrNull()
                        when {
                            selectedFileUri == null -> toast("Seleccioná un archivo .ovpn primero")
                            selectedApp == null -> toast("Elegí una aplicación")
                            secs == null || secs <= 0 -> toast("Ingresá un tiempo válido")
                            else -> {
                                isConnecting.value = true
                                prefs.edit()
                                    .putString("last_ovpn_uri", selectedFileUri.toString())
                                    .putString("last_ovpn_name", selectedFileName)
                                    .putString("last_vpn_user", vpnUser)
                                    .putString("last_vpn_pass", vpnPass)
                                    .putString("last_app_package", selectedApp!!.packageName)
                                    .putString("last_app_name", selectedApp!!.name)
                                    .putString("last_seconds", seconds)
                                    .apply()
                                prepararConexion(selectedFileUri!!, selectedApp!!.packageName, secs, vpnUser, vpnPass)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .focusRequester(focusBoton)
                        .onFocusChanged { botonFocused = it.isFocused }
                        .tvNav(
                            onUp = { try { focusSeconds.requestFocus() } catch (_: Exception) {} }
                        ),
                    enabled = selectedFileUri != null && selectedApp != null && !isConnecting.value,
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = if (botonFocused) 8.dp else 2.dp
                    )
                ) {
                    if (isConnecting.value) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Conectando...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text(
                            "Conectar y abrir ${selectedApp?.name ?: ""}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
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
            else allApps.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }

        LaunchedEffect(Unit) {
            delay(150)
            searchFocus.requestFocus()
        }

        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Elegir aplicación",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Buscador: al presionar ABAJO mueve foco al primer item
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Buscar app...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            // Al presionar Search/Enter en teclado, baja al primer item
                            try { firstItemFocus.requestFocus() } catch (_: Exception) {}
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .focusRequester(searchFocus)
                            .onPreviewKeyEvent { event ->
                                // Interceptamos D-PAD abajo para salir del TextField
                                if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                    event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                                    try { firstItemFocus.requestFocus() } catch (_: Exception) {}
                                    true // consumimos el evento
                                } else false
                            },
                        singleLine = true
                    )

                    LazyColumn {
                        items(filteredApps.size) { index ->
                            val app = filteredApps[index]
                            var isFocused by remember { mutableStateOf(false) }
                            val itemFocus = remember { FocusRequester() }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        // Solo el primer item recibe el FocusRequester nombrado
                                        if (index == 0) Modifier.focusRequester(firstItemFocus)
                                        else Modifier
                                    )
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .focusRequester(itemFocus)
                                    .focusable()
                                    .background(
                                        if (isFocused) MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent
                                    )
                                    .onPreviewKeyEvent { event ->
                                        if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                            when (event.nativeKeyEvent.keyCode) {
                                                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                                                android.view.KeyEvent.KEYCODE_ENTER,
                                                android.view.KeyEvent.KEYCODE_BUTTON_A -> {
                                                    onAppSelected(app)
                                                    true
                                                }
                                                android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                                    // Si es el primero, volvemos al buscador
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
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.name,
                                        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isFocused)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = app.packageName,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

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

        if (!conexionExitosa) {
            isConnecting.value = false
            return
        }

        try {
            de.blinkt.openvpn.core.VpnStatus.removeStateListener(vpnListener)
        } catch (_: Exception) {}

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