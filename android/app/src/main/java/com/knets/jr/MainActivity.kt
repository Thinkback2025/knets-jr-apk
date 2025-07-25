package com.knets.jr

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.knets.jr.admin.KnetsDeviceAdminReceiver
import com.knets.jr.api.KnetsApiService
import com.knets.jr.api.RetrofitClient
import com.knets.jr.databinding.ActivityMainBinding
import com.knets.jr.model.DeviceRegistration
import com.knets.jr.service.ScheduleMonitorService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "KnetsJrMainActivity"
        private const val REQUEST_CODE_ENABLE_ADMIN = 1
        private const val REQUEST_CODE_USAGE_STATS = 2
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private lateinit var apiService: KnetsApiService
    
    private var deviceImei: String? = null
    private var isRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, KnetsDeviceAdminReceiver::class.java)
        apiService = RetrofitClient.instance.create(KnetsApiService::class.java)

        setupUI()
        loadDeviceInfo()
        checkRegistrationStatus()
    }

    private fun setupUI() {
        binding.btnEnableAdmin.setOnClickListener { enableDeviceAdmin() }
        binding.btnRegisterDevice.setOnClickListener { registerDevice() }
        binding.btnTestLock.setOnClickListener { testDeviceLock() }
        binding.btnSettings.setOnClickListener { openSettings() }
        
        // Show current admin status
        updateAdminStatus()
    }

    private fun loadDeviceInfo() {
        try {
            // Get device IMEI (requires permission)
            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            deviceImei = telephonyManager.deviceId ?: "UNKNOWN_${android.os.Build.SERIAL}"
            
            binding.tvDeviceInfo.text = "Device IMEI: $deviceImei\n" +
                    "Model: ${android.os.Build.MODEL}\n" +
                    "Brand: ${android.os.Build.BRAND}"
                    
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for device ID", e)
            deviceImei = "PERM_DENIED_${android.os.Build.SERIAL}"
            binding.tvDeviceInfo.text = "Device ID: $deviceImei (Limited permissions)\n" +
                    "Model: ${android.os.Build.MODEL}\n" +
                    "Brand: ${android.os.Build.BRAND}"
        }
    }

    private fun checkRegistrationStatus() {
        val prefs = getSharedPreferences("knets_jr_prefs", Context.MODE_PRIVATE)
        isRegistered = prefs.getBoolean("is_registered", false)
        
        if (isRegistered) {
            binding.tvRegistrationStatus.text = "✓ Device is registered with Knets"
            binding.btnRegisterDevice.text = "Re-register Device"
        } else {
            binding.tvRegistrationStatus.text = "✗ Device not registered"
            binding.btnRegisterDevice.text = "Register Device"
        }
    }

    private fun updateAdminStatus() {
        val isAdminActive = devicePolicyManager.isAdminActive(adminComponent)
        
        if (isAdminActive) {
            binding.tvAdminStatus.text = "✓ Device Admin Enabled"
            binding.btnEnableAdmin.text = "Admin Active"
            binding.btnEnableAdmin.isEnabled = false
            binding.btnTestLock.isEnabled = true
            
            // Start monitoring service
            startService(Intent(this, ScheduleMonitorService::class.java))
        } else {
            binding.tvAdminStatus.text = "✗ Device Admin Required"
            binding.btnEnableAdmin.text = "Enable Device Admin"
            binding.btnEnableAdmin.isEnabled = true
            binding.btnTestLock.isEnabled = false
        }
    }

    private fun enableDeviceAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Knets Jr needs device admin permissions to enforce parental controls and lock the device when scheduled."
            )
        }
        startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN)
    }

    private fun registerDevice() {
        if (deviceImei == null) {
            Toast.makeText(this, "Unable to get device identifier", Toast.LENGTH_SHORT).show()
            return
        }

        // Show registration dialog
        val builder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_device_registration, null)
        builder.setView(dialogView)
        
        val dialog = builder.create()
        
        dialogView.findViewById<View>(R.id.btn_register_confirm).setOnClickListener {
            val childName = dialogView.findViewById<android.widget.EditText>(R.id.et_child_name).text.toString()
            val parentPhone = dialogView.findViewById<android.widget.EditText>(R.id.et_parent_phone).text.toString()
            
            if (childName.isNotBlank() && parentPhone.isNotBlank()) {
                performDeviceRegistration(childName, parentPhone)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }
        
        dialogView.findViewById<View>(R.id.btn_register_cancel).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun performDeviceRegistration(childName: String, parentPhone: String) {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                
                val registration = DeviceRegistration(
                    imei = deviceImei!!,
                    deviceName = "$childName's ${android.os.Build.MODEL}",
                    childName = childName,
                    parentPhone = parentPhone,
                    deviceModel = android.os.Build.MODEL,
                    deviceBrand = android.os.Build.BRAND,
                    androidVersion = android.os.Build.VERSION.RELEASE
                )
                
                val response = apiService.registerDevice(registration)
                
                // Save registration locally
                val prefs = getSharedPreferences("knets_jr_prefs", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putBoolean("is_registered", true)
                    putString("device_imei", deviceImei)
                    putString("child_name", childName)
                    putString("parent_phone", parentPhone)
                    apply()
                }
                
                isRegistered = true
                checkRegistrationStatus()
                
                Toast.makeText(this@MainActivity, "Device registered successfully!", Toast.LENGTH_LONG).show()
                
            } catch (e: Exception) {
                Log.e(TAG, "Registration failed", e)
                Toast.makeText(this@MainActivity, "Registration failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun testDeviceLock() {
        if (devicePolicyManager.isAdminActive(adminComponent)) {
            try {
                devicePolicyManager.lockNow()
                Toast.makeText(this, "Device locked!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to lock device", e)
                Toast.makeText(this, "Failed to lock device: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Device admin not enabled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openSettings() {
        startActivity(Intent(this, com.knets.jr.activity.SettingsActivity::class.java))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            REQUEST_CODE_ENABLE_ADMIN -> {
                if (resultCode == RESULT_OK) {
                    Toast.makeText(this, "Device Admin enabled!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Device Admin required for app to work", Toast.LENGTH_LONG).show()
                }
                updateAdminStatus()
            }
            REQUEST_CODE_USAGE_STATS -> {
                // Handle usage stats permission result
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateAdminStatus()
    }
}