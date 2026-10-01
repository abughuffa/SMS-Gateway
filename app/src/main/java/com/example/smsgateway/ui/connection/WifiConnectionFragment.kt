package com.example.smsgateway.ui.connection

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.smsgateway.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

class WifiConnectionFragment : Fragment() {

    private val vm: ConnectionViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, s: Bundle?
    ): View = inflater.inflate(R.layout.fragment_wifi, container, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        val cfg = vm.config.value
        val etPort = v.findViewById<EditText>(R.id.etWifiPort)
        val etToken = v.findViewById<EditText>(R.id.etWifiToken)
        val cbAll = v.findViewById<CheckBox>(R.id.cbBindAll)
        val tvStatus = v.findViewById<TextView>(R.id.tvWifiStatus)
        val tvIps = v.findViewById<TextView>(R.id.tvIpList)

        etPort.setText(String.format(Locale.US, "%d", cfg.wifiPort))
        etToken.setText(cfg.wifiToken ?: "")
        cbAll.isChecked = cfg.wifiBindAll

        // Update in-memory config as user edits (NO auto-save yet)
        etPort.doAfterTextChanged { text ->
            text?.toString()?.toIntOrNull()?.let { p ->
                if (p in 1..65535) {
                    vm.update { it.copy(wifiPort = p) }
                }
            }
        }

        etToken.doAfterTextChanged { text ->
            vm.update {
                it.copy(
                    wifiToken = text?.toString()?.takeIf { t -> t.isNotBlank() }
                )
            }
        }

        cbAll.setOnCheckedChangeListener { _, c ->
            vm.update { it.copy(wifiBindAll = c) }
        }

        fun refreshIps() {
            viewLifecycleOwner.lifecycleScope.launch {
                val ips = withContext(Dispatchers.IO) { localIpv4Addresses() }
                val body = if (ips.isEmpty()) getString(R.string.local_ips_none)
                else ips.joinToString("\n")
                tvIps.text = getString(R.string.local_ips_block, body)
            }
        }
        refreshIps()
        v.findViewById<Button>(R.id.btnWifiRefreshIp)
            .setOnClickListener { refreshIps() }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.wifiStatus.collect {
                tvStatus.text = getString(R.string.status_format, it)
            }
        }
    }

    private fun localIpv4Addresses(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
    } catch (_: Exception) { emptyList() }
}