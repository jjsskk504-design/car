package com.prism.vehicle.app

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prism.vehicle.app.ui.PrismApp

class MainActivity : ComponentActivity() {
    private val vm: PrismViewModel by viewModels()

    // FUSED_LOCATION 은 dangerous 권한. 동의하면 SDK 세션을 다시 연다.
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.restartSdk()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val s by vm.ui.collectAsStateWithLifecycle()
            LaunchedEffect(s.mode) {
                if (s.mode == AppMode.PLEOS_CONTEXT &&
                    checkSelfPermission(FUSED_LOCATION) != PackageManager.PERMISSION_GRANTED
                ) locationPermission.launch(FUSED_LOCATION)
            }
            PrismApp(vm)
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }

    override fun onStop() {
        vm.onBackground()
        super.onStop()
    }

    private companion object { const val FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION" }
}
