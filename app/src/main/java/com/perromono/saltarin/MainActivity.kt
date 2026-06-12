package com.perromono.saltarin

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.perromono.saltarin.ui.theme.SaltarinTheme
import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.core.ConfigParser
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.ProfileManager
import java.io.InputStreamReader

data class AppInfo(val name: String, val packageName: String)

class MainActivity : ComponentActivity() {

    private val VPN_REQUEST_CODE = 100
    private var pendingPackageName = ""
    private var pendingSeconds = 0L
    private var pendingUri: Uri? = null

    private lateinit var prefs: SharedPreferences

    // 1) Variables de estado pendientes
    private var appPendiente = ""
    private var segundosPendientes = 0L
    private var vpnYaConectada = false

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
                    // 3) Abrir app y arrancar temporizador cuando conecta
                    if (vpnYaConectada) return
                    vpnYaConectada = true

                    android.util.Log.d("SALTARIN_VPN", "¡CONEXIÓN ESTABLECIDA!")

                    runOnUiThread {
                        toast("VPN CONECTADA")
                        abrirAplicacion(appPendiente)
                        iniciarTemporizador(segundosPendientes)
                    }
                }

                de.blinkt.openvpn.core.ConnectionStatus.LEVEL_AUTH_FAILED -> {
                    runOnUiThread { toast("Error de autenticación VPN") }
                }

                de.blinkt.openvpn.core.ConnectionStatus.LEVEL_NOTCONNECTED -> {
                    if (state != "NOPROCESS") {
                        runOnUiThread { toast("VPN desconectada") }
                    }
                }

                else -> {}
            }
        }

        override fun setConnectedVPN(uuid: String?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("saltarin_prefs", MODE_PRIVATE)

        setContent {
            SaltarinTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PantallaPrincipal()
                }
            }
        }
    }

    @Composable
    fun PantallaPrincipal() {
        var selectedFileUri by remember {
            mutableStateOf(prefs.getString("last_ovpn_uri", null)?.let { Uri.parse(it) })
        }
        var selectedFileName by remember {
            mutableStateOf(prefs.getString("last_ovpn_name", null) ?: "Ningún archivo cargado")
        }
        var selectedApp by remember {
            mutableStateOf(
                prefs.getString("last_app_package", null)?.let { pkg ->
                    val name = prefs.getString("last_app_name", pkg) ?: pkg
                    AppInfo(name, pkg)
                }
            )
        }
        var seconds by remember {
            mutableStateOf(prefs.getString("last_seconds", "30") ?: "30")
        }

        var showAppPicker by remember { mutableStateOf(false) }

        val filePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            selectedFileUri = uri
            selectedFileName = if (uri != null) obtenerNombreArchivo(uri) else "Ningún archivo cargado"
        }

        if (showAppPicker) {
            AppPickerDialog(
                onDismiss = { showAppPicker = false },
                onAppSelected = { app ->
                    selectedApp = app
                    showAppPicker = false
                }
            )
        }

        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "Saltarín",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "VPN temporal por app",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 28.dp)
            )

            StepLabel("1", "Archivo de configuración VPN")
            Button(
                onClick = { filePickerLauncher.launch("*/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Seleccionar archivo .ovpn")
            }
            Text(
                text = selectedFileName,
                fontSize = 13.sp,
                color = if (selectedFileUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
            )

            StepLabel("2", "Aplicación a abrir")
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAppPicker = true }
                    .padding(bottom = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = selectedApp?.name ?: "Tocar para elegir una app",
                            fontWeight = if (selectedApp != null) FontWeight.Medium else FontWeight.Normal,
                            fontSize = 15.sp
                        )
                        if (selectedApp != null) {
                            Text(
                                text = selectedApp!!.packageName,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Elegir app",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            StepLabel("3", "Duración de la conexión")
            OutlinedTextField(
                value = seconds,
                onValueChange = { seconds = it },
                label = { Text("Segundos de conexión") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                suffix = { Text("seg") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp)
            )

            Button(
                onClick = {
                    val secs = seconds.toLongOrNull()
                    when {
                        selectedFileUri == null -> toast("Seleccioná un archivo .ovpn primero")
                        selectedApp == null -> toast("Elegí una aplicación")
                        secs == null || secs <= 0 -> toast("Ingresá una cantidad de segundos válida")
                        else -> {
                            prefs.edit()
                                .putString("last_ovpn_uri", selectedFileUri.toString())
                                .putString("last_ovpn_name", selectedFileName)
                                .putString("last_app_package", selectedApp!!.packageName)
                                .putString("last_app_name", selectedApp!!.name)
                                .putString("last_seconds", seconds)
                                .apply()
                            prepararConexion(selectedFileUri!!, selectedApp!!.packageName, secs)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedFileUri != null && selectedApp != null
            ) {
                Text("Conectar y abrir ${selectedApp?.name ?: "app"}", fontSize = 16.sp)
            }
        }
    }

    @Composable
    fun StepLabel(number: String, label: String) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = number,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    @Composable
    fun AppPickerDialog(onDismiss: () -> Unit, onAppSelected: (AppInfo) -> Unit) {
        var searchQuery by remember { mutableStateOf("") }

        val allApps = remember {
            val pm: PackageManager = packageManager
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            pm.queryIntentActivities(intent, 0)
                .map { resolveInfo -> AppInfo(name = resolveInfo.loadLabel(pm).toString(), packageName = resolveInfo.activityInfo.packageName) }
                .distinctBy { it.packageName }
                .sortedBy { it.name.lowercase() }
        }

        val filteredApps = remember(searchQuery) {
            if (searchQuery.isBlank()) allApps
            else allApps.filter {
                it.name.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }

        Dialog(onDismissRequest = onDismiss) {
            Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Elegir aplicación", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Buscar app...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        singleLine = true
                    )
                    LazyColumn {
                        items(filteredApps) { app ->
                            ListItem(
                                headlineContent = { Text(app.name, fontWeight = FontWeight.Medium) },
                                supportingContent = { Text(app.packageName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                modifier = Modifier.clickable { onAppSelected(app) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    private fun prepararConexion(uri: Uri, pkg: String, secs: Long) {
        pendingUri = uri
        pendingPackageName = pkg
        pendingSeconds = secs

        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            startActivityForResult(vpnIntent, VPN_REQUEST_CODE)
        } else {
            iniciarProceso(uri, pkg, secs)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            pendingUri?.let { uri -> iniciarProceso(uri, pendingPackageName, pendingSeconds) }
        }
    }

    // 2) Modificar iniciarProceso
    private fun iniciarProceso(uri: Uri, packageName: String, seconds: Long) {
        vpnYaConectada = false
        appPendiente = packageName
        segundosPendientes = seconds

        val conexionExitosa = conectarVPN(uri)

        if (!conexionExitosa) {
            return
        }

        try {
            de.blinkt.openvpn.core.VpnStatus.removeStateListener(vpnListener)
        } catch (_: Exception) {
        }

        de.blinkt.openvpn.core.VpnStatus.addStateListener(vpnListener)

        toast("Iniciando VPN...")
    }

    private fun conectarVPN(uri: Uri): Boolean {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            var ovpnConfig = inputStream?.bufferedReader()?.use { it.readText() } ?: ""

            ovpnConfig = ovpnConfig.replace(Regex("^dev\\s+tun\\d+", RegexOption.MULTILINE), "dev tun")
            ovpnConfig = ovpnConfig.replace(Regex("^auth-user-pass\\s+.*$", RegexOption.MULTILINE), "auth-user-pass")

            val configParser = de.blinkt.openvpn.core.ConfigParser()
            configParser.parseConfig(java.io.StringReader(ovpnConfig))
            val profile: VpnProfile = configParser.convertProfile()

            profile.mUsername = "vpnbook"
            profile.mPassword = "8zw5j9h"

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

    // 4) Función abrirAplicacion
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

    // 5) Función iniciarTemporizador
    private fun iniciarTemporizador(segundos: Long) {
        val intent = Intent(this, VpnTimerService::class.java)
        intent.putExtra("TIMER_SECONDS", segundos)
        // startForegroundService requiere que el servicio llame a startForeground() en sus primeros 5 segundos
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