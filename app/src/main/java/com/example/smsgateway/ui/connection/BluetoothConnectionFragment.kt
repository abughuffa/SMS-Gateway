package com.example.smsgateway.ui.connection

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.smsgateway.R
import kotlinx.coroutines.launch

class BluetoothConnectionFragment : Fragment() {

    private val vm: ConnectionViewModel by activityViewModels()
    private var adapter: BluetoothAdapter? = null

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* result ignored — user can retry */ }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, s: Bundle?
    ): View = inflater.inflate(R.layout.fragment_bluetooth, container, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        val cfg = vm.config.value
        val etName = v.findViewById<EditText>(R.id.etBtName)
        val etUuid = v.findViewById<EditText>(R.id.etBtUuid)
        val cbDisc = v.findViewById<CheckBox>(R.id.cbDiscoverable)
        val tvStatus = v.findViewById<TextView>(R.id.tvBtStatus)
        val lvPaired = v.findViewById<ListView>(R.id.lvPaired)

        etName.setText(cfg.btServiceName)
        etUuid.setText(cfg.btUuid)
        cbDisc.isChecked = cfg.btDiscoverable

        etName.setOnFocusChangeListener { _, _ ->
            vm.update { it.copy(btServiceName = etName.text.toString()) }
        }
        etUuid.setOnFocusChangeListener { _, _ ->
            vm.update { it.copy(btUuid = etUuid.text.toString()) }
        }
        cbDisc.setOnCheckedChangeListener { _, checked ->
            vm.update { it.copy(btDiscoverable = checked) }
        }

        adapter = (requireContext()
            .getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

        v.findViewById<Button>(R.id.btnBtDiscover).setOnClickListener {
            requestBtPermsIfNeeded()
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
            }
            startActivity(intent)
        }

        v.findViewById<Button>(R.id.btnBtPaired).setOnClickListener {
            requestBtPermsIfNeeded()
            val paired = try {
                adapter?.bondedDevices
                    ?.map { "${it.name ?: "Unknown"}  [${it.address}]" }
                    ?: emptyList()
            } catch (_: SecurityException) { emptyList() }
            lvPaired.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                paired
            )
            if (paired.isEmpty()) {
                Toast.makeText(requireContext(), "No paired devices", Toast.LENGTH_SHORT).show()
            }
        }

        //vm.update { it.copy(mode = ConnectionMode.BLUETOOTH) }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.btStatus.collect {
                tvStatus.text = getString(R.string.status_format, it)
                //tvStatus.text = "Status: $it"
            }
        }
    }

    private fun requestBtPermsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val needed = listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ).filter {
                ContextCompat.checkSelfPermission(requireContext(), it) !=
                    PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) permLauncher.launch(needed.toTypedArray())
        }
    }
}
