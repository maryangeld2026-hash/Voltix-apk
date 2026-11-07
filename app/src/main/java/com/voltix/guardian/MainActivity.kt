package com.voltix.guardian

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.voltix.guardian.service.GuardianService
import com.voltix.guardian.service.Notifier
import com.voltix.guardian.ui.MainViewModel
import com.voltix.guardian.ui.VoltixApp
import com.voltix.guardian.ui.VoltixTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshPerms() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.ensureChannels(this)

        // Tras un reinicio o si el sistema cerró el servicio, se reanuda al abrir la app.
        if (vm.ui.value.guardian) {
            try {
                GuardianService.start(this)
            } catch (_: Exception) {
            }
        }

        // Solo refresca mientras la app está visible: cero consumo en segundo plano desde la UI.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.refreshPerms()
                while (true) {
                    vm.refresh()
                    delay(2000)
                }
            }
        }

        setContent {
            VoltixTheme {
                VoltixApp(vm) {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        }
    }
}
