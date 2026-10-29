package com.ovehbe.junkboy.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.ovehbe.junkboy.ui.compose.JunkboyApp
import com.ovehbe.junkboy.ui.theme.JunkboyTheme
import com.ovehbe.junkboy.utils.PreferencesManager
import com.ovehbe.junkboy.work.WorkScheduler

class MainActivity : ComponentActivity() {
    
    private lateinit var preferencesManager: PreferencesManager
    
    // State for tracking permission status - updated live
    private var permissionRefreshTrigger by mutableIntStateOf(0)
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Always trigger UI refresh after permission request completes
        permissionRefreshTrigger++
        
        val allGranted = hasAllPermissions()
        preferencesManager.setPermissionsGranted(allGranted)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge before calling super
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        // Allow app to handle IME insets for keyboard offset feature
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        preferencesManager = PreferencesManager(this)
        WorkScheduler.schedule(this)
        
        setContent {
            JunkboyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    JunkboyApp(
                        onRequestPermissions = { checkAndRequestPermissions() },
                        permissionRefreshTrigger = permissionRefreshTrigger,
                        checkPermissions = { hasAllPermissions() }
                    )
                }
            }
        }
        
        // Initial permission check
        if (!hasAllPermissions()) {
            checkAndRequestPermissions()
        } else {
            preferencesManager.setPermissionsGranted(true)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Re-check permissions every time app resumes (user may have granted in settings)
        val allGranted = hasAllPermissions()
        preferencesManager.setPermissionsGranted(allGranted)
        permissionRefreshTrigger++
    }
    
    private fun checkAndRequestPermissions() {
        val requiredPermissions = buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.SEND_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            preferencesManager.setPermissionsGranted(true)
            permissionRefreshTrigger++
        }
    }
    
    private fun hasAllPermissions(): Boolean {
        val requiredPermissions = buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.SEND_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}
