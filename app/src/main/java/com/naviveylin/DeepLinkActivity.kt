package com.naviveylin

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

class DeepLinkActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val original = intent
        Log.d(TAG, "Deep link received: action=${original?.action} data=${original?.data}")

        val forward = Intent(this, MainActivity::class.java)
        forward.action = original?.action
        forward.data = original?.data
        original?.getStringExtra(Intent.EXTRA_TEXT)?.let { forward.putExtra(Intent.EXTRA_TEXT, it) }
        original?.getStringExtra("android.intent.extra.QUERY")?.let { forward.putExtra("android.intent.extra.QUERY", it) }
        forward.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            startActivity(forward)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to forward deep link to MainActivity", e)
        }
        finish()
    }

    companion object {
        private const val TAG = "DeepLinkActivity"
    }
}
