package com.timecontroller.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Activity transparan, disiapkan sebagai host apabila ke depannya diperlukan
 * dialog sistem tambahan terkait izin overlay. Saat ini langsung ditutup.
 */
class OverlayPermissionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
