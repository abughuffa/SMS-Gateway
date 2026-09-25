package com.example.smsgateway.ui.connection

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.smsgateway.R
import kotlinx.coroutines.launch

class UsbConnectionFragment : Fragment() {

    private val vm: ConnectionViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, s: Bundle?
    ): View = inflater.inflate(R.layout.fragment_usb, container, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        val rg = v.findViewById<RadioGroup>(R.id.rgUsbMode)
        val etPort = v.findViewById<EditText>(R.id.etUsbPort)
        val tvStatus = v.findViewById<TextView>(R.id.tvUsbStatus)

        val cfg = vm.config.value
        etPort.setText(cfg.usbPort.toString())
        when (cfg.usbMode) {
            UsbMode.ADB_FORWARD -> rg.check(R.id.rbAdb)
            UsbMode.ACCESSORY -> rg.check(R.id.rbAccessory)
        }

        rg.setOnCheckedChangeListener { _, id ->
            vm.update {
                it.copy(
                    mode = ConnectionMode.USB,
                    usbMode = if (id == R.id.rbAdb) UsbMode.ADB_FORWARD
                    else UsbMode.ACCESSORY
                )
            }
        }
        etPort.setOnFocusChangeListener { _, _ ->
            etPort.text.toString().toIntOrNull()?.let { p ->
                vm.update { it.copy(usbPort = p) }
            }
        }

        v.findViewById<Button>(R.id.btnUsbTest).setOnClickListener {
            tvStatus.text = getString(R.string.status_format, vm.usbStatus.value)
            //tvStatus.text = "Status: ${vm.usbStatus.value}"
            Toast.makeText(requireContext(), "USB test triggered", Toast.LENGTH_SHORT).show()
        }

        //vm.update { it.copy(mode = ConnectionMode.USB) }
        viewLifecycleOwner.lifecycleScope.launch {

            vm.usbStatus.collect {
                tvStatus.text = getString(R.string.status_format, it)
              //  tvStatus.text = "Status: $it"
            }
        }
    }
}
