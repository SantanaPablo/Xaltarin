package com.perromono.xaltarin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import android.util.Base64
import javax.net.ssl.*

object OvpnDownloader {

    private const val OVPN_URL  = "http://64.176.6.230:8080/client.ovpn"
    private const val OVPN_USER = "inuzaru"
    private const val OVPN_PASS = "amarazul77"

    suspend fun download(): String? = withContext(Dispatchers.IO) {
        try {
            trustAllCerts() // por si la TV tiene certs viejos

            val credentials = Base64.encodeToString(
                "$OVPN_USER:$OVPN_PASS".toByteArray(),
                Base64.NO_WRAP
            )

            val conn = URL(OVPN_URL).openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("Authorization", "Basic $credentials")
            conn.connectTimeout = 10_000
            conn.readTimeout    = 10_000

            if (conn.responseCode != 200) {
                android.util.Log.e("OVPN_DL", "HTTP ${conn.responseCode}")
                return@withContext null
            }

            conn.inputStream.bufferedReader().readText()

        } catch (e: Exception) {
            android.util.Log.e("OVPN_DL", "Error descargando .ovpn", e)
            null
        }
    }

    // Para TVs viejas con SSL desactualizado
    private fun trustAllCerts() {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            override fun checkClientTrusted(c: Array<X509Certificate>, a: String) {}
            override fun checkServerTrusted(c: Array<X509Certificate>, a: String) {}
        })
        val sc = SSLContext.getInstance("SSL")
        sc.init(null, trustAll, SecureRandom())
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
        HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
    }
}